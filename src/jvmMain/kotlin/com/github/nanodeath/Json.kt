package com.github.nanodeath

import com.github.nanodeath.StateMachines.Collection.offer
import java.io.InputStream
import java.io.Reader
import java.nio.Buffer
import java.nio.CharBuffer

interface StateMachine {
    suspend fun SequenceScope<Token>.offer(char: Char) : StateMachine
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
            var expectingNextElementOfArray = false
            while (true) {
                val bytesRead = reader.read(buffer)
                if (bytesRead <= 0) break
                buffer.flip()
                while (buffer.remaining() > 0) {
                    buffer.mark()
                    if (expectingNextElementOfArray) {
                        val char = buffer.get()
                        when {
                            char.isWhitespace() -> {}
                            char == ',' || char == ']' -> expectingNextElementOfArray = false
                            else -> throw IllegalStateException("Unexpected char $char at pos ${buffer.previousPosition()}")
                        }
                    } else {
                        when (buffer.get()) {
                            '[' -> yield(Token.StartArray)
                            in '0'..'9' -> {
                                buffer.reset()
                                yield(readNumber(buffer))
                                expectingNextElementOfArray = true
                            }
                            '"' -> {
                                yield(readString(buffer))
                            }
                            ']' -> yield(Token.EndArray)
                            '{' -> {
                                yield(Token.StartObject)
                            }
                            '}' -> yield(Token.EndObject)
                        }
                    }
                }
            }
        }
    }

    private fun readNumber(buffer: CharBuffer): Token {
        val originalLimit = buffer.limit()
        buffer.mark()
        while (true) {
            val current = buffer.get()
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

    private fun readString(buffer: CharBuffer): Token {
        val originalLimit = buffer.limit()
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

fun main() {
    Json().parse("[42, 24, [18]]".reader()).joinToString(separator = "\n", transform = { "$it  (${it.javaClass.simpleName})" }).let { println(it) }
    Json().parse("""{"foo": "bar"}""".reader()).joinToString(separator = "\n", transform = { "$it  (${it.javaClass.simpleName})" }).let { println(it) }
}

sealed class Token(val value: String) {
    object StartArray : Token("[")
    object EndArray : Token("]")
    object StartObject : Token("{")
    object EndObject : Token("}")
    class Number(value: String) : Token(value)
    class StringToken(value: String) : Token(value)

    override fun toString() = value
}