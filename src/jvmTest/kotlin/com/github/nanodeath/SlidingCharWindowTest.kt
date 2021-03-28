package com.github.nanodeath

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.streams.asSequence

internal class SlidingCharWindowTest {
    private val window = SlidingCharWindow(5)

    @Test
    fun underCapacity() {
        window.append('a')
        window.append('b')
        window.append('c')
        assertThat(window.string()).isEqualTo("abc")
    }

    @Test
    fun atCapacity() {
        window.append('a')
        window.append('b')
        window.append('c')
        window.append('d')
        window.append('e')
        assertThat(window.string()).isEqualTo("abcde")
    }

    @Test
    fun overCapacity() {
        window.append('a')
        window.append('b')
        window.append('c')
        window.append('d')
        window.append('e')
        window.append('\uD83C').append('\uDF0E')
        window.append('g')
        assertThat(window.string()).isEqualTo("cde\uD83C\uDF0Eg")
    }

    @Test
    fun multipleEmoji() {
        "\uD83C\uDD70️\uD83C\uDD71️\uD83D\uDD24\uD83D\uDD25\u2728\u2714".chars().asSequence()
            .forEach { window.append(it.toChar()) }
        assertThat(window.string()).isEqualTo("\uD83C\uDD71️\uD83D\uDD24\uD83D\uDD25\u2728\u2714")
    }
}