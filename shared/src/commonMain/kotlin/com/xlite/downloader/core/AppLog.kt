package com.xlite.downloader.core

import com.xlite.downloader.util.Redactor
import kotlin.time.Clock

object AppLog {
    var sink: ((level: String, message: String) -> Unit)? = null

    fun info(tag: String, message: String) = write("I", tag, message, null)

    fun warn(tag: String, message: String, error: Throwable? = null) = write("W", tag, message, error)

    fun error(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    fun debug(tag: String, message: String) = write("D", tag, message, null)

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val detail = if (error == null) message else "$message: ${error.javaClass.simpleName}: ${error.message}"
        val line = "${Clock.System.now().toEpochMilliseconds()} $level/$tag ${Redactor.redact(detail)}"
        val output = sink
        if (output != null) {
            runCatching { output(level, line) }.onFailure { println(line) }
        } else {
            println(line)
        }
    }
}
