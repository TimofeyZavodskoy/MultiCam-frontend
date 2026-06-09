package ru.hotdog.multicam.api.dto

import com.google.gson.annotations.SerializedName

// Описывает сохранённый на backend результат избранного.
data class SavedResultDto(
    @SerializedName("id")        val id: Long,
    @SerializedName("imageUrl")  val imageUrl: String? = null,
    @SerializedName("jsonData")  val jsonData: String? = null,
    @SerializedName("category")  val category: String? = null,
    @SerializedName("userId")    val userId: Long? = null,
    @SerializedName("createdAt") val createdAt: String? = null
)