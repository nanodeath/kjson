package com.github.nanodeath

import java.util.*

internal class SlidingCharWindow(private val capacity: Int) {
    private val window = LinkedList<Char>()
    private var size = 0

    fun append(char: Char): SlidingCharWindow {
        window.addLast(char)
        if (!char.isLowSurrogate() && !char.isVariationSelector()){
            size++
        }
        reduceToSize(capacity)
        return this
    }

    fun string(): String =
        buildString {
            for (char in window) {
                append(char)
            }
        }

    private fun reduceToSize(targetSize: Int) {
        while (size > targetSize) {
            val first = window.removeFirst()
            if (first.isHighSurrogate()) {
                window.removeFirst() // low surrogate, could check here?
            }
            // Should probably skip the entire plane, which includes Variation Selectors Supplement
            // https://www.compart.com/en/unicode/plane/U+E0000
            window.removeFirstIf { it.isVariationSelector() }
            size--
        }
    }
}

internal fun SlidingCharWindow.append(codepoint: Int) = append(codepoint.toChar())

internal fun SlidingCharWindow.append(string: String) {
    for (idx in string.indices) {
        append(string[idx])
    }
}

/**
 * Removes the first element if it matched [predicate]. Does nothing for empty lists.
 * @return true if the list was modified.
 */
private fun <E> MutableList<E>.removeFirstIf(predicate: (E) -> Boolean): Boolean =
    if (isNotEmpty() && predicate(first())) {
        removeFirst()
        true
    } else {
        false
    }

private fun Char.isVariationSelector(): Boolean {
    // https://en.wikipedia.org/wiki/Variation_Selectors_(Unicode_block)
    return this in '\uFE00'..'\uFE0F'
}
