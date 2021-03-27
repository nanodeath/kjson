@file:Suppress("SameParameterValue")

package com.github.nanodeath

import java.io.Reader
import java.nio.CharBuffer

class Json {

    fun parse(string: String): Sequence<Token> = parse(string.reader())
    fun parseValue(string: String): Sequence<Token> {
        val reader = string.reader().markable()
        check(reader.markSupported())
        return sequence {
            yieldAll(reader.readAny())
            require(reader.peek() == -1) { "Input contains multiple values, expected one" }
        }
    }

    fun parse(reader: Reader): Sequence<Token> {
        val markable = reader.markable()
        check(markable.markSupported())
        return sequence {
            yieldAll(readStructuredType(markable))
            require(reader.peek() == -1) { "Input contains multiple values, expected one" }
        }
    }

    private fun readStructuredType(reader: Reader): Sequence<Token> =
        when (val next = reader.peek()) {
            Structural.BeginObject.int -> readMap(reader)
            Structural.BeginArray.int -> readArray(reader)
            else -> reader.printError(0, "Unexpected value ${next.codepointToString()}")
        }

    private fun readMap(reader: Reader): Sequence<Token> =
        sequence {
            reader.expect(Structural.BeginObject)
            yield(Token.StartObject)
            if (reader.tryExpect(Structural.EndObject)) {
                yield(Token.EndObject)
                return@sequence
            }
            while (true) {
                val readString = reader.readString()
                yield(Token.Key(readString))
                reader.expect(Structural.NameSeparator)
                val value = reader.readAny()
                yieldAll(value)
                if (reader.tryExpect(Structural.EndObject)) {
                    yield(Token.EndObject)
                    return@sequence
                }
                reader.expect(Structural.ValueSeparator)
            }
        }

    private fun readArray(reader: Reader): Sequence<Token> =
        sequence {
            reader.expect(Structural.BeginArray)
            yield(Token.StartArray)
            if (reader.tryExpect(Structural.EndArray)) {
                yield(Token.EndArray)
                return@sequence
            }
            while (true) {
                yieldAll(reader.readAny())
                if (reader.tryExpect(Structural.EndArray)) {
                    yield(Token.EndArray)
                    return@sequence
                }
                reader.expect(Structural.ValueSeparator)
            }
        }

    private fun tryReadMap(reader: Reader): Sequence<Token>? =
        if (reader.peek() == Structural.BeginObject.int) {
            readMap(reader)
        } else null

    private fun tryReadArray(reader: Reader): Sequence<Token>? {
        return if (reader.peek() == Structural.BeginArray.int) {
            readArray(reader)
        } else null
    }

    private fun Reader.expect(char: Structural) {
        skipWhitespace()
        expect(char.int)
        skipWhitespace()
    }

    private fun Reader.expect(character: Int): Int {
        when (val int = this.read()) {
            character -> return int
            else -> this.printError(
                -1,
                "Unexpected character ${int.codepointToString()}, expected ${character.codepointToString()}"
            )
        }
    }



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
        val longestLiteral = 5 // length of `false`
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
        // read fraction
        val frac = if (tryExpect('.'.toInt())) {
            sb.clear()
            while (true) {
                mark(1)
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
        // build up string representation again.
        // eventually we'll want to do something smarter with the components.
        with(sb) {
            sb.clear()
            if (negative) append('-')
            append(int)
            frac?.let { append('.').append(it) }
        }
        return Token.Number(sb.toString())
    }

    private fun Reader.tryReadString(): Token.StringToken? =
        if (peek() == quote) {
            readString()
        } else {
            null
        }

    private fun Reader.readString(): Token.StringToken {
        expect(quote)
        val sb = StringBuilder()
        while (true) {
            val current = read()
            if (current == escape) {
                // If it's an escape character, we immediately append whatever the next character is
                // unless it's a `u`, and then we treat it as a unicode sequence.
                if (tryExpect(u)) { // unicode escape characters, like \u1234
                    val buffer = CharBuffer.allocate(4)
                    read(buffer)
                    val hex = buffer.flip().toString()
                    hex.validateHex()
                    sb.appendCodePoint(hex.toInt(16))
                } else {
                    sb.appendCodePoint(read())
                }
            } else if (current == quote) {
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

    private fun Int.isJsonSpace() =
        this == spaceInt || this == horizontalTabInt || this == lineFeedInt || this == carriageReturnInt

    private companion object {
        val digits = '0'.toInt()..'9'.toInt()
        const val spaceInt = 0x20
        const val horizontalTabInt = 0x09
        const val lineFeedInt = 0x0A
        const val carriageReturnInt = 0x0D
        const val escape = '\\'.toInt()
        const val u = 'u'.toInt()
        const val quote = '"'.toInt()
    }
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

private fun Reader.markable() = if (this.markSupported()) this else this.buffered()
private fun Int.codepointToString() = String(Character.toChars(this))