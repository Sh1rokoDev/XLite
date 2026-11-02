package com.xlite.downloader.domain.model

sealed interface AuthState {
    data object NotConfigured : AuthState
    data object Validating : AuthState
    data class Ready(val appId: String, val tokenExpiresAtEpochSeconds: Long) : AuthState
    data class Expired(val message: String) : AuthState
    data class Error(val message: String) : AuthState
}
