package com.github.nanodeath.async

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.Channels
import java.nio.channels.CompletionHandler
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

internal class AsyncInputStream(private val inputStream: InputStream) : AsynchronousByteChannel {
    private val channel = Channels.newChannel(inputStream)
    private var closed = false
    override fun close() {
        if (!closed) {
            channel.close()
            inputStream.close()
            closed = true
        }
    }

    override fun isOpen(): Boolean = !closed

    override fun <A : Any?> read(dst: ByteBuffer, attachment: A, handler: CompletionHandler<Int, in A>) {
        if (!dst.hasRemaining()) {
            handler.completed(0, attachment)
            return
        }
        try {
            val bytesRead = channel.read(dst)
            handler.completed(bytesRead, attachment)
        } catch (e: Exception) {
            handler.failed(e, attachment)
        }
    }

    override fun read(dst: ByteBuffer): Future<Int> {
        if (!dst.hasRemaining()) {
            return CompletableFuture.completedFuture(0)
        }
        return try {
            val bytesRead = channel.read(dst)
            CompletableFuture.completedFuture(bytesRead)
        } catch (e: Exception) {
            CompletableFuture.failedFuture(e)
        }
    }

    override fun <A : Any?> write(src: ByteBuffer, attachment: A, handler: CompletionHandler<Int, in A>?) {
        throw UnsupportedOperationException()
    }

    override fun write(src: ByteBuffer): Future<Int> {
        throw UnsupportedOperationException()
    }
}
