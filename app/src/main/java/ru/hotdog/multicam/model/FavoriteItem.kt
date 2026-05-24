package ru.hotdog.multicam.model

enum class FavoriteCategory(val displayName: String, val emoji: String) {
    FOOD("Еда", "🍽"),
    MATH("Математика", "🧮"),
    PHYSICS("Физика", "⚛️"),
    CHEMISTRY("Химия", "🧪"),
    TEXT("Текст", "📝"),
    OBJECT_SEARCH("Поиск объектов", "🔍"),
    IMAGES("Изображения", "📸")
}

data class FavoriteItem(
    val id: String,
    val timestamp: Long,
    val category: ru.hotdog.multicam.model.FavoriteCategory,
    val title: String,
    val resultText: String? = null,
    val calories: Int? = null,
    val proteins: Int? = null,
    val fats: Int? = null,
    val carbs: Int? = null,
    val backendId: Long? = null
)