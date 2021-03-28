package com.github.nanodeath

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val file = args.first().let { File(it) }
    try {
        file.inputStream().use { br ->
            runBlocking {
                Json().parse(br).toList().joinToString().let { System.err.println(it) }
            }
        }
        exitProcess(0)
    } catch (e: IOException) {
        exitProcess(2)
    } catch (e: Exception) {
        exitProcess(1)
    }
}