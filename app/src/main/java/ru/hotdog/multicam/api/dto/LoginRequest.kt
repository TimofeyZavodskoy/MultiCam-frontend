package ru.hotdog.multicam.api.dto

import kotlinx.serialization.Serializable

// Передаёт email и пароль для входа.
@Serializable
data class LoginRequest(
    val email: String? = null,
    val password: String? = null,
)