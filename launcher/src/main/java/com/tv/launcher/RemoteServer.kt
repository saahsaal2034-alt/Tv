package com.tv.launcher

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.ByteArrayOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * A tiny web server on the TV that serves the phone remote page and receives
 * its button presses. Requests must carry the secret key from the QR code, so
 * other people on the Wi-Fi can't control the TV.
 */
object RemoteServer {
    private const val PORT = 8737

    @Volatile
    var running = false
        private set

    private var server: ServerSocket? = null

    fun start(context: Context, onCommand: (command: String, text: String?) -> Unit) {
        if (running) return
        val appContext = context.applicationContext
        AtvRemote.init(appContext)
        val key = key(appContext)
        val page = appContext.assets.open("remote.html").use { it.readBytes() }
        val socket = try {
            ServerSocket(PORT)
        } catch (e: Exception) {
            return
        }
        server = socket
        running = true
        thread(name = "remote-server", isDaemon = true) {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: Exception) {
                    break
                }
                thread(isDaemon = true) { handle(appContext, client, key, page, onCommand) }
            }
        }
    }

    fun stop() {
        running = false
        server?.close()
        server = null
    }

    /** The address the QR code points to, or null when the TV has no network. */
    fun url(context: Context): String? = localIp()?.let { "http://$it:$PORT/?k=${key(context)}" }

    private fun handle(context: Context, client: Socket, key: String, page: ByteArray, onCommand: (String, String?) -> Unit) {
        client.use {
            client.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return
            while (!reader.readLine().isNullOrEmpty()) Unit // Skip headers.

            val target = requestLine.split(' ').getOrNull(1) ?: return
            val path = target.substringBefore('?')
            val query = parseQuery(target.substringAfter('?', ""))
            val out = client.getOutputStream()

            when {
                query["k"] != key -> respond(out, "403 Forbidden", "text/plain", "Scan the QR code on the TV again.".toByteArray())
                path == "/" -> respond(out, "200 OK", "text/html; charset=utf-8", page)
                path == "/s" -> {
                    val full = HomeButtonService.instance != null
                    if (AtvRemote.paired && !AtvRemote.connected) AtvRemote.warmUp()
                    val error = AtvRemote.lastError?.let(::jsonString) ?: "null"
                    val nav = HomeButtonService.lastResult?.let(::jsonString) ?: "null"
                    val json = "{\"full\":$full,\"paired\":${AtvRemote.paired},\"live\":${AtvRemote.connected}," +
                        "\"err\":$error,\"nav\":$nav,\"android\":${android.os.Build.VERSION.SDK_INT}}"
                    respond(out, "200 OK", "application/json", json.toByteArray())
                }
                path == "/pair" -> {
                    // Pairing talks to the TV over the network, so it runs here, off the main thread.
                    val error = try {
                        if (query["step"] == "code") AtvRemote.finishPairing(query["t"].orEmpty()) else AtvRemote.startPairing()
                        null
                    } catch (e: Exception) {
                        e.message ?: "Pairing failed"
                    }
                    val json = if (error == null) "{\"ok\":true}" else "{\"ok\":false,\"error\":\"${error.replace("\"", "'")}\"}"
                    respond(out, "200 OK", "application/json", json.toByteArray())
                }
                path == "/apps" -> {
                    val json = Apps.all(context).joinToString(",", "[", "]") {
                        "{\"p\":${jsonString(it.component.packageName)},\"n\":${jsonString(it.label)}}"
                    }
                    respond(out, "200 OK", "application/json", json.toByteArray())
                }
                path == "/icon" -> {
                    val png = query["p"]?.let { iconPng(context, it) }
                    if (png != null) respond(out, "200 OK", "image/png", png) else respond(out, "404 Not Found", "text/plain", ByteArray(0))
                }
                // Lets the remote be added to the phone's home screen as an app.
                path == "/manifest.json" -> {
                    val json = """{"name":"Vortrex Remote","short_name":"Vortrex","start_url":"/?k=$key",""" +
                        """"display":"standalone","background_color":"#000000","theme_color":"#000000",""" +
                        """"icons":[{"src":"/app-icon.png?k=$key","sizes":"512x512","type":"image/png"}]}"""
                    respond(out, "200 OK", "application/manifest+json", json.toByteArray())
                }
                path == "/app-icon.png" -> respond(out, "200 OK", "image/png", context.assets.open("app-icon.png").use { it.readBytes() })
                path == "/k" -> {
                    query["c"]?.let { onCommand(it, query["t"]) }
                    respond(out, "204 No Content", "text/plain", ByteArray(0))
                }
                else -> respond(out, "404 Not Found", "text/plain", ByteArray(0))
            }
        }
    }

    private val icons = ConcurrentHashMap<String, ByteArray>()

    private fun iconPng(context: Context, pkg: String): ByteArray? = icons[pkg] ?: try {
        val icon = context.packageManager.getApplicationIcon(pkg)
        val bitmap = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 144, 144)
        icon.draw(Canvas(bitmap))
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            .also { icons[pkg] = it }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun jsonString(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""

    private fun respond(out: OutputStream, status: String, type: String, body: ByteArray) {
        val head = "HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').filter { '=' in it }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }

    private fun key(context: Context): String {
        val prefs = context.getSharedPreferences("remote", Context.MODE_PRIVATE)
        prefs.getString("key", null)?.let { return it }
        val bytes = ByteArray(6).also { SecureRandom().nextBytes(it) }
        val key = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("key", key).apply()
        return key
    }

    fun localIp(): String? =
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
}
