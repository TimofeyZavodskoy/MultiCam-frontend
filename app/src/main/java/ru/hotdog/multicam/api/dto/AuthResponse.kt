package ru.hotdog.multicam.api.dto

// Описывает простой ответ авторизации с токеном, сообщением или ошибкой.
data class AuthResponse(
    val token: String? = null,
    val message: String? = null,
    val error: String? = null
)