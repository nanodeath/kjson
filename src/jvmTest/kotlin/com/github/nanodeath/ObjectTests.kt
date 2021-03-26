package com.github.nanodeath

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ObjectTests {
    @Test
    fun `empty object`() {
        assertThat(Json().parse("{}").toList()).containsExactly(
            Token.StartObject,
            Token.EndObject
        )
    }

    @Test
    fun `single-key object`() {
        assertThat(Json().parse("""{"foo": "bar"}""").toList()).containsExactly(
            Token.StartObject,
            Token.Key(Token.StringToken("foo")),
            Token.StringToken("bar"),
            Token.EndObject
        )
    }

    @Test
    fun `two-key object`() {
        assertThat(Json().parse("""{"foo": "bar", "cat": 1}""").toList()).containsExactly(
            Token.StartObject,
            Token.Key(Token.StringToken("foo")),
            Token.StringToken("bar"),
            Token.Key(Token.StringToken("cat")),
            Token.Number("1"),
            Token.EndObject
        )
    }

    @Test
    fun `nested object`() {
        assertThat(Json().parse("""{"foo": {"cat": "dog"}}""").toList()).containsExactly(
            Token.StartObject,
            Token.Key(Token.StringToken("foo")),
            Token.StartObject,
            Token.Key(Token.StringToken("cat")),
            Token.StringToken("dog"),
            Token.EndObject,
            Token.EndObject
        )
    }
}