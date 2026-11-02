package com.xlite.downloader.auth

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class JwtInfo(
    val expiresAtEpochSeconds: Long?,
    val audience: String?,
)

object JwtParser {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalEncodingApi::class)
    fun parse(token: String): JwtInfo? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        val payload = runCatching {
            val bytes = Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(parts[1])
            json.parseToJsonElement(bytes.decodeToString()).jsonObject
        }.getOrNull() ?: return null

        val exp = payload["exp"]?.jsonPrimitive?.longOrNull
        val aud = when (val value = payload["aud"]) {
            is JsonPrimitive -> value.contentOrNull
            is JsonArray -> value.firstOrNull()?.jsonPrimitive?.contentOrNull
            else -> null
        }
        return JwtInfo(expiresAtEpochSeconds = exp, audience = aud)
    }
}
