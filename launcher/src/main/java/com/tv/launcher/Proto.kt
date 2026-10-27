package com.tv.launcher

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** Just enough protobuf to talk to the TV's remote service. */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun int(field: Int, value: Long) {
        tag(field, 0)
        writeVarint(out, value)
    }

    fun bytes(field: Int, value: ByteArray) {
        tag(field, 2)
        writeVarint(out, value.size.toLong())
        out.write(value)
    }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray())

    fun message(field: Int, build: ProtoWriter.() -> Unit) = bytes(field, ProtoWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = writeVarint(out, ((field shl 3) or wireType).toLong())
}

fun proto(build: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply(build).toByteArray()

class ProtoMessage(bytes: ByteArray) {
    private val fields = HashMap<Int, Any>()

    init {
        val input = bytes.inputStream()
        read@ while (input.available() > 0) {
            val tag = readVarint(input)
            val field = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> fields[field] = readVarint(input)
                1 -> input.skip(8)
                2 -> fields[field] = readExactly(input, readVarint(input).toInt())
                5 -> input.skip(4)
                else -> break@read
            }
        }
    }

    fun has(field: Int) = field in fields

    fun int(field: Int): Long? = fields[field] as? Long

    fun bytes(field: Int): ByteArray? = fields[field] as? ByteArray

    fun message(field: Int): ProtoMessage? = bytes(field)?.let(::ProtoMessage)
}

/** Messages are sent as a varint length followed by the message. */
fun writeFrame(out: OutputStream, message: ByteArray) {
    val frame = ByteArrayOutputStream()
    writeVarint(frame, message.size.toLong())
    frame.write(message)
    out.write(frame.toByteArray())
    out.flush()
}

fun readFrame(input: InputStream): ProtoMessage = ProtoMessage(readExactly(input, readVarint(input).toInt()))

private fun writeVarint(out: OutputStream, value: Long) {
    var v = value
    while (v and 0x7FL.inv() != 0L) {
        out.write(((v and 0x7F) or 0x80).toInt())
        v = v ushr 7
    }
    out.write(v.toInt())
}

private fun readVarint(input: InputStream): Long {
    var result = 0L
    var shift = 0
    while (true) {
        val b = input.read()
        if (b < 0) throw EOFException()
        result = result or ((b and 0x7F).toLong() shl shift)
        if (b and 0x80 == 0) return result
        shift += 7
    }
}

private fun readExactly(input: InputStream, size: Int): ByteArray {
    val bytes = ByteArray(size)
    var read = 0
    while (read < size) {
        val n = input.read(bytes, read, size - read)
        if (n < 0) throw EOFException()
        read += n
    }
    return bytes
}
