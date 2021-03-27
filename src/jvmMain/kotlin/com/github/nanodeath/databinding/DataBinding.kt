package com.github.nanodeath.databinding

import com.github.nanodeath.Token

class DataBinding {
    fun bind(tokens: Iterable<Token>): Any? {
        val iterator = tokens.iterator()
        if (!iterator.hasNext()) return null
        return when (iterator.next()) {
            Token.StartArray -> readArray(iterator)
            Token.StartObject -> readObject(iterator)
            else -> throw IllegalArgumentException()
        }
    }

    private fun readArray(iterator: Iterator<Token>): List<Any?> {
        val list = mutableListOf<Any?>()
        for (token in iterator) {
            if (token == Token.EndArray) {
                return list
            } else {
                list.add(readValue(token, iterator))
            }
        }
        throw IllegalArgumentException("Unclosed array")
    }

    private fun readObject(iterator: Iterator<Token>): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (token in iterator) {
            when (token) {
                is Token.Key ->
                    map[token.value] = readValue(iterator.next(), iterator)
                Token.EndObject ->
                    return map
                else ->
                    throw IllegalStateException()
            }.let { }
        }
        throw IllegalArgumentException("Unclosed object")
    }

    private fun readValue(token: Token, iterator: Iterator<Token>): Any? =
        when (token) {
            Token.StartArray ->
                readArray(iterator)
            Token.StartObject ->
                readObject(iterator)
            Token.Null ->
                null
            Token.False ->
                false
            Token.True ->
                true
            is Token.Number ->
                token.value
            is Token.StringToken ->
                token.value
            else ->
                throw IllegalStateException()
        }
}