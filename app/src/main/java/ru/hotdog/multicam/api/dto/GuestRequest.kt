package ru.hotdog.multicam.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class GuestRequest(
    val uuid: String
)
