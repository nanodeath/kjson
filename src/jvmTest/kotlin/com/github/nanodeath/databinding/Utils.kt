package com.github.nanodeath.databinding

import com.github.nanodeath.Token

fun bind(vararg token: Token) = DataBinding().bind(sequenceOf(*token).asIterable())