package ru.hotdog.multicam.api.dto

data class SaveRequest(
    val imageUrl: String? = null,
    val clientJson: ru.hotdog.multicam.api.dto.OCRResponse,
    val category: String
)