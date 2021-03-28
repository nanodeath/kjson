package com.github.nanodeath

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ErrorMessaging {
    companion object {
        lateinit var mainOut: PrintStream
        lateinit var mainErr: PrintStream

        @BeforeAll
        @JvmStatic
        fun saveOut() {
            mainOut = System.out
            mainErr = System.err
        }
    }

    @AfterEach
    fun restoreOut() {
        System.setOut(mainOut)
        System.setErr(mainErr)
    }

    private fun captureStreams(cb: () -> Unit): Pair<String, String> {
        val out = ByteArrayOutputStream()
        val outStream = PrintStream(out).also { System.setOut(it) }
        val err = ByteArrayOutputStream()
        val errStream = PrintStream(err).also { System.setErr(it) }
        cb()
        outStream.close()
        errStream.flush()
        return out.toString(Charsets.UTF_8) to err.toString(Charsets.UTF_8)
    }

    @Test
    fun `unterminated string`() {
        val (out, err) = captureStreams {
            assertThatThrownBy {
                valueToTokens("\"Hello World")
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Unexpected EOF, expected 1 of 2 options: \", basically anything")
        }
        assertThat(out).isEmpty()
        assertThat(err.trim()).isEqualToNormalizingNewlines("""
            "Hello World
                        ^
        """.trimIndent())
    }

    @Test
    fun `unclosed array`() {
        val (out, err) = captureStreams {
            assertThatThrownBy {
                valueToTokens("""["foo"""")
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Unexpected EOF, expected 1 of 2 options: , (comma), ] (right square bracket)")
        }
        assertThat(out).isEmpty()
        assertThat(err.trim()).isEqualToNormalizingNewlines("""
            ["foo"
                  ^
        """.trimIndent())
    }

    @Test
    fun `unclosed object - EOF`() {
        val (out, err) = captureStreams {
            assertThatThrownBy {
                valueToTokens("""{"foo": "bar"""")
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Unexpected EOF, expected 1 of 2 options: , (comma), } (right curly bracket)")
        }
        assertThat(out).isEmpty()
        assertThat(err.trim()).isEqualToNormalizingNewlines("""
            {"foo": "bar"
                         ^
        """.trimIndent())
    }

    @Test
    fun `invalid number in array`() {
        val (out, err) = captureStreams {
            assertThatThrownBy {
                parseToTokens("[1a1]")
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Unexpected a, expected: ,")
        }
        assertThat(out).isEmpty()
        assertThat(err.trim()).isEqualToNormalizingNewlines("""
            [1a1]
              ^
        """.trimIndent())
    }

    @Test
    fun `leading zero number`() {
        val (out, err) = captureStreams {
            assertThatThrownBy {
                valueToTokens("[1,012345678]")
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Unexpected 0, expected: non-zero digit")
        }
        assertThat(out).isEmpty()
        assertThat(err.trim()).isEqualToNormalizingNewlines("""
            [1,012345678]
               ^
        """.trimIndent())
    }

    @Test
    fun `unterminated fractional number`() {
        val (out, err) = captureStreams {
            assertThatThrownBy {
                valueToTokens("1.")
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Unterminated fractional number, expected digit")
        }
        assertThat(out).isEmpty()
        assertThat(err.trim()).isEqualToNormalizingNewlines("""
            1.
              ^
        """.trimIndent())
    }
}