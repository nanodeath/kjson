@file:Suppress("SameParameterValue")

package com.github.nanodeath

import java.io.Reader
import java.nio.CharBuffer

class Json(private val bufferSize: Int = 1024) {

    fun parse(string: String): Sequence<Token> = parse(string.reader())
    fun parseValue(string: String): Sequence<Token> {
        val reader = string.reader()
        val markable = if (reader.markSupported()) reader else reader.buffered(bufferSize)
        check(markable.markSupported())
        return sequence {
            yieldAll(markable.readAny())
            require(markable.peek() == 1) { "Input contains multiple values, expected one" }
        }
    }

    fun parse(reader: Reader): Sequence<Token> {
        val markable = if (reader.markSupported()) reader else reader.buffered(bufferSize)
        check(markable.markSupported())
        return sequence {
            yieldAll(readStructuredType(markable))
        }
    }

    private fun readStructuredType(buffer: Reader): Sequence<Token> {
        return when (val next = buffer.peek()) {
            Structural.BeginObject.int -> readMap(buffer)
            Structural.BeginArray.int -> readArray(buffer)
            else -> buffer.printError(0, "Unexpected value ${next.codepointToString()}")
        }
    }

    private fun readMap(buffer: Reader): Sequence<Token> =
        sequence {
            buffer.expect(Structural.BeginObject)
            yield(Token.StartObject)
            if (buffer.tryExpect(Structural.EndObject)) {
                yield(Token.EndObject)
                return@sequence
            }
            while (true) {
                val readString = buffer.readString()
                yield(Token.Key(readString))
                buffer.expect(Structural.NameSeparator)
                val value = buffer.readAny()
                yieldAll(value)
                if (buffer.tryExpect(Structural.EndObject)) {
                    yield(Token.EndObject)
                    return@sequence
                }
                buffer.expect(Structural.ValueSeparator)
            }
        }

    private fun readArray(buffer: Reader): Sequence<Token> =
        sequence {
            buffer.expect('['.toInt())
            yield(Token.StartArray)
            if (buffer.tryExpect(Structural.EndArray)) {
                yield(Token.EndArray)
                return@sequence
            }
            while (true) {
                buffer.skipWhitespace()
                yieldAll(buffer.readAny())
                if (buffer.tryExpect(Structural.EndArray)) {
                    yield(Token.EndArray)
                    return@sequence
                }
                buffer.expect(','.toInt())
                buffer.skipWhitespace()
            }
        }

    private fun tryReadMap(buffer: Reader): Sequence<Token>? {
        if (buffer.peek() == '{'.toInt()) {
            return readMap(buffer)
        }
        return null
    }

    private fun tryReadArray(buffer: Reader): Sequence<Token>? {
        if (buffer.peek() == '['.toInt()) {
            return readArray(buffer)
        }
        return null
    }

    private fun Reader.expect(c: Structural) {
        skipWhitespace()
        expect(c.int)
        skipWhitespace()
    }

    private fun Reader.expect(vararg c: Int): Int {
        when (val b = this.read()) {
            in c -> return b
            else -> this.printError(
                -1,
                "Unexpected character ${b.codepointToString()}, expected ${c.joinToString { it.codepointToString() }}"
            )
        }
    }

    private fun Int.codepointToString() = String(Character.toChars(this))

    private fun Reader.tryExpect(c: Structural): Boolean {
        skipWhitespace()
        mark(1)
        return when (this.read()) {
            c.int -> {
                skipWhitespace()
                true
            }
            else -> {
                this.reset()
                false
            }
        }
    }

    private fun Reader.tryExpect(c: Int): Boolean {
        mark(1)
        return when (this.read()) {
            c -> {
                true
            }
            else -> {
                this.reset()
                false
            }
        }
    }

    private fun Reader.peek(): Int {
        mark(1)
        return read().also { reset() }
    }

    private fun Reader.printError(position: Int, message: String): Nothing {
        System.err.println(toString())
        val sb = StringBuilder()
        repeat(position) {
            sb.append(' ')
        }
        sb.append('^')
        System.err.println(sb)
        val ex = IllegalArgumentException(message)
        ex.stackTrace = ex.stackTrace.drop(1).toTypedArray()
        throw ex
    }

    private fun Reader.readAny(): Sequence<Token> = tryReadAny() ?: printError(-1, message = "Failed to read any")

    private fun Reader.tryReadAny(): Sequence<Token>? =
        tryReadString()?.let { sequenceOf(it) }
            ?: tryReadNumber()?.let { sequenceOf(it) }
            ?: tryReadMap(this)
            ?: tryReadArray(this)
            ?: tryReadLiteral(this)

    private fun tryReadLiteral(reader: Reader): Sequence<Token>? {
        val longestLiteral = 5
        reader.mark(longestLiteral)
        val data = CharBuffer.allocate(longestLiteral)
        reader.read(data)
        data.flip()
        return when (data.toString()) {
            "null" -> sequenceOf(Token.Null)
            "false" -> sequenceOf(Token.False)
            "true" -> sequenceOf(Token.True)
            else -> {
                reader.reset()
                null
            }
        }
    }

    private fun Reader.tryReadNumber(): Token.Number? {
        val peek = peek()
        return when (peek) {
            in digits -> readNumber()
            '-'.toInt() -> readNumber()
            else -> null
        }
    }

    private fun Reader.readNumber(): Token.Number {
        val negative = tryExpect('-'.toInt())
        val sb = StringBuilder()
        // read integer
        while (true) {
            mark(1)
            when (val d = read()) {
                in digits -> sb.appendCodePoint(d)
                else -> {
                    reset()
                    break
                }
            }
        }
        val int = sb.toString()
        require(int.length <= 1 || int[0] != '0') { "Leading zeros not allowed" }
        val frac = if (tryExpect('.'.toInt())) {
            sb.clear()
            while (true) {
                when (val d = read()) {
                    in digits -> sb.append(d.toChar())
                    else -> {
                        reset()
                        break
                    }
                }
            }
            sb.toString()
        } else null
        with(sb) {
            sb.clear()
            if (negative) append('-')
            append(int)
            frac?.let { append('.').append(it) }
        }
        return Token.Number(sb.toString())
    }

    private fun Reader.tryReadString(): Token.StringToken? {
        mark(1)
        if (peek() == '"'.toInt()) {
            return readString()
        }
        return null
    }

    private fun Reader.readString(): Token.StringToken {
        this.expect('"'.toInt())
        val sb = StringBuilder()
        while (true) {
            val current = read()
            if (current == '"'.toInt()) {
                break
            } else {
                sb.appendCodePoint(current)
            }
        }
        return Token.StringToken(sb.toString())
    }

    private fun Reader.skipWhitespace() {
        while (true) {
            mark(1)
            if (!read().isJsonSpace()) {
                reset()
                break
            }
        }
    }

    private inline fun Int.isJsonSpace() =
        this == spaceInt || this == horizontalTabInt || this == lineFeedInt || this == carriageReturnInt

    private companion object {
        const val ZERO = '0'.toInt()
        val digits = '0'.toInt()..'9'.toInt()
        const val spaceInt = 0x20
        const val horizontalTabInt = 0x09
        const val lineFeedInt = 0x0A
        const val carriageReturnInt = 0x0D
    }
}

fun main() {
//    Json().parse("[42, 24, [18]]".reader()).joinToString(separator = "\n", transform = { "$it  (${it.javaClass.simpleName})" }).let { println(it) }
    Json().parse("""{"foo": "bar", "baz": 10, "nestedMap": {"nested": "value"}}""".reader())
        .joinToString(separator = "\n", transform = { "$it  (${it.javaClass.simpleName})" }).let { println(it) }
}

sealed class Structural(char: Char) {
    val int: Int = char.toInt()

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
    object Null : Token("null")
    object False : Token("false")
    object True : Token("true")

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