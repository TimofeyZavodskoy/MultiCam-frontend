package ru.hotdog.multicam.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String? = null,
    val password: String? = null,
)