package com.example.multicam.api.dto

data class SaveRequest(
    val imageUrl: String? = null,
    val clientJson: OCRResponse,
    val category: String
)