package com.example.multicam.api.dto

import com.google.gson.annotations.SerializedName

data class AuthResponse(
    val token: String? = null,
    val message: String? = null,
    val error: String? = null
)