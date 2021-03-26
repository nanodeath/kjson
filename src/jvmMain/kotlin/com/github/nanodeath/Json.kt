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

    fun parse(string: String): Sequence<Token> = parse(string.reader())
    fun parseAny(string: String): Sequence<Token> {
        val buffer = CharBuffer.allocate(1024)
        buffer.put(string)
        buffer.flip()
        return sequence {
            yieldAll(buffer.readAny())
        }
    }

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
        return when (val b = buffer.get()) {
            Structural.BeginObject.char -> {
                buffer.reset()
                readMap(buffer)
            }
            Structural.BeginArray.char -> {
                buffer.reset()
                readArray(buffer)
            }
            else -> buffer.printError(buffer.position() - 1, "Unexpected value $b")
        }
    }

    private fun readMap(buffer: CharBuffer): Sequence<Token> =
        sequence {
            buffer.expectStructural(Structural.BeginObject)
            yield(Token.StartObject)
            if (buffer.tryExpectStructural(Structural.EndObject)) {
                yield(Token.EndObject)
                return@sequence
            }
            while (true) {
                val readString = buffer.readString()
                yield(Token.Key(readString))
                buffer.expectStructural(Structural.NameSeparator)
                val value = buffer.readAny()
                yieldAll(value)
                if (buffer.tryExpectStructural(Structural.EndObject)) {
                    yield(Token.EndObject)
                    return@sequence
                }
                buffer.expectStructural(Structural.ValueSeparator)
            }
        }

    private fun readArray(buffer: CharBuffer): Sequence<Token> =
        sequence {
            buffer.expect('[')
            yield(Token.StartArray)
            if (buffer.tryExpectStructural(Structural.EndArray)) {
                yield(Token.EndArray)
                return@sequence
            }
            while (true) {
                buffer.skipWhitespace()
                yieldAll(buffer.readAny())
                if (buffer.tryExpectStructural(Structural.EndArray)) {
                    yield(Token.EndArray)
                    return@sequence
                }
                buffer.expect(',')
                buffer.skipWhitespace()
            }
        }

    private fun tryReadMap(buffer: CharBuffer): Sequence<Token>? {
        if (buffer.peek() == '{') {
            return readMap(buffer)
        }
        return null
    }

    private fun tryReadArray(buffer: CharBuffer): Sequence<Token>? {
        if (buffer.peek() == '[') {
            return readArray(buffer)
        }
        return null
    }

    private fun CharBuffer.expectStructural(c: Structural) {
        skipWhitespace()
        expect(c.char, ignoreWhitespace = false)
        skipWhitespace()
    }

    @Suppress("SameParameterValue")
    private fun CharBuffer.expect(vararg c: Char, ignoreWhitespace: Boolean = true): Char {
        while (true) {
            val b = this.get()
            when {
                b.isWhitespace() -> {
                    if (!ignoreWhitespace) {
                        error("Whitespace at ${this.position() - 1}")
                    }
                }
                b in c -> return b
                else -> this.printError(
                    this.position() - 1,
                    "Unexpected character $b, expected ${c.joinToString()}"
                )
            }
        }
    }

    private fun CharBuffer.tryExpectStructural(c: Structural): Boolean = tryExpect(c.char)

    private fun CharBuffer.tryExpect(c: Char): Boolean {
        if (position() >= limit()) return false
        this.mark()
        skipWhitespace()
        return when (this.get()) {
            c -> {
                skipWhitespace()
                true
            }
            else -> {
                this.reset()
                false
            }
        }
    }

    private fun CharBuffer.peek(): Char = this[position()]

    private fun CharBuffer.printError(position: Int = position(), message: String): Nothing {
        rewind()
        System.err.println(toString())
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

    private fun CharBuffer.readAny(): Sequence<Token> = tryReadAny() ?: printError(message = "Failed to read any")

    private fun CharBuffer.tryReadAny(): Sequence<Token>? =
        tryReadString()?.let { sequenceOf(it) }
            ?: tryReadNumber()?.let { sequenceOf(it) }
            ?: tryReadMap(this)
            ?: tryReadArray(this)

    private fun CharBuffer.tryReadNumber(): Token.Number? {
        mark()
        while (true) {
            val b = get()
            when {
                b.isWhitespace() -> Unit
                b in '0'..'9' || b == '-' -> {
                    reset()
                    return readNumber()
                }
                else -> {
                    reset()
                    return null
                }
            }
        }
    }

    private fun CharBuffer.readNumber(): Token.Number {
        val originalLimit = limit()
        val negative = tryExpect('-')
        mark()
        // read integer
        while (true) {
            if (position() >= limit()) {
                reset()
                break
            }
            when (get()) {
                in '0'..'9' -> {
                }
                else -> {
                    limit(position() - 1)
                    reset()
                    break
                }
            }
        }
        val int = toString()
        limit(originalLimit)
        position(position() + int.length)
        val frac = if (tryExpect('.')) {
            mark()
            while (true) {
                if (position() >= limit()) {
                    reset()
                    break
                }
                when (get()) {
                    in '0'..'9' -> {
                    }
                    else -> {
                        limit(position() - 1)
                        reset()
                        break
                    }
                }
            }
            toString().also { limit(originalLimit) }
        } else null
        val str = buildString {
            if (negative) append('-')
            append(int)
            frac?.let { append('.').append(it) }
        }
        return Token.Number(str)
    }

    private fun CharBuffer.tryReadString(): Token.StringToken? {
        mark()
        while (true) {
            val b = get()
            when {
                b.isWhitespace() -> Unit
                b == '"' -> {
                    rewindToPreviousPosition()
                    return readString()
                }
                else -> {
                    reset()
                    return null
                }
            }
        }
    }

    private fun CharBuffer.readString(): Token.StringToken {
        val originalLimit = limit()
        this.expect('"')
        mark()
        while (true) {
            val current = get()
            if (current == '"') {
                limit(position() - 1)
                reset()
                break
            }
        }
        val number = toString()
        limit(originalLimit)
        position(position() + number.length + 1)
        return Token.StringToken(number)
    }

    private fun CharBuffer.skipWhitespace() {
        while (position() < limit() && get(position()).isJsonSpace()) {
            // consume
            position(position() + 1)
        }
    }

    private inline fun Char.isJsonSpace() =
        this == space || this == horizontalTab || this == lineFeed || this == carriageReturn

    private companion object {
        const val space = 0x20.toChar()
        const val horizontalTab = 0x09.toChar()
        const val lineFeed = 0x0A.toChar()
        const val carriageReturn = 0x0D.toChar()
    }
}

private fun Buffer.previousPosition(): Int = position() - 1
private fun Buffer.rewindToPreviousPosition() = position(previousPosition())

fun main() {
//    Json().parse("[42, 24, [18]]".reader()).joinToString(separator = "\n", transform = { "$it  (${it.javaClass.simpleName})" }).let { println(it) }
    Json().parse("""{"foo": "bar", "baz": 10, "nestedMap": {"nested": "value"}}""".reader())
        .joinToString(separator = "\n", transform = { "$it  (${it.javaClass.simpleName})" }).let { println(it) }
}

sealed class Structural(val char: Char) {
    object BeginArray : Structural('[')
    object BeginObject : Structural('{')
    object EndArray : Structural(']')
    object EndObject : Structural('}')
    object NameSeparator : Structural(':')
    object ValueSeparator : Structural(',')
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