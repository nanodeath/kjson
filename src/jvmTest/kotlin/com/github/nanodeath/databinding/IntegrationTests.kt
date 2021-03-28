package com.github.nanodeath.databinding

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class IntegrationTests {
    @Test
    fun emptyObject() {
        assertThat(parseAndBind("{}")).isEqualTo(emptyMap<Any, Any>())
    }

    @Test
    fun intObject() {
        assertThat(
            parseAndBind("{\"counter\":1}")
        ).isEqualTo(mapOf("counter" to "1"))
    }

    @Test
    fun stringObject() {
        assertThat(
            parseAndBind("{\"hello\":\"world\"}")
        ).isEqualTo(mapOf("hello" to "world"))
    }

    @Test
    fun literalObject() {
        assertThat(
            parseAndBind("""{"true": true, "false": false, "null": null}""")
        ).isEqualTo(mapOf("true" to true, "false" to false, "null" to null))
    }

    @Test
    fun nestedObject() {
        assertThat(
            parseAndBind("""{"nested": {"key": "value"}}""")
        ).isEqualTo(mapOf("nested" to mapOf("key" to "value")))
    }

    @Test
    fun nestedArray() {
        assertThat(
            parseAndBind("""{"nested": ["key", "value"]}""")
        ).isEqualTo(mapOf("nested" to listOf("key", "value")))
    }

    @Test
    fun unclosedObject() {
        assertThatThrownBy {
            parseAndBind("{")
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Unexpected character EOF")
    }
}