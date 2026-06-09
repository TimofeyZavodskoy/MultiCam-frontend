package ru.hotdog.multicam.api.dto

// Передаёт данные для регистрации или апгрейда аккаунта.
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)
