package com.github.nanodeath

/**
 * Validates that every character in this string conforms to [0-9a-ZA-Z].
 * @throws IllegalArgumentException if invalid
 */
internal fun String.validateHex() {
    for (i in indices) {
        val c = this[i]
        if (c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z') {
            // fine
        } else {
            throw IllegalArgumentException("\"$this\"[$i] ($c) is not a valid hexadecimal character")
        }
    }
}

