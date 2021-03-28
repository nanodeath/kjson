package com.github.nanodeath

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.CompletionHandler
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class CoreTests {
    @Test
    fun `large documents`() {
        runBlocking {
            val longText =
                "what if the document is large? if our internal buffer is insufficiently-sized, how to adapt?"
                    .repeat(100)
            val doc = """
            {"thought": "$longText"}
        """.trimIndent()
            val list = Json().parse(doc).toList()
            assertThat(list).containsExactly(
                Token.StartObject,
                Token.Key(Token.StringToken("thought")),
                Token.StringToken(longText),
                Token.EndObject
            )
        }
    }

    @Test
    fun `parse value parses the entire input`() {
        assertThatThrownBy {
            runBlocking {
                Json().parseValue("1a").toList()
            }
        }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `slow-to-load documents`() {
        val channel = DelayingByteChannel(LinkedList(listOf(
            100L to "[\"Hello".toByteArray(),
            50L to "...wait for it...".toByteArray(),
            200L to "World\"]".toByteArray()
        )))
        runBlocking {
            assertThat(Json().parse(channel).toList())
                .containsExactly(
                    Token.StartArray,
                    Token.StringToken("Hello...wait for it...World"),
                    Token.EndArray
                )
        }
    }
}

private class DelayingByteChannel(private val operations: Queue<Pair<Long, ByteArray>>) : AsynchronousByteChannel {
    private val executor = Executors.newSingleThreadScheduledExecutor()

    override fun close() {
        executor.shutdown()
    }

    override fun isOpen(): Boolean = !executor.isShutdown

    override fun <A : Any?> read(dst: ByteBuffer, attachment: A, handler: CompletionHandler<Int, in A>) {
        operations.poll()?.let { (delay, data) ->
            executor.schedule({
                dst.put(data)
                handler.completed(data.size, attachment)
            }, delay, TimeUnit.MILLISECONDS)
        } ?: handler.completed(-1, attachment)
    }

    override fun read(dst: ByteBuffer): Future<Int> {
        throw UnsupportedOperationException()
    }

    override fun <A : Any?> write(src: ByteBuffer, attachment: A, handler: CompletionHandler<Int, in A>) {
        throw UnsupportedOperationException()
    }

    override fun write(src: ByteBuffer): Future<Int> {
        throw UnsupportedOperationException()
    }
}
