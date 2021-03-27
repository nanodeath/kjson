package com.github.nanodeath.databinding

import com.github.nanodeath.Token
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ArrayTests {
    @Test
    fun emptyArray() {
        assertThat(bind(Token.StartArray, Token.EndArray)).isEqualTo(emptyList<Any>())
    }

    @Test
    fun intArray() {
        assertThat(
            bind(
                Token.StartArray,
                Token.Number("1"),
                Token.Number("2"),
                Token.Number("3"),
                Token.EndArray
            )
        ).isEqualTo(listOf("1", "2", "3"))
    }

    @Test
    fun stringArray() {
        assertThat(
            bind(
                Token.StartArray,
                Token.StringToken("hello"),
                Token.StringToken("world"),
                Token.EndArray
            )
        ).isEqualTo(listOf("hello", "world"))
    }

    @Test
    fun literalArray() {
        assertThat(
            bind(
                Token.StartArray,
                Token.True,
                Token.False,
                Token.Null,
                Token.EndArray
            )
        ).isEqualTo(listOf(true, false, null))
    }

    @Test
    fun nestedArray() {
        assertThat(
            bind(
                Token.StartArray,
                Token.StartArray,
                Token.StringToken("hello"),
                Token.StringToken("world"),
                Token.EndArray,
                Token.EndArray
            )
        ).isEqualTo(listOf(listOf("hello", "world")))
    }

    @Test
    fun nestedObject() {
        assertThat(
            bind(
                Token.StartArray,
                Token.StartObject,
                Token.Key(Token.StringToken("key")),
                Token.StringToken("value"),
                Token.EndObject,
                Token.EndArray,
            )
        ).isEqualTo(listOf(mapOf("key" to "value")))
    }

    @Test
    fun unclosedArray() {
        assertThatThrownBy {
            bind(Token.StartArray)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Unclosed array")
    }
}
