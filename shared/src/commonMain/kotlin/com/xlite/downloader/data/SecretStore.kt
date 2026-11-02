package com.xlite.downloader.data

interface SecretStore {
    suspend fun readApiKey(): String?
    suspend fun writeApiKey(apiKey: String)
    suspend fun clearApiKey()
}

class MemorySecretStore(initial: String? = null) : SecretStore {
    private var value: String? = initial

    override suspend fun readApiKey(): String? = value

    override suspend fun writeApiKey(apiKey: String) {
        value = apiKey
    }

    override suspend fun clearApiKey() {
        value = null
    }
}
