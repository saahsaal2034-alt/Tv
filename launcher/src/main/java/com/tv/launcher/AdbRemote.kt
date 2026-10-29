package com.tv.launcher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Full control through the TV's own debugging connection, the way Shizuku
 * works, but built in: Vortrex connects to the TV's debugging service on this
 * same TV, the person approves it once on screen ("Always allow"), and then a
 * small helper ([KeyInjector]) presses real keys in every app.
 */
object AdbRemote {
    private const val PORT = 5555
    private const val ALIAS = "vortrex_adb"

    private lateinit var appContext: Context
    private val worker = Executors.newSingleThreadExecutor()

    private var connection: AdbConnection? = null
    private var helper: AdbStream? = null

    // Without the helper, fall back to running the "input" command per key (slower).
    private var useInputCommand = false
    private var typed = ""

    /** "off", "connecting", "approve" (waiting on the TV), "connected" or "error". */
    @Volatile
    var state = "off"
        private set

    @Volatile
    var lastError: String? = null
        private set

    val connected: Boolean
        get() = state == "connected"

    fun init(context: Context) {
        appContext = context.applicationContext
        // Reconnect by itself once approved, e.g. after the TV restarts.
        if (prefs().getBoolean("approved", false)) connectAsync()
    }

    fun connectAsync() = worker.execute { connect() }

    /** Presses a key; runs [fallback] instead if full control isn't available. */
    fun key(keyCode: Int, fallback: () -> Unit) = worker.execute {
        if (!run("key $keyCode")) fallback()
    }

    /** Types the phone's text by sending only what changed since last time. */
    fun type(text: String, fallback: () -> Unit) = worker.execute {
        if (!ensureConnected()) return@execute fallback()
        val common = typed.commonPrefixWith(text).length
        repeat(typed.length - common) { run("key 67") } // Delete.
        val added = text.substring(common)
        if (added.isNotEmpty()) run("text $added")
        typed = text
    }

    fun startTyping() = worker.execute { typed = "" }

    private fun ensureConnected(): Boolean {
        if (!connected && prefs().getBoolean("approved", false)) connect()
        return connected
    }

    private fun run(command: String): Boolean {
        if (!ensureConnected()) return false
        val conn = connection ?: return false
        return try {
            if (useInputCommand) {
                conn.exec("shell:" + inputCommand(command))
            } else {
                helper?.write("$command\n".toByteArray()) ?: return false
            }
            true
        } catch (e: Exception) {
            fail("Lost connection: ${e.message}")
            false
        }
    }

    private fun connect() {
        if (connected) return
        state = "connecting"
        try {
            val (privateKey, publicKey) = keys()
            val conn = AdbConnection.open(hosts(), PORT, privateKey, publicKey) { state = "approve" }
            connection = conn
            prefs().edit().putBoolean("approved", true).apply()

            // Start the key helper from Vortrex's own APK.
            val pkg = appContext.packageName
            val stream = conn.open(
                "shell:CLASSPATH=\$(pm path $pkg | cut -d: -f2) exec app_process /system/bin ${KeyInjector::class.java.name}"
            )
            useInputCommand = stream == null || !stream.waitFor("ready", 5000)
            helper = if (useInputCommand) null else stream
            lastError = if (useInputCommand) "Using the slower key method: ${stream?.text()?.take(120)}" else null
            state = "connected"
        } catch (e: Exception) {
            fail(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun fail(reason: String) {
        lastError = reason
        state = "error"
        helper = null
        connection?.close()
        connection = null
    }

    private fun inputCommand(command: String): String = when {
        command.startsWith("key ") -> "input keyevent ${command.substring(4)}"
        // "input text" needs spaces as %s and the text quoted.
        else -> "input text '" + command.substring(5).replace("'", "").replace(" ", "%s") + "'"
    }

    private fun hosts() = listOfNotNull("127.0.0.1", RemoteServer.localIp()).distinct()

    private fun prefs() = appContext.getSharedPreferences("adb_remote", Context.MODE_PRIVATE)

    // The key the TV remembers when "Always allow" is ticked.
    private fun keys(): Pair<PrivateKey, ByteArray> {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setKeySize(2048)
                        .setDigests(KeyProperties.DIGEST_NONE)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .build()
                )
                generateKeyPair()
            }
        }
        val privateKey = store.getKey(ALIAS, null) as PrivateKey
        val publicKey = store.getCertificate(ALIAS).publicKey as RSAPublicKey
        return privateKey to adbPublicKey(publicKey)
    }

    /** The public key in the format adb uses, followed by a name the TV shows. */
    private fun adbPublicKey(key: RSAPublicKey): ByteArray {
        val words = 64 // 2048 bits in 32-bit words.
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n = key.modulus
        val n0inv = r32.subtract(n.mod(r32).modInverse(r32))
        val rr = BigInteger.ONE.shiftLeft(words * 64).mod(n)
        val buffer = ByteBuffer.allocate(4 + 4 + words * 4 + words * 4 + 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(words)
        buffer.putInt(n0inv.toInt())
        for (i in 0 until words) buffer.putInt(n.shiftRight(32 * i).toInt())
        for (i in 0 until words) buffer.putInt(rr.shiftRight(32 * i).toInt())
        buffer.putInt(key.publicExponent.toInt())
        val encoded = Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
        return "$encoded vortrex@tv\u0000".toByteArray()
    }
}

/** A minimal adb protocol client: connect, authenticate, open streams. */
private class AdbConnection private constructor(private val socket: Socket) {
    private val out: OutputStream = socket.getOutputStream()
    private val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
    private val streams = ConcurrentHashMap<Int, AdbStream>()
    private val nextId = AtomicInteger(1)

    class Message(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    fun open(service: String): AdbStream? {
        val stream = AdbStream(this, nextId.getAndIncrement())
        streams[stream.localId] = stream
        write(OPEN, stream.localId, 0, "$service\u0000".toByteArray())
        return if (stream.awaitOkay(5000)) stream else null
    }

    /** Runs a one-off command and waits for it to finish. */
    fun exec(service: String) {
        val stream = open(service) ?: error("Couldn't run the command")
        stream.waitForClose(5000)
    }

    fun write(command: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).putInt(arg0).putInt(arg1).putInt(data.size)
            .putInt(data.sumOf { it.toInt() and 0xFF }).putInt(command.inv())
        synchronized(out) {
            out.write(header.array())
            out.write(data)
            out.flush()
        }
    }

    fun read(): Message {
        val header = ByteArray(24).also(input::readFully)
        val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = b.int
        val arg0 = b.int
        val arg1 = b.int
        val length = b.int
        val data = ByteArray(length).also(input::readFully)
        return Message(command, arg0, arg1, data)
    }

    fun close() {
        try {
            socket.close()
        } catch (e: Exception) {
        }
        streams.values.forEach { it.onClose() }
    }

    private fun startReading() = thread(name = "adb-reader", isDaemon = true) {
        try {
            while (true) {
                val m = read()
                val stream = streams[m.arg1] ?: continue
                when (m.command) {
                    OKAY -> stream.onOkay(m.arg0)
                    WRTE -> {
                        stream.onData(m.data)
                        write(OKAY, m.arg1, m.arg0)
                    }
                    CLSE -> {
                        stream.onClose()
                        streams.remove(m.arg1)
                    }
                }
            }
        } catch (e: Exception) {
            close()
        }
    }

    companion object {
        const val CNXN = 0x4e584e43
        const val AUTH = 0x48545541
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val CLSE = 0x45534c43
        const val WRTE = 0x45545257

        // SHA-1 DigestInfo prefix: adb signs its token as if it were a SHA-1 hash.
        private val SHA1_PREFIX = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
        )

        fun open(hosts: List<String>, port: Int, key: PrivateKey, publicKey: ByteArray, onApprovalNeeded: () -> Unit): AdbConnection {
            var lastError: Exception = IllegalStateException("No network")
            for (host in hosts) {
                try {
                    val socket = Socket().apply { connect(InetSocketAddress(host, port), 2000) }
                    return AdbConnection(socket).also { it.handshake(key, publicKey, onApprovalNeeded) }
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw IllegalStateException(
                "Can't reach the TV's debugging service. Is USB debugging on? (${lastError.message})"
            )
        }
    }

    private fun handshake(key: PrivateKey, publicKey: ByteArray, onApprovalNeeded: () -> Unit) {
        // Long timeout: the person may need a moment to press "Allow" on the TV.
        socket.soTimeout = 90_000
        write(CNXN, 0x01000000, 4096, "host::\u0000".toByteArray())
        var signed = false
        var sentKey = false
        while (true) {
            val m = try {
                read()
            } catch (e: java.net.SocketTimeoutException) {
                throw IllegalStateException("The TV didn't answer. Choose Always allow, then OK, and try again.")
            }
            when (m.command) {
                CNXN -> break
                AUTH -> when {
                    !signed -> {
                        val signature = Signature.getInstance("NONEwithRSA").run {
                            initSign(key)
                            update(SHA1_PREFIX + m.data)
                            sign()
                        }
                        write(AUTH, 2, 0, signature)
                        signed = true
                    }
                    !sentKey -> {
                        // Not approved yet: send our key so the TV asks the person.
                        write(AUTH, 3, 0, publicKey)
                        sentKey = true
                        onApprovalNeeded()
                    }
                    else -> throw IllegalStateException("The TV didn't allow the connection")
                }
            }
        }
        socket.soTimeout = 0
        startReading()
    }
}

/** One adb stream: write to it and collect what it prints. */
private class AdbStream(private val connection: AdbConnection, val localId: Int) {
    @Volatile
    private var remoteId = 0

    @Volatile
    private var closed = false
    private val acks = LinkedBlockingQueue<Boolean>()
    private val output = ByteArrayOutputStream()

    fun awaitOkay(timeoutMs: Long): Boolean = acks.poll(timeoutMs, TimeUnit.MILLISECONDS) == true && !closed

    // adb sends one OKAY per write before the next write is allowed.
    fun write(data: ByteArray) {
        check(!closed) { "Stream closed" }
        connection.write(AdbConnection.WRTE, localId, remoteId, data)
        check(awaitOkay(3000)) { "The TV stopped answering" }
    }

    fun onOkay(remote: Int) {
        remoteId = remote
        acks.offer(true)
    }

    fun onData(data: ByteArray) = synchronized(output) {
        output.write(data)
        (output as Object).notifyAll()
    }

    fun onClose() {
        closed = true
        acks.offer(false)
        synchronized(output) { (output as Object).notifyAll() }
    }

    fun text(): String = synchronized(output) { output.toString() }

    /** Waits until the output contains [marker]; false if it didn't in time. */
    fun waitFor(marker: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(output) {
            while (marker !in output.toString()) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0 || closed) return false
                (output as Object).wait(left)
            }
        }
        return true
    }

    fun waitForClose(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(output) {
            while (!closed) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) return
                (output as Object).wait(left)
            }
        }
    }
}
