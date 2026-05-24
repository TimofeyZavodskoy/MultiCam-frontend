package ru.hotdog.multicam.api.dto

import com.google.gson.annotations.SerializedName

data class TokenPair(
    @SerializedName("accessToken")  val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String
)
