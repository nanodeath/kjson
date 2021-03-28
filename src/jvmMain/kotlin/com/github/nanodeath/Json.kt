@file:Suppress("SameParameterValue")

package com.github.nanodeath

import com.github.nanodeath.async.AsyncByteJsonSource
import com.github.nanodeath.async.AsyncInputStream
import com.github.nanodeath.async.AsyncJsonSource
import kotlinx.coroutines.flow.*
import java.io.InputStream
import java.nio.channels.AsynchronousByteChannel

class Json {
    fun parse(string: String): Flow<Token> = parse(string.byteInputStream())
    fun parse(inputStream: InputStream): Flow<Token> = parse(AsyncInputStream(inputStream))
    fun parse(asynchronousByteChannel: AsynchronousByteChannel): Flow<Token> =
        flow {
            AsyncByteJsonSource(asynchronousByteChannel).use { source ->
                emitAll(SourceContext(source).readStructuredType())
                require(source.peek() == -1) { "Input contains multiple values, expected one" }
            }
        }

    internal fun parseValue(string: String): Flow<Token> =
        flow {
            AsyncByteJsonSource(AsyncInputStream(string.byteInputStream())).use { source ->
                emitAll(SourceContext(source).readAny())
                require(source.peek() == -1) { "Input contains multiple values, expected one" }
            }
        }

    internal class SourceContext(private val source: AsyncJsonSource) {
        private val slidingWindow = SlidingCharWindow(10)

        suspend fun readStructuredType(): Flow<Token> =
            when (val next = source.peek()) {
                Structural.BeginObject.int -> readMap()
                Structural.BeginArray.int -> readArray()
                else -> printError("Unexpected value ${next.codepointToString()}")
            }

        private suspend fun readMap(): Flow<Token> =
            flow {
                expect(Structural.BeginObject)
                emit(Token.StartObject)
                if (tryExpect(Structural.EndObject)) {
                    emit(Token.EndObject)
                    return@flow
                }
                while (true) {
                    val readString = readString()
                    emit(Token.Key(readString))
                    expect(Structural.NameSeparator)
                    val value = readAny()
                    emitAll(value)
                    if (tryExpect(Structural.EndObject)) {
                        emit(Token.EndObject)
                        return@flow
                    }
                    expect(Structural.ValueSeparator)
                }
            }

        private fun readArray(): Flow<Token> =
            flow {
                expect(Structural.BeginArray)
                emit(Token.StartArray)
                if (tryExpect(Structural.EndArray)) {
                    emit(Token.EndArray)
                    return@flow
                }
                while (true) {
                    emitAll(readAny())
                    if (tryExpect(Structural.EndArray)) {
                        emit(Token.EndArray)
                        return@flow
                    }
                    expect(Structural.ValueSeparator)
                }
            }

        private suspend fun tryReadMap(): Flow<Token>? =
            if (source.peek() == Structural.BeginObject.int) {
                readMap()
            } else null

        private suspend fun tryReadArray(): Flow<Token>? =
            if (source.peek() == Structural.BeginArray.int) {
                readArray()
            } else null

        private suspend fun expect(char: Structural) {
            skipWhitespace()
            expect(char.int)
            skipWhitespace()
        }

        private suspend fun expect(character: Int): Int {
            when (val int = source.read().andLogIt()) {
                character -> return int
                else -> printError(
                    "Unexpected character ${int.codepointToString()}, expected ${character.codepointToString()}"
                )
            }
        }

        private suspend fun tryExpect(c: Structural): Boolean {
            skipWhitespace()

            return if (source.peek() == c.int) {
                source.read().andLogIt()
                skipWhitespace()
                true
            } else {
                false
            }
        }

        private suspend fun tryExpect(c: Int): Boolean =
            if (source.peek() == c) {
                source.read().andLogIt()
                true
            } else {
                false
            }

        private suspend fun printError(message: String): Nothing {
            val context = slidingWindow.string()
            val after = source.peekString(20)
            System.err.println(context + after)
            val sb = StringBuilder()
            repeat(context.length - 1) {
                sb.append(' ')
            }
            sb.append('^')
            System.err.println(sb)
            val ex = IllegalArgumentException(message)
            ex.stackTrace = ex.stackTrace.drop(1).toTypedArray()
            throw ex
        }

        suspend fun readAny(): Flow<Token> =
            tryReadAny() ?: printError(message = "Failed to read any")

        private suspend fun tryReadAny(): Flow<Token>? =
            tryReadString()?.let { flowOf(it) }
                ?: tryReadNumber()?.let { flowOf(it) }
                ?: tryReadMap()
                ?: tryReadArray()
                ?: tryReadLiteral()

        private suspend fun tryReadLiteral(): Flow<Token>? {
            val next5 = source.peekString(5)
            return when {
                next5.startsWith("null") -> flowOf(Token.Null).also { source.skipCharacters(4) }
                next5 == "false" -> flowOf(Token.False).also { source.skipCharacters(5) }
                next5.startsWith("true") -> flowOf(Token.True).also { source.skipCharacters(4) }
                else -> null
            }
        }

        private suspend fun tryReadNumber(): Token.Number? {
            val peek = source.peek()
            return when (peek) {
                in digits -> readNumber()
                '-'.toInt() -> readNumber()
                else -> null
            }
        }

        private suspend fun readNumber(): Token.Number {
            val negative = tryExpect('-'.toInt())
            val sb = StringBuilder()
            // read integer
            source.takeWhile { it in digits }
                .collect { sb.append(it.toChar()) }
            val int = sb.toString().andLogIt()
            require(int.length <= 1 || int[0] != '0') { "Leading zeros not allowed" }
            // read fraction
            val frac = if (tryExpect('.'.toInt())) {
                sb.clear()
                source.takeWhile { it in digits }
                    .collect { sb.append(it.toChar()) }
                sb.toString().andLogIt()
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

        private suspend fun tryReadString(): Token.StringToken? =
            if (source.peek() == quote) {
                readString()
            } else {
                null
            }

        private suspend fun readString(): Token.StringToken {
            expect(quote)
            val sb = StringBuilder()
            while (true) {
                val current = source.read()
                if (current == escape) {
                    // If it's an escape character, we immediately append whatever the next character is
                    // unless it's a `u`, and then we treat it as a unicode sequence.
                    if (tryExpect(u)) { // unicode escape characters, like \u1234
                        val hex = source.readString(4).also { it.validateHex() }
                        sb.appendCodePoint(hex.toInt(16))
                    } else {
                        sb.appendCodePoint(source.read())
                    }
                } else if (current == quote) {
                    break
                } else {
                    sb.appendCodePoint(current)
                }
            }
            return Token.StringToken(sb.toString().andLogIt())
        }

        private suspend fun skipWhitespace() {
            while (source.peek().isJsonSpace()) {
                source.read().andLogIt()
            }
        }

        private fun Int.isJsonSpace() =
            this == spaceInt || this == horizontalTabInt || this == lineFeedInt || this == carriageReturnInt


        private fun Int.andLogIt() = also { slidingWindow.append(it) }
        private fun String.andLogIt() = also { slidingWindow.append(it) }
    }

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

private fun Int.codepointToString() = if (this == -1) "EOF" else String(Character.toChars(this))