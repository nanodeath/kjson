@file:Suppress("SameParameterValue")

package com.github.nanodeath

import kotlinx.coroutines.flow.*
import java.io.Reader

class Json {

    fun parse(string: String): Flow<Token> = parse(string.reader())
    fun parseValue(string: String): Flow<Token> {
        val reader = string.reader().markable()
        val markable = reader.markable()
        check(markable.markSupported())
        return flow {
            emitAll(ReaderJsonSource(markable).readAny())
            require(reader.peek() == -1) { "Input contains multiple values, expected one" }
        }
    }

    fun parse(reader: Reader): Flow<Token> {
        val markable = reader.markable()
        check(markable.markSupported())
        return flow {
            emitAll(readStructuredType(ReaderJsonSource(markable)))
            require(reader.peek() == -1) { "Input contains multiple values, expected one" }
        }
    }

    private fun readStructuredType(reader: JsonSource): Flow<Token> =
        when (val next = reader.peek()) {
            Structural.BeginObject.int -> readMap(reader)
            Structural.BeginArray.int -> readArray(reader)
            else -> reader.printError(0, "Unexpected value ${next.codepointToString()}")
        }

    private fun readMap(reader: JsonSource): Flow<Token> =
        flow {
            reader.expect(Structural.BeginObject)
            emit(Token.StartObject)
            if (reader.tryExpect(Structural.EndObject)) {
                emit(Token.EndObject)
                return@flow
            }
            while (true) {
                val readString = reader.readString()
                emit(Token.Key(readString))
                reader.expect(Structural.NameSeparator)
                val value = reader.readAny()
                emitAll(value)
                if (reader.tryExpect(Structural.EndObject)) {
                    emit(Token.EndObject)
                    return@flow
                }
                reader.expect(Structural.ValueSeparator)
            }
        }

    private fun readArray(reader: JsonSource): Flow<Token> =
        flow {
            reader.expect(Structural.BeginArray)
            emit(Token.StartArray)
            if (reader.tryExpect(Structural.EndArray)) {
                emit(Token.EndArray)
                return@flow
            }
            while (true) {
                emitAll(reader.readAny())
                if (reader.tryExpect(Structural.EndArray)) {
                    emit(Token.EndArray)
                    return@flow
                }
                reader.expect(Structural.ValueSeparator)
            }
        }

    private fun tryReadMap(reader: JsonSource): Flow<Token>? =
        if (reader.peek() == Structural.BeginObject.int) {
            readMap(reader)
        } else null

    private fun tryReadArray(reader: JsonSource): Flow<Token>? {
        return if (reader.peek() == Structural.BeginArray.int) {
            readArray(reader)
        } else null
    }

    private fun JsonSource.expect(char: Structural) {
        skipWhitespace()
        expect(char.int)
        skipWhitespace()
    }

    private fun JsonSource.expect(character: Int): Int {
        when (val int = this.read()) {
            character -> return int
            else -> this.printError(
                -1,
                "Unexpected character ${int.codepointToString()}, expected ${character.codepointToString()}"
            )
        }
    }


    private fun JsonSource.tryExpect(c: Structural): Boolean {
        skipWhitespace()

        return if (peek() == c.int) {
            read()
            skipWhitespace()
            true
        } else {
            false
        }
    }

    private fun JsonSource.tryExpect(c: Int): Boolean {
        return if (peek() == c) {
            read()
            true
        } else {
            false
        }
    }

    private fun Reader.peek(): Int {
        mark(1)
        return read().also { reset() }
    }

    private fun JsonSource.printError(position: Int, message: String): Nothing {
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

    private fun JsonSource.readAny(): Flow<Token> = tryReadAny() ?: printError(-1, message = "Failed to read any")

    private fun JsonSource.tryReadAny(): Flow<Token>? =
        tryReadString()?.let { flowOf(it) }
            ?: tryReadNumber()?.let { flowOf(it) }
            ?: tryReadMap(this)
            ?: tryReadArray(this)
            ?: tryReadLiteral(this)

    private fun tryReadLiteral(reader: JsonSource): Flow<Token>? {
        val next5 = reader.peekString(5)
        return when (next5) {
            "null" -> flowOf(Token.Null).also { reader.skipCharacters(4) }
            "false" -> flowOf(Token.False).also { reader.skipCharacters(5) }
            "true" -> flowOf(Token.True).also { reader.skipCharacters(4) }
            else -> null
        }
    }

    private fun JsonSource.tryReadNumber(): Token.Number? {
        val peek = peek()
        return when (peek) {
            in digits -> readNumber()
            '-'.toInt() -> readNumber()
            else -> null
        }
    }

    private fun JsonSource.readNumber(): Token.Number {
        val negative = tryExpect('-'.toInt())
        val sb = StringBuilder()
        // read integer
        takeWhile { it in digits }
            .forEach { sb.append(it.toChar()) }
        val int = sb.toString()
        require(int.length <= 1 || int[0] != '0') { "Leading zeros not allowed" }
        // read fraction
        val frac = if (tryExpect('.'.toInt())) {
            sb.clear()
            takeWhile { it in digits }
                .forEach { sb.append(it.toChar()) }
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

    private fun JsonSource.tryReadString(): Token.StringToken? =
        if (peek() == quote) {
            readString()
        } else {
            null
        }

    private fun JsonSource.readString(): Token.StringToken {
        expect(quote)
        val sb = StringBuilder()
        while (true) {
            val current = read()
            if (current == escape) {
                // If it's an escape character, we immediately append whatever the next character is
                // unless it's a `u`, and then we treat it as a unicode sequence.
                if (tryExpect(u)) { // unicode escape characters, like \u1234
                    val hex = readString(4).also { it.validateHex() }
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

    private fun JsonSource.skipWhitespace() {
        while (peek().isJsonSpace()) {
            read()
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