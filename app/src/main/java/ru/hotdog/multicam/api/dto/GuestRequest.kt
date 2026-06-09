package ru.hotdog.multicam.api.dto

import kotlinx.serialization.Serializable

// Передаёт UUID устройства для гостевой регистрации.
@Serializable
data class GuestRequest(
    val uuid: String
)
