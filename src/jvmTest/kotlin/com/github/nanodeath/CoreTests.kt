package com.github.nanodeath

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CoreTests {
    @Test
    fun `large documents`() {
        val longText = "what if the document is large? if our internal buffer is insufficiently-sized, how to adapt?"
        val doc = """
            {"thought": "$longText"}
        """.trimIndent()
        val list = Json(bufferSize = 32).parse(doc).toList()
        assertThat(list).containsExactly(
            Token.StartObject,
            Token.Key(Token.StringToken("thought")),
            Token.StringToken(longText),
            Token.EndObject
        )
    }

    @Test
    fun `parse value parses the entire input`() {
        assertThatThrownBy {
            Json().parseValue("1a").toList()
        }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}