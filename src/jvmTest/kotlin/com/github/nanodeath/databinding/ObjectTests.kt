package com.github.nanodeath.databinding

import com.github.nanodeath.Token
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ObjectTests {
    @Test
    fun emptyObject() {
        assertThat(bind(Token.StartObject, Token.EndObject)).isEqualTo(emptyMap<Any, Any>())
    }

    @Test
    fun intObject() {
        assertThat(
            bind(
                Token.StartObject,
                Token.Key(Token.StringToken("counter")),
                Token.Number("1"),
                Token.EndObject
            )
        ).isEqualTo(mapOf("counter" to "1"))
    }

    @Test
    fun stringObject() {
        assertThat(
            bind(
                Token.StartObject,
                Token.Key(Token.StringToken("hello")),
                Token.StringToken("world"),
                Token.EndObject
            )
        ).isEqualTo(mapOf("hello" to "world"))
    }

    @Test
    fun literalObject() {
        assertThat(
            bind(
                Token.StartObject,
                Token.Key(Token.StringToken("true")),
                Token.True,
                Token.Key(Token.StringToken("false")),
                Token.False,
                Token.Key(Token.StringToken("null")),
                Token.Null,
                Token.EndObject
            )
        ).isEqualTo(mapOf("true" to true, "false" to false, "null" to null))
    }

    @Test
    fun nestedObject() {
        assertThat(
            bind(
                Token.StartObject,
                Token.Key(Token.StringToken("nested")),
                Token.StartObject,
                Token.Key(Token.StringToken("key")),
                Token.StringToken("value"),
                Token.EndObject,
                Token.EndObject
            )
        ).isEqualTo(mapOf("nested" to mapOf("key" to "value")))
    }

    @Test
    fun nestedArray() {
        assertThat(
            bind(
                Token.StartObject,
                Token.Key(Token.StringToken("nested")),
                Token.StartArray,
                Token.StringToken("key"),
                Token.StringToken("value"),
                Token.EndArray,
                Token.EndObject
            )
        ).isEqualTo(mapOf("nested" to listOf("key", "value")))
    }

    @Test
    fun unclosedObject() {
        assertThatThrownBy {
            bind(Token.StartObject)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Unclosed object")
    }
}
