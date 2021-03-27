package com.github.nanodeath

import java.io.Reader
import java.nio.CharBuffer

internal interface JsonSource {
    /** Consume the next codepoint and advance the internal position. */
    fun read(): Int

    /** Return the next codepoint without advancing the internal position. */
    fun peek(): Int

    /** Consume the next [count] codepoints and advance the internal position. */
    fun readString(count: Int): String

    /** Return the next [count] codepoints without advancing the internal position. */
    fun peekString(count: Int): String

    /**
     * Incrementally take codepoints so long as [predicate] is true (or until EOF) and return them as a sequence,
     * while advancing the internal position.
     */
    fun takeWhile(predicate: (Int) -> Boolean): Sequence<Int>

    /**
     * Advances the internal position by [count] characters.
     */
    fun skipCharacters(count: Int)
}

internal class ReaderJsonSource(private val reader: Reader) : JsonSource {
    init {
        require(reader.markSupported())
    }

    private val buffer = CharBuffer.allocate(16)

    override fun read(): Int = reader.read()

    override fun peek(): Int {
        reader.apply {
            mark(1)
            return read().also { reset() }
        }
    }

    override fun readString(count: Int): String = provideString(count, reset = false)

    override fun peekString(count: Int): String = provideString(count, reset = true)

    private fun provideString(count: Int, reset: Boolean): String {
        require(count <= buffer.capacity())
        reader.mark(count)
        buffer.clear()
        buffer.limit(count)
        reader.read(buffer)
        if (reset) {
            reader.reset()
        }
        return buffer.flip().toString()
    }

    override fun takeWhile(predicate: (Int) -> Boolean): Sequence<Int> =
        sequence {
            while (true) {
                reader.mark(1)
                val char = reader.read()
                if (predicate(char)) {
                    yield(char)
                } else {
                    reader.reset()
                    break
                }
            }
        }

    override fun skipCharacters(count: Int) {
        reader.skip(count.toLong())
    }
}