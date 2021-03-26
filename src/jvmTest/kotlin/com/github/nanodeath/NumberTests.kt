package com.github.nanodeath

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class NumberTests {
    private fun parse(str: String) = Json().parseValue(str).toList()

    @Test
    fun integer() {
        val parse = parse("1")
        assertThat(parse).containsExactly(
            Token.Number("1")
        )
    }

    @Test
    fun `negative number`() {
        assertThat(parse("-1")).containsExactly(
            Token.Number("-1")
        )
    }

    @Test
    fun `fraction`() {
        assertThat(parse("0.1")).containsExactly(
            Token.Number("0.1")
        )
    }

    @Test
    fun `leading zeros not allowed`() {
        assertThatThrownBy {
            parse("01")
        }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `number followed by letter not allowed`() {
        assertThatThrownBy {
            Json().parse("[1a]").toList()
        }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}