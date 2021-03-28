package com.github.nanodeath

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking


fun parseToTokens(str: String): List<Token> = runBlocking { Json().parse(str).toList() }
fun valueToTokens(str: String): List<Token> = runBlocking { Json().parseValue(str).toList() }