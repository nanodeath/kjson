package com.github.nanodeath

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Tests {
    @Test
    fun `number array`() {
        assertThat(Json().parse("[1, 2, 3]".reader()).toList()).containsExactly(
            Token.StartArray,
            Token.Number("1"),
            Token.Number("2"),
            Token.Number("3"),
            Token.EndArray
        )
    }

    @Test
    fun `string array`() {
        assertThat(Json().parse("""["foo", "bar"]""".reader()).toList()).containsExactly(
            Token.StartArray,
            Token.StringToken("foo"),
            Token.StringToken("bar"),
            Token.EndArray
        )
    }

    @Test
    fun `nested array`() {
        assertThat(Json().parse("""[[[1, 2]]]""".reader()).toList()).containsExactly(
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