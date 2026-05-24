package ru.hotdog.multicam_client.api.dto

import com.google.gson.annotations.SerializedName


data class BoundingBox(val x: Float, val y: Float, val width: Float, val height: Float)
data class DetectedObj(val label: String, val bbox: BoundingBox)

data class OCRResponse(
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("result") val result: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("solution") val solution: String? = null,
    @SerializedName("reasoning") val reasoning: String? = null,

    @SerializedName("calories") val calories: Int? = null,
    @SerializedName("proteins") val proteins: Int? = null,
    @SerializedName("fats") val fats: Int? = null,
    @SerializedName("carbs") val carbs: Int? = null,

    @SerializedName("detectedObj") val detections: List<DetectedObj>? = null
)