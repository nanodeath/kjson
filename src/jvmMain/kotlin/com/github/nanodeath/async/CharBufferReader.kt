package com.github.nanodeath.async

import java.io.Reader
import java.nio.CharBuffer

/**
 * [Reader] wrapper around a given [CharBuffer]. Supports marking.
 *
 * Not thread-safe.
 */
internal class CharBufferReader(private var charBuffer: CharBuffer): Reader() {
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        val charBufferRemaining = charBuffer.limit() - charBuffer.position()
        if (charBufferRemaining == 0) {
            return -1
        }
        val bytesRead = minOf(len, charBufferRemaining)
        charBuffer.get(cbuf, off, bytesRead)
        return bytesRead
    }

    override fun markSupported() = true

    override fun mark(readAheadLimit: Int) {
        charBuffer.mark()
    }

    override fun reset() {
        charBuffer.reset()
    }

    override fun skip(n: Long): Long {
        val toSkip = minOf(charBuffer.limit() - charBuffer.position(), n.toInt())
        charBuffer.position(charBuffer.position() + toSkip)
        return toSkip.toLong()
    }

    override fun close() {
        // nothing to do
    }
}
