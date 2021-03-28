package com.github.nanodeath.async

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.CompletionHandler
import java.nio.charset.Charset
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal interface AsyncJsonSource {

    /** Consume the next codepoint and advance the internal position. */
    suspend fun read(): Int

    /** Return the next codepoint without advancing the internal position. */
    suspend fun peek(): Int

    /** Consume the next [count] codepoints and advance the internal position. */
    suspend fun readString(count: Int): String

    /** Return the next [count] codepoints without advancing the internal position. */
    suspend fun peekString(count: Int): String

    /**
     * Incrementally take codepoints so long as [predicate] is true (or until EOF) and return them as a sequence,
     * while advancing the internal position.
     */
    fun takeWhile(predicate: (Int) -> Boolean): Flow<Int>

    /**
     * Advances the internal position by [count] characters.
     */
    suspend fun skipCharacters(count: Int): Int
}

internal class AsyncByteJsonSource(
    private val byteChannel: AsynchronousByteChannel,
    encoding: Charset = Charsets.UTF_8,
    bufferSize: Int = 4 * 1024
) : AsyncJsonSource {
    //region Fields
    private val byteBuffer = ByteBuffer.allocate(bufferSize)
    private val charBuffer = CharBuffer.allocate(byteBuffer.capacity() / 2)
    private val reader = CharBufferReader(charBuffer)
    private val decoder = encoding.newDecoder()
    //endregion

    init {
        byteBuffer.limit(0)
        charBuffer.limit(0)
    }

    //region API
    override suspend fun read(): Int {
        ensureChars(2)
        return readUnchecked()
    }

    override suspend fun peek(): Int {
        ensureChars(2)
        reader.mark(2)
        return readUnchecked().also { reader.reset() }
    }

    override suspend fun readString(count: Int): String {
        ensureChars(count)
        return readStringUnchecked(count)
    }

    override suspend fun peekString(count: Int): String {
        ensureChars(count)
        reader.mark(count)
        return readStringUnchecked(count).also { reader.reset() }
    }

    override fun takeWhile(predicate: (Int) -> Boolean): Flow<Int> =
        flow {
            while (true) {
                val next = peek()
                if (next >= 0 && predicate(next)) {
                    emit(read())
                } else {
                    break
                }
            }
        }

    override suspend fun skipCharacters(count: Int): Int {
        ensureChars(count * 2)
        var charsSkipped = 0
        val chars = CharArray(1)
        while (charsSkipped < count) {
            val charsRead = reader.read(chars, 0, 1)
            if (charsRead < 0) {
                break
            }
            if (chars[0].isHighSurrogate()) {
                reader.read(chars, 0, 1)
            }
            charsSkipped++
        }
        return charsSkipped
    }
    //endregion

    //region Internal API Helpers
    private fun readUnchecked(): Int {
        val char = reader.read()
        if (!char.toChar().isHighSurrogate()) {
            return char
        }
        val lowSurrogate = reader.read()
        return Character.toCodePoint(char.toChar(), lowSurrogate.toChar())
    }

    private fun readStringUnchecked(count: Int): String {
        var toRead = 0
        charBuffer.mark()
        for (i in 0 until count) {
            if (!charBuffer.hasRemaining()) {
                break
            }
            val next = charBuffer.get()
            toRead++
            if (next.isHighSurrogate()) {
                charBuffer.get()
                toRead++
            }
        }
        charBuffer.reset()

        val oldLimit = charBuffer.limit()
        val newLimit = minOf(charBuffer.position() + toRead, oldLimit) // ensure we don't read past EOF
        val value = charBuffer.limit(newLimit).toString()
        charBuffer.position(charBuffer.limit())
        charBuffer.limit(oldLimit)
        return value
    }
    //endregion

    //region Utility
    private suspend fun ensureChars(count: Int) {
        if (charBuffer.limit() - charBuffer.position() < count) {
            println("ensureChars($count): ${charBuffer.limit()} - ${charBuffer.position()} < $count")
            charBuffer.compact()
            val bytesRead = fillByteBuffer()
            val result = decoder.decode(byteBuffer, charBuffer, bytesRead == -1)
            charBuffer.flip()
            println(result)
        }
    }

    private suspend fun fillByteBuffer(): Int {
        byteBuffer.compact()
        return suspendCoroutine { continuation ->
            byteChannel.read(byteBuffer, Unit, object : CompletionHandler<Int, Unit> {
                override fun completed(result: Int, attachment: Unit) {
                    byteBuffer.flip()
                    byteBuffer.position(0)
                    continuation.resume(result)
                }

                override fun failed(exc: Throwable, attachment: Unit) {
                    continuation.resumeWithException(exc)
                }
            })
        }
    }
    //endregion
}