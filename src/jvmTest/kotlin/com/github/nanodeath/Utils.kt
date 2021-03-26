package com.github.nanodeath


fun valueToTokens(str: String) = Json().parseValue(str).toList()