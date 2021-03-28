package com.github.nanodeath.async

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

internal class AsyncByteJsonSourceTest {
    @Test
    fun sanity() {
        runBlocking {
            val source = AsyncByteJsonSource(MyByteChannel("Hello World".toByteArray()))
            assertThat(source.read()).isEqualTo('H'.toInt())
            assertThat(source.read()).isEqualTo('e'.toInt())
            assertThat(source.read()).isEqualTo('l'.toInt())
            assertThat(source.read()).isEqualTo('l'.toInt())
            assertThat(source.read()).isEqualTo('o'.toInt())
            assertThat(source.read()).isEqualTo(' '.toInt())
            assertThat(source.readString(5)).isEqualTo("World")
        }
    }

    @Test
    fun `read emoji`() {
        runBlocking {
            val source = AsyncByteJsonSource(MyByteChannel("Hello \uD83C\uDF0E!".toByteArray()))
            assertThat(source.readString(6)).isEqualTo("Hello ")
            assertThat(source.read()).isEqualTo("🌎".codePointAt(0))
            assertThat(source.read()).isEqualTo('!'.toInt())
        }
    }

    @Test
    fun `readString emoji`() {
        runBlocking {
            val source = AsyncByteJsonSource(MyByteChannel("Hello \uD83C\uDF0E! what up".toByteArray()))
            assertThat(source.readString(8)).isEqualTo("Hello 🌎!")
        }
    }

    @Test
    fun peek() {
        runBlocking {
            val source = AsyncByteJsonSource(MyByteChannel("Hi".toByteArray()))
            assertThat(source.peek()).isEqualTo('H'.toInt())
            assertThat(source.peek()).isEqualTo('H'.toInt())
            assertThat(source.read()).isEqualTo('H'.toInt())
            assertThat(source.read()).isEqualTo('i'.toInt())
        }
    }

    @Test
    fun peekString() {
        runBlocking {
            val source = AsyncByteJsonSource(MyByteChannel("Hi".toByteArray()))
            assertThat(source.peekString(2)).isEqualTo("Hi")
            assertThat(source.peekString(2)).isEqualTo("Hi")
            assertThat(source.readString(2)).isEqualTo("Hi")
        }
    }

    @Test
    fun readPastEmpty() {
        runBlocking {
            val source = AsyncByteJsonSource(MyByteChannel("".toByteArray()))
            assertThat(source.read()).isEqualTo(-1)
        }
    }

    @Test
    fun readPastBlank() {
        runBlocking {
            val source = AsyncByteJsonSource(MyByteChannel(" ".toByteArray()))
            assertThat(source.readString(2)).isEqualTo(" ")
            assertThat(source.readString(2)).isEmpty()
        }
    }

    @Test
    fun takeWhile() {
        runBlocking {
            val source = AsyncByteJsonSource(MyByteChannel("Hello World"))
            assertThat(source.takeWhile { !it.toChar().isWhitespace() }.cpToStr()).isEqualTo("Hello")
            assertThat(source.readString(1)).isEqualTo(" ")
            assertThat(source.takeWhile { !it.toChar().isWhitespace() }.cpToStr()).isEqualTo("World")
        }
    }

    @Test
    fun skipCharacters() {
        runBlocking {
            val source = AsyncByteJsonSource(MyByteChannel("Hello"))
            source.skipCharacters(3)
            assertThat(source.readString(2)).isEqualTo("lo")
        }
    }

    @Test
    fun skipCharactersUnicode() {
        runBlocking {
            val source = AsyncByteJsonSource(MyByteChannel("🌎!"))
            source.skipCharacters(1)
            assertThat(source.readString(1)).isEqualTo("!")
        }
    }

    private suspend fun Flow<Int>.cpToStr() =
        buildString {
            collect {
                println("Got cp $it")
                appendCodePoint(it)
            }
        }
}

class MyByteChannel(private val data: ByteArray) : AsynchronousByteChannel {
    constructor(str: String) : this(str.toByteArray())

    private var atEof = data.isEmpty()

    override fun close() {
    }

    override fun isOpen(): Boolean {
        return true
    }

    override fun <A : Any?> read(dst: ByteBuffer, attachment: A, handler: CompletionHandler<Int, in A>) {
        if (atEof) {
            handler.completed(-1, attachment)
        } else {
            dst.put(data)
            atEof = true
            handler.completed(data.size, attachment)
        }
    }

    override fun read(dst: ByteBuffer): Future<Int> {
        return if (atEof) {
            FutureTask { -1 }
        } else {
            dst.put(data)
            atEof = true
            FutureTask { data.size }
        }
    }

    override fun <A : Any?> write(src: ByteBuffer, attachment: A, handler: CompletionHandler<Int, in A>) {
        throw UnsupportedOperationException()
    }

    override fun write(src: ByteBuffer): Future<Int> {
        throw UnsupportedOperationException()
    }

}