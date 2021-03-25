@file:Suppress("SameParameterValue")

package com.github.nanodeath

import java.io.Reader
import java.nio.Buffer
import java.nio.CharBuffer

interface StateMachine {
    suspend fun SequenceScope<Token>.offer(char: Char): StateMachine
}

object StateMachines {
    object Collection : StateMachine {
        override suspend fun SequenceScope<Token>.offer(char: Char): StateMachine {
            when (char) {
                '[' -> yield(Token.StartArray)
                ']' -> yield(Token.EndArray)
            }
            return this@Collection
        }
    }
}

class Json {

    fun parse(reader: Reader): Sequence<Token> {
        val buffer = CharBuffer.allocate(1024)
        return sequence {
            while (true) {
                val bytesRead = reader.read(buffer)
                if (bytesRead <= 0) break
                buffer.flip()
                while (buffer.remaining() > 0) {
                    yieldAll(readObject(buffer))
                }
            }
        }
    }

    private fun readObject(buffer: CharBuffer): Sequence<Token> {
        buffer.mark()
        val b = buffer.get()
        return when (b) {
            '{' -> {
                buffer.reset()
                readMap(buffer)
            }
            '[' -> {
                buffer.reset()
                readArray(buffer)
//                emptySequence()
            }
            else -> printError(buffer, buffer.position() - 1, "Unexpected value $b")
        }
    }

    private fun readMap(buffer: CharBuffer): Sequence<Token> =
        sequence {
            expect(buffer, '{')
            yield(Token.StartObject)
            if (peek(buffer) == '}') {
                yield(Token.EndObject)
                return@sequence
            }
            while (true) {
                val readString = readString(buffer)
                yield(Token.Key(readString))
                expect(buffer, ':')
                val value = readAny(buffer)
                yieldAll(value)
                if (peek(buffer) == '}') {
                    expect(buffer, '}')
                    yield(Token.EndObject)
                    return@sequence
                }
                expect(buffer, ',')
            }
        }

    private fun readArray(buffer: CharBuffer): Sequence<Token> =
        sequence {
            expect(buffer, '[')
            yield(Token.StartArray)
            if (peek(buffer) == ']') {
                yield(Token.EndObject)
                return@sequence
            }
            while (true) {
                yieldAll(readAny(buffer))
                if (tryExpect(buffer, ']')) {
                    yield(Token.EndArray)
                    return@sequence
                }
                expect(buffer, ',')
            }
        }

    private fun tryReadMap(buffer: CharBuffer): Sequence<Token>? {
        if (peek(buffer) == '{') {
            return readMap(buffer)
        }
        return null
    }

    private fun tryReadArray(buffer: CharBuffer): Sequence<Token>? {
        if (peek(buffer) == '[') {
            return readArray(buffer)
        }
        return null
    }

    @Suppress("SameParameterValue")
    private fun expect(buffer: CharBuffer, vararg c: Char, ignoreWhitespace: Boolean = true): Char {
        while (true) {
            val b = buffer.get()
            when {
                b.isWhitespace() -> {
                    if (!ignoreWhitespace) {
                        error("Whitespace at ${buffer.position() - 1}")
                    }
                }
                b in c -> return b
                else -> printError(
                    buffer,
                    buffer.position() - 1,
                    "Unexpected character $b, expected ${c.joinToString()}"
                )
            }
        }
    }

    private fun tryExpect(buffer: CharBuffer, vararg c: Char, ignoreWhitespace: Boolean = true): Boolean {
        buffer.mark()
        while (true) {
            val b = buffer.get()
            when {
                b in c -> return true
                b.isWhitespace() -> {
                    if (!ignoreWhitespace) {
                        return false
                    }
                }
                else -> {
                    buffer.reset()
                    return false
                }
            }
        }
    }

    private fun peek(buffer: CharBuffer, skipWhitespace: Boolean = true): Char {
        while (true) {
            val b = buffer.get()
            when {
                skipWhitespace && b.isWhitespace() -> {
                }
                else -> {
                    buffer.rewindToPreviousPosition()
                    return b
                }
            }
        }
    }

    private fun printError(buffer: CharBuffer, position: Int, message: String): Nothing {
        buffer.rewind()
        System.err.println(buffer.toString())
        val sb = StringBuilder()
        repeat(position) {
            sb.append(' ')
        }
        sb.append('^')
        System.err.println(sb)
        val ex = IllegalStateException(message)
        ex.stackTrace = ex.stackTrace.drop(1).toTypedArray()
        throw ex
    }

    private fun readAny(buffer: CharBuffer): Sequence<Token> = tryReadAny(buffer)!!

    private fun tryReadAny(buffer: CharBuffer): Sequence<Token>? =
        tryReadString(buffer)?.let { sequenceOf(it) }
            ?: tryReadNumber(buffer)?.let { sequenceOf(it) }
            ?: tryReadMap(buffer)
            ?: tryReadArray(buffer)

    private fun tryReadNumber(buffer: CharBuffer): Token.Number? {
        buffer.mark()
        while (true) {
            val b = buffer.get()
            when {
                b.isWhitespace() -> Unit
                b in '0'..'9' -> {
                    buffer.reset()
                    return readNumber(buffer)
                }
                else -> {
                    buffer.reset()
                    return null
                }
            }
        }
    }

    private fun readNumber(buffer: CharBuffer): Token.Number {
        val originalLimit = buffer.limit()
        buffer.mark()
        while (true) {
            val current = buffer.get()
            if (current.isWhitespace()) {
                buffer.mark()
                continue
            }
            if (current !in '0'..'9') {
                buffer.limit(buffer.position() - 1)
                buffer.reset()
                break
            }
        }
        val number = buffer.toString()
        buffer.limit(originalLimit)
        buffer.position(buffer.position() + number.length)
        return Token.Number(number)
    }

    private fun tryReadString(buffer: CharBuffer): Token.StringToken? {
        buffer.mark()
        while (true) {
            val b = buffer.get()
            when {
                b.isWhitespace() -> Unit
                b == '"' -> {
                    buffer.rewindToPreviousPosition()
                    return readString(buffer)
                }
                else -> {
                    buffer.reset()
                    return null
                }
            }
        }
    }

    private fun readString(buffer: CharBuffer): Token.StringToken {
        val originalLimit = buffer.limit()
        expect(buffer, '"')
        buffer.mark()
        while (true) {
            val current = buffer.get()
            if (current == '"') {
                buffer.limit(buffer.position() - 1)
                buffer.reset()
                break
            }
        }
        val number = buffer.toString()
        buffer.limit(originalLimit)
        buffer.position(buffer.position() + number.length + 1)
        return Token.StringToken(number)
    }
}

private fun Buffer.previousPosition(): Int = position() - 1
private fun Buffer.rewindToPreviousPosition() = position(previousPosition())

fun main() {
//    Json().parse("[42, 24, [18]]".reader()).joinToString(separator = "\n", transform = { "$it  (${it.javaClass.simpleName})" }).let { println(it) }
    Json().parse("""{"foo": "bar", "baz": 10, "nestedMap": {"nested": "value"}}""".reader())
        .joinToString(separator = "\n", transform = { "$it  (${it.javaClass.simpleName})" }).let { println(it) }
}

sealed class Token(val value: String) {
    object StartArray : Token("[")
    object EndArray : Token("]")
    object StartObject : Token("{")
    object EndObject : Token("}")
    class Number(value: String) : Token(value)
    class StringToken(value: String) : Token(value)
    class Key(token: StringToken) : Token(token.value)
    class Value(token: Token) : Token(token.value)

    override fun toString() = value
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Token) return false

        if (value != other.value) return false

        return javaClass == other.javaClass
    }

    override fun hashCode(): Int = value.hashCode()
}