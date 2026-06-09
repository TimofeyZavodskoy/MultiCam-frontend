package ru.hotdog.multicam.api.dto

import com.google.gson.annotations.SerializedName

// Описывает координаты найденного объекта на изображении.
data class BoundingBox(val x: Float, val y: Float, val width: Float, val height: Float)
// Описывает найденный объект и его рамку.
data class DetectedObj(val label: String, val bbox: ru.hotdog.multicam.api.dto.BoundingBox)

// Описывает ссылку на найденный товар в маркетплейсе.
data class SearchResult(
    @SerializedName("marketplace") val marketplace: String,
    @SerializedName("url")         val url: String,
    @SerializedName("icon")        val icon: String? = null
)
// Описывает полный ответ backend после анализа изображения.
data class OCRResponse(
    @SerializedName("tag")          val tag: String? = null,
    @SerializedName("result")       val result: String? = null,
    @SerializedName("content")      val content: String? = null,
    @SerializedName("description")  val description: String? = null,
    @SerializedName("solution")     val solution: String? = null,
    @SerializedName("reasoning")    val reasoning: String? = null,

    // Питание
    @SerializedName("mass")         val mass: Int? = null,
    @SerializedName("calories")     val calories: Int? = null,
    @SerializedName("proteins")     val proteins: Int? = null,
    @SerializedName("fats")         val fats: Int? = null,
    @SerializedName("carbs")        val carbs: Int? = null,

    @SerializedName("detectedObjs") val detections: List<ru.hotdog.multicam.api.dto.DetectedObj>? = null,
    @SerializedName("searchResults") val searchResults: List<ru.hotdog.multicam.api.dto.SearchResult>? = null

)