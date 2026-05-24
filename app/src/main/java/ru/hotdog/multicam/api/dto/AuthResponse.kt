package ru.hotdog.multicam.api.dto

data class AuthResponse(
    val token: String? = null,
    val message: String? = null,
    val error: String? = null
)