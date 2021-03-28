package com.github.nanodeath

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ArrayTests {
    @Test
    fun `empty array`() {
        runBlocking {
            assertThat(parseToTokens("[]")).containsExactly(
                Token.StartArray,
                Token.EndArray
            )
        }
    }

    @Test
    fun `number array`() {
        runBlocking {
            assertThat(parseToTokens("[1, 2, 3]")).containsExactly(
                Token.StartArray,
                Token.Number("1"),
                Token.Number("2"),
                Token.Number("3"),
                Token.EndArray
            )
        }
    }

    @Test
    fun `string array`() {
        runBlocking {
            assertThat(parseToTokens("""["foo", "bar"]""")).containsExactly(
                Token.StartArray,
                Token.StringToken("foo"),
                Token.StringToken("bar"),
                Token.EndArray
            )
        }
    }

    @Test
    fun `nested array`() {
        runBlocking {
            assertThat(parseToTokens("""[[1, 2]]""")).containsExactly(
                Token.StartArray,
                Token.StartArray,
                Token.Number("1"),
                Token.Number("2"),
                Token.EndArray,
                Token.EndArray
            )
        }
    }

    @Test
    fun `double-nested array`() {
        runBlocking {
            assertThat(parseToTokens("""[[[1, 2]]]""")).containsExactly(
                Token.StartArray,
                Token.StartArray,
                Token.StartArray,
                Token.Number("1"),
                Token.Number("2"),
                Token.EndArray,
                Token.EndArray,
                Token.EndArray
            )
        }
    }
}
