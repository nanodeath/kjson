package com.github.nanodeath

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class StringTests {
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("tests")
    fun `tokens are expected`(name: String, value: String, expected: List<Token>) {
        assertThat(valueToTokens(value)).isEqualTo(expected)
    }

    @Test
    fun `incomplete unicode`() {
        assertThatThrownBy {
            valueToTokens("\"\\uD83C\\uDF1\"")
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage(""""DF1""[3] (") is not a valid hexadecimal character""")
    }

    companion object {
        @JvmStatic
        fun tests(): Stream<Arguments> = Stream.of(
            arguments("empty string", "\"\"", listOf(Token.StringToken(""))),
            arguments("short string", "\"foo\"", listOf(Token.StringToken("foo"))),
            arguments("embedded double-quote", """"foo\"bar"""", listOf(Token.StringToken("foo\"bar"))),
            arguments("embedded reverse solidus", """"foo\\bar"""", listOf(Token.StringToken("foo\\bar"))),
            arguments("glowing star emoji", "\"🌟\"", listOf(Token.StringToken("🌟"))),
            arguments("glowing star emoji (unicode)", "\"\\uD83C\\uDF1F\"", listOf(Token.StringToken("🌟"))),
        )
    }
}