package com.xlite.downloader.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class TaskTraffic(val originBytes: Long, val p2pBytes: Long, val p2sBytes: Long) {
    val total: Double get() = originBytes.toDouble() + p2pBytes.toDouble() + p2sBytes.toDouble()
    fun fraction(bytes: Long): Float = if (total > 0) (bytes.toDouble() / total).toFloat() else 0f

    companion object {
        fun parse(raw: String): TaskTraffic? = runCatching {
            val root = Json.parseToJsonElement(raw).jsonObject
            val data = root["traffic"]?.let {
                if (it is JsonPrimitive) Json.parseToJsonElement(it.content).jsonObject else it.jsonObject
            } ?: root
            fun count(snake: String, camel: String): Long {
                val value = (data[snake] ?: data[camel])?.jsonPrimitive?.longOrNull
                require(value != null && value >= 0)
                return value
            }
            TaskTraffic(count("origin_size", "originSize"), count("p2p_size", "p2pSize"), count("p2s_size", "p2sSize"))
        }.getOrNull()
    }
}
