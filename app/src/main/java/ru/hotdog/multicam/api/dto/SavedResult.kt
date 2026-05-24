package ru.hotdog.multicam.api.dto

import com.google.gson.annotations.SerializedName

/**
 * Зеркало SaveResultEntity с бекенда.
 * Используется при получении лайков (GET /api/save/likes/all)
 * и при сохранении (POST /api/save/like) для получения id.
 */
data class SavedResultDto(
    @SerializedName("id")        val id: Long,
    @SerializedName("imageUrl")  val imageUrl: String? = null,
    @SerializedName("jsonData")  val jsonData: String? = null,
    @SerializedName("category")  val category: String? = null,
    @SerializedName("userId")    val userId: Long? = null,
    @SerializedName("createdAt") val createdAt: String? = null
)