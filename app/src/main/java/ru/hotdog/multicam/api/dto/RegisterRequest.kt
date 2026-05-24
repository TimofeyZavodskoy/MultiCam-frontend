package ru.hotdog.multicam.api.dto

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)
