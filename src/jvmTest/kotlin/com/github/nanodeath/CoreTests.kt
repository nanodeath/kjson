package com.github.nanodeath

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CoreTests {
    @Test
    fun `large documents`() {
        runBlocking {
            val longText =
                "what if the document is large? if our internal buffer is insufficiently-sized, how to adapt?"
                    .repeat(100)
            val doc = """
            {"thought": "$longText"}
        """.trimIndent()
            val list = Json().parse(doc).toList()
            assertThat(list).containsExactly(
                Token.StartObject,
                Token.Key(Token.StringToken("thought")),
                Token.StringToken(longText),
                Token.EndObject
            )
        }
    }

    @Test
    fun `parse value parses the entire input`() {
        assertThatThrownBy {
            runBlocking {
                Json().parseValue("1a").toList()
            }
        }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}