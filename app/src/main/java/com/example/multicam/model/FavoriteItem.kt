package com.example.multicam.model

enum class FavoriteCategory(val displayName: String, val emoji: String) {
    FOOD("Еда", "🍽"),
    MATH("Математика", "🧮"),
    TEXT("Текст", "📝"),
    OBJECT_SEARCH("Поиск объектов", "🔍"),
    IMAGES("Изображения", "📸")
}

data class FavoriteItem(
    val id: String,
    val timestamp: Long,
    val category: FavoriteCategory,
    val title: String,
    val resultText: String? = null,
    val calories: Int? = null,
    val proteins: Int? = null,
    val fats: Int? = null,
    val carbs: Int? = null
)
