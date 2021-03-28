package com.github.nanodeath.databinding

import com.github.nanodeath.Json
import com.github.nanodeath.Token
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

fun parseAndBind(json: String) = runBlocking {  Json().parse(json).toList().toTypedArray().let { bind(*it) } }
fun bind(vararg token: Token) = DataBinding().bind(sequenceOf(*token).asIterable())