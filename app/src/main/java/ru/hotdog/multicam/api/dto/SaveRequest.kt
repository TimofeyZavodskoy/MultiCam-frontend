package ru.hotdog.multicam.api.dto

// Передаёт данные результата для сохранения в избранное.
data class SaveRequest(
    val imageUrl: String? = null,
    val clientJson: ru.hotdog.multicam.api.dto.OCRResponse,
    val category: String
)