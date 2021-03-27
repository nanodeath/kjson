package com.github.nanodeath

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class LiteralTests {
    @ParameterizedTest
    @MethodSource("tests")
    fun `literals generated expected tokens`(literal: String, expected: List<Token>) {
        runBlocking {
            assertThat(Json().parseValue(literal).toList()).containsExactlyElementsOf(expected)
        }
    }

    companion object {
        @JvmStatic
        fun tests() = Stream.of(
            arguments("null", listOf(Token.Null)),
            arguments("false", listOf(Token.False)),
            arguments("true", listOf(Token.True)),
        )
    }
}