package com.tv.launcher

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import java.util.concurrent.Executors
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal
import kotlin.concurrent.thread

/**
 * Talks to the TV's own Android TV Remote Service, the same one the Google TV
 * phone app uses. Once paired, its key presses work in every app, including
 * Netflix and YouTube, which ignore accessibility-based navigation.
 */
object AtvRemote {
    private const val HOST = "127.0.0.1"
    private const val PAIRING_PORT = 6467
    private const val REMOTE_PORT = 6466
    private const val ALIAS = "vortrex_remote"
    private const val CLIENT_NAME = "Vortrex TV"

    private lateinit var appContext: Context
    private val sender = Executors.newSingleThreadExecutor()
    private val lock = Object()

    private var pairingSocket: SSLSocket? = null
    private var remoteSocket: SSLSocket? = null

    @Volatile
    private var active = false
    private var typed = ""
    private var lastFailure = 0L

    /** Why the last connection to the TV's remote service failed, for the phone to show. */
    @Volatile
    var lastError: String? = null
        private set

    val connected: Boolean
        get() = active

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val paired: Boolean
        get() = prefs().getBoolean("paired", false)

    // Pairing: the TV shows a 6-character code, which the phone sends back.

    /** Starts pairing; the TV shows a code when this returns. */
    fun startPairing() {
        pairingSocket?.close()
        val socket = open(PAIRING_PORT)
        pairingSocket = socket
        val out = socket.outputStream
        val input = socket.inputStream

        send(out) { message(10) { string(1, "atvremote"); string(2, CLIENT_NAME) } }
        expect(input, 11)
        send(out) {
            message(20) {
                message(1) { int(1, 3); int(2, 6) } // Hexadecimal code, 6 characters.
                int(3, 1) // We type the code in.
            }
        }
        expect(input, 20)
        send(out) {
            message(30) {
                message(1) { int(1, 3); int(2, 6) }
                int(2, 1)
            }
        }
        expect(input, 31)
    }

    /** Finishes pairing with the code shown on the TV. */
    fun finishPairing(code: String) {
        val socket = pairingSocket ?: error("Start pairing first")
        val clean = code.trim().uppercase()
        require(clean.length == 6 && clean.all { it in "0123456789ABCDEF" }) { "The code has 6 letters and numbers" }

        val client = keyStore().getCertificate(ALIAS).publicKey as RSAPublicKey
        val server = (socket.session.peerCertificates[0] as X509Certificate).publicKey as RSAPublicKey
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(client.modulus.unsigned())
            update(client.publicExponent.unsigned())
            update(server.modulus.unsigned())
            update(server.publicExponent.unsigned())
            update(hex(clean.substring(2)))
        }.digest()
        // The first byte of the code is a check digit for the rest.
        require(digest[0] == hex(clean.substring(0, 2))[0]) { "That code doesn't match. Check the TV and try again." }

        send(socket.outputStream) { message(40) { bytes(1, digest) } }
        expect(socket.inputStream, 41)
        socket.close()
        pairingSocket = null
        prefs().edit().putBoolean("paired", true).apply()
        sender.execute { connect() }
    }

    // Key presses.

    /** Presses a key; runs [fallback] instead if the TV's remote service can't be reached. */
    fun key(keyCode: Int, fallback: () -> Unit) = sender.execute { if (!sendKey(keyCode)) fallback() }

    /** Connects in the background so the first key press is quick. */
    fun warmUp() = sender.execute { ensureConnected() }

    /** Types the phone's text by sending only what changed since last time. */
    fun type(text: String, fallback: () -> Unit) = sender.execute {
        if (!ensureConnected()) return@execute fallback()
        val next = text.lowercase()
        val common = typed.commonPrefixWith(next).length
        repeat(typed.length - common) { sendKey(67) } // Delete.
        for (c in next.substring(common)) keyCodeFor(c)?.let(::sendKey)
        typed = next
    }

    fun startTyping() = sender.execute { typed = "" }

    private fun sendKey(keyCode: Int): Boolean {
        if (!ensureConnected()) return false
        return try {
            synchronized(lock) {
                val out = remoteSocket?.outputStream ?: return false
                writeFrame(out, proto { message(10) { int(1, keyCode.toLong()); int(2, 3) } }) // A short press.
            }
            true
        } catch (e: Exception) {
            fail("Lost connection: ${e.message}")
            false
        }
    }

    private fun ensureConnected(): Boolean {
        if (!paired) return false
        if (active) return true
        // Don't make every press wait while the service is unreachable.
        if (remoteSocket == null && System.currentTimeMillis() - lastFailure < 10_000) return false
        if (remoteSocket == null) connect()
        // The TV accepts keys once it has marked us active.
        val deadline = System.currentTimeMillis() + 3000
        while (!active && remoteSocket != null && System.currentTimeMillis() < deadline) Thread.sleep(50)
        if (!active) fail(lastError ?: "The TV didn't accept the remote")
        return active
    }

    private fun connect() {
        val socket = try {
            open(REMOTE_PORT)
        } catch (e: SSLHandshakeException) {
            // The TV no longer recognises us, so pair again.
            prefs().edit().putBoolean("paired", false).apply()
            fail("The TV forgot the pairing: ${e.message}")
            return
        } catch (e: Exception) {
            fail("Can't reach the TV's remote service: ${e.message}")
            return
        }
        remoteSocket = socket
        active = false
        lastError = null
        thread(name = "atv-remote", isDaemon = true) {
            try {
                val input = socket.inputStream
                while (true) {
                    val incoming = readFrame(input)
                    when {
                        incoming.has(1) -> reply(socket) {
                            message(1) {
                                int(1, 622)
                                message(2) {
                                    string(1, Build.MODEL)
                                    string(2, Build.MANUFACTURER)
                                    int(3, 1)
                                    string(4, "1")
                                    string(5, "atvremote")
                                    string(6, "1.0.0")
                                }
                            }
                        }
                        incoming.has(2) -> {
                            reply(socket) { message(2) { int(1, 622) } }
                            active = true
                        }
                        incoming.has(8) -> {
                            val value = incoming.message(8)?.int(1) ?: 0L
                            reply(socket) { message(9) { int(1, value) } }
                        }
                    }
                }
            } catch (e: Exception) {
                if (remoteSocket === socket) fail("The TV closed the connection: ${e.message}")
            }
        }
    }

    private fun fail(reason: String) {
        lastError = reason
        lastFailure = System.currentTimeMillis()
        active = false
        try {
            remoteSocket?.close()
        } catch (e: Exception) {
        }
        remoteSocket = null
    }

    private fun reply(socket: SSLSocket, build: ProtoWriter.() -> Unit) =
        synchronized(lock) { writeFrame(socket.outputStream, proto(build)) }

    // Pairing messages carry a protocol version and an OK status.
    private fun send(out: java.io.OutputStream, build: ProtoWriter.() -> Unit) =
        writeFrame(out, proto { int(1, 2); int(2, 200); build() })

    private fun expect(input: java.io.InputStream, field: Int) {
        val reply = readFrame(input)
        check(reply.int(2) == 200L && reply.has(field)) { "The TV said no (status ${reply.int(2)})" }
    }

    // TLS with our own certificate, which the TV remembers after pairing.

    // Tries the TV itself first, then its Wi-Fi address, in case the service only listens there.
    private fun open(port: Int): SSLSocket {
        val context = SSLContext.getInstance("TLS")
        context.init(arrayOf(KeystoreKeyManager(keyStore())), arrayOf(TrustAnyTv), null)
        var error: Exception = IllegalStateException("No network")
        for (host in listOfNotNull(HOST, RemoteServer.localIp())) {
            try {
                val tcp = Socket().apply { connect(InetSocketAddress(host, port), 2000) }
                return (context.socketFactory.createSocket(tcp, host, port, true) as SSLSocket).apply {
                    soTimeout = 0
                    startHandshake()
                }
            } catch (e: SSLHandshakeException) {
                throw e
            } catch (e: Exception) {
                error = e
            }
        }
        throw error
    }

    private fun keyStore(): KeyStore {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            val now = System.currentTimeMillis()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(2048)
                        .setDigests(
                            KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                            KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512,
                        )
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE, KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                        .setCertificateSubject(X500Principal("CN=atvremote/$CLIENT_NAME"))
                        .setCertificateSerialNumber(BigInteger.ONE)
                        .setCertificateNotBefore(Date(now - 86_400_000L))
                        .setCertificateNotAfter(Date(now + 20L * 365 * 86_400_000L))
                        .build()
                )
                generateKeyPair()
            }
        }
        return store
    }

    private class KeystoreKeyManager(private val store: KeyStore) : X509ExtendedKeyManager() {
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun getCertificateChain(alias: String?): Array<X509Certificate> =
            store.getCertificateChain(ALIAS).map { it as X509Certificate }.toTypedArray()
        override fun getPrivateKey(alias: String?) = store.getKey(ALIAS, null) as PrivateKey
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
    }

    // We only ever connect to this TV itself (127.0.0.1), whose certificate is self-signed.
    private object TrustAnyTv : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private fun prefs() = appContext.getSharedPreferences("atv_remote", Context.MODE_PRIVATE)

    private fun BigInteger.unsigned(): ByteArray {
        val bytes = toByteArray()
        return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private fun hex(text: String) = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun keyCodeFor(c: Char): Int? = when (c) {
        in 'a'..'z' -> 29 + (c - 'a')
        in '0'..'9' -> 7 + (c - '0')
        ' ' -> 62
        '.' -> 56
        ',' -> 55
        '-' -> 69
        '\'' -> 75
        '/' -> 76
        '@' -> 77
        else -> null
    }
}
