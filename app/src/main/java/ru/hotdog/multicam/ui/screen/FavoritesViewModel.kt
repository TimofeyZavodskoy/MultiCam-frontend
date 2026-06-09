package ru.hotdog.multicam.ui.screen

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.hotdog.multicam.api.RetrofitClient
import ru.hotdog.multicam.api.dto.OCRResponse
import ru.hotdog.multicam.api.dto.SaveRequest
import ru.hotdog.multicam.model.FavoriteCategory
import ru.hotdog.multicam.model.FavoriteItem
import ru.hotdog.multicam.model.buildFavoriteTitleFromText
import ru.hotdog.multicam.model.normalizeFavoriteCategory

private const val TAG = "FavoritesVM"

// Управляет локальным и серверным избранным для UI.
class FavoritesViewModel(app: Application) : AndroidViewModel(app) {

    // SharedPreferences держит кэш избранного локально, чтобы данные не пропадали между запусками.
    private val prefs = app.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    // Gson нужен для сериализации списка FavoriteItem в JSON и обратно.
    private val gson  = Gson()

    // Текущее состояние избранного; UI подписан на это поле напрямую.
    var favorites by mutableStateOf<List<FavoriteItem>>(emptyList())
        private set

    // Показывает, идёт ли синхронизация избранного с backend.
    var isSyncing by mutableStateOf(false)
        private set

    init {
        // Сначала поднимаем локальный кэш, а уже потом при необходимости синкаем сервер.
        loadLocal()
        if (!RetrofitClient.authToken.isNullOrBlank()) {
            syncFromBackend()
        }
    }

    // Загружает избранное из локального JSON-кэша и нормализует записи.
    private fun loadLocal() {
        // Если ключа нет, значит локального кэша ещё не существует.
        val json = prefs.getString("items", null) ?: return
        // TypeToken нужен из-за стирания generic-типов в JVM.
        val type = object : TypeToken<List<FavoriteItem>>() {}.type
        // Старые записи поднимаем и сразу нормализуем категорию + заголовок.
        favorites = (gson.fromJson<List<FavoriteItem>>(json, type) ?: emptyList()).map { item ->
            item.copy(
                category = normalizeFavoriteCategory(item.category, item.resultText),
                title = buildFavoriteTitleFromText(item.resultText, item.title)
            )
        }
        // Перезаписываем кэш уже очищенной версией, чтобы дальше не тащить старые ошибки.
        persist()
    }

    // Сохраняет текущий список избранного в SharedPreferences.
    private fun persist() {
        // Храним весь список одним JSON-массивом, потому что это проще и надёжнее для мелкого кэша.
        prefs.edit().putString("items", gson.toJson(favorites)).apply()
    }

    // ── Публичный API ─────────────────────────────────────────────────────────

    // Добавляет элемент в избранное локально и синхронизирует его с backend.
    fun add(item: FavoriteItem, rawResponse: OCRResponse?, category: FavoriteCategory) {
        // Дубликаты по id не нужны: одна и та же карточка должна лайкаться только один раз.
        if (favorites.any { it.id == item.id }) return

        // Сначала показываем результат в UI, чтобы лайк ощущался мгновенно.
        favorites = listOf(item) + favorites
        persist()

        // Если сырого ответа нет, синк на сервер невозможен, но локальный лайк всё равно остаётся.
        rawResponse ?: return
        viewModelScope.launch {
            try {
                // На сервер отправляем raw OCR payload вместе с категорией.
                val resp = RetrofitClient.api.saveLike(
                    SaveRequest(clientJson = rawResponse, category = category.name)
                )
                if (resp.isSuccessful) {
                    val backendId = resp.body()?.id
                    if (backendId != null) {
                        // Сохраняем backendId — он нужен, чтобы потом сделать DELETE уже по серверному id.
                        favorites = favorites.map {
                            if (it.id == item.id) it.copy(backendId = backendId) else it
                        }
                        persist()
                        Log.d(TAG, "Like synced → backendId=$backendId")
                    }
                } else {
                    Log.w(TAG, "saveLike HTTP ${resp.code()}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "saveLike failed (non-critical): ${e.message}")
            }
        }
    }

    // Удаляет элемент из избранного локально и на backend при наличии server id.
    fun remove(id: String) {
        // Сначала убираем запись из UI и локального кэша, чтобы состояние обновилось без ожидания сети.
        val item = favorites.find { it.id == id }
        favorites = favorites.filter { it.id != id }
        persist()

        // Если серверного id нет, значит удалять на бэкенде нечего.
        val backendId = item?.backendId ?: return
        viewModelScope.launch {
            try {
                // Серверное удаление выполняем фоном и не валим UI при ошибке.
                val resp = RetrofitClient.api.deleteLike(backendId)
                if (resp.isSuccessful) {
                    Log.d(TAG, "Like deleted on backend: backendId=$backendId")
                } else {
                    Log.w(TAG, "deleteLike HTTP ${resp.code()}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "deleteLike failed (non-critical): ${e.message}")
            }
        }
    }

    // Проверяет, есть ли элемент с указанным id в избранном.
    fun contains(id: String) = favorites.any { it.id == id }

    // Возвращает избранные элементы только выбранной категории.
    fun byCategory(cat: FavoriteCategory) = favorites.filter { it.category == cat }

    // Загружает избранное с backend и объединяет его с локальным кэшем.
    fun syncFromBackend() {
        viewModelScope.launch {
            isSyncing = true
            try {
                // Запрашиваем сохранённые лайки у сервера.
                val resp = RetrofitClient.api.getLikes()
                if (resp.isSuccessful) {
                    val remote = resp.body() ?: emptyList()
                    // Каждый DTO превращаем в локальную модель, попутно восстанавливая текст и категорию.
                    favorites = remote.mapNotNull { dto ->
                        // Категория хранится строкой, поэтому распаковываем её осторожно.
                        val cat = runCatching {
                            FavoriteCategory.valueOf(dto.category ?: "")
                        }.getOrNull() ?: return@mapNotNull null

                        // jsonData содержит сырой ответ, из которого можно достать полный текст и КБЖУ.
                        val ocr = runCatching {
                            gson.fromJson(dto.jsonData, OCRResponse::class.java)
                        }.getOrNull()

                        // Одну и ту же сущность используем и для локального кэша, и для UI.
                        val resultText = ocr?.solution ?: ocr?.result ?: ocr?.content ?: ocr?.description
                        FavoriteItem(
                            id         = "backend_${dto.id}",
                            backendId  = dto.id,
                            timestamp  = parseTimestamp(dto.createdAt),
                            category   = normalizeFavoriteCategory(cat, resultText, ocr?.tag),
                            title      = buildTitleFromOcr(ocr, cat, resultText),
                            resultText = resultText,
                            calories   = ocr?.calories,
                            proteins   = ocr?.proteins,
                            fats       = ocr?.fats,
                            carbs      = ocr?.carbs
                        )
                    }
                    // После синка сервер становится источником правды, поэтому обновлённый список снова кэшируем локально.
                    persist()
                    Log.d(TAG, "Synced ${favorites.size} likes from backend")
                } else {
                    Log.w(TAG, "getLikes HTTP ${resp.code()}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "syncFromBackend failed, using local cache: ${e.message}")
            } finally {
                isSyncing = false
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Строит заголовок избранного из OCR-ответа и категории.
    private fun buildTitleFromOcr(ocr: OCRResponse?, cat: FavoriteCategory, resultText: String?): String {
        // Если ответа нет вообще, лучше показать хотя бы имя категории.
        if (ocr == null) return cat.displayName
        return when (cat) {
            // Для еды в заголовок идут калории, потому что это самый короткий и понятный сигнал.
            FavoriteCategory.FOOD          -> "🍽 ${ocr.calories ?: "?"} ккал"
            // Для поиска объектов важнее label первого результата, чем весь текст ответа.
            FavoriteCategory.OBJECT_SEARCH -> "🔍 ${ocr.detections?.firstOrNull()?.label ?: "Объект"}"
            // Для картинок показываем первую найденную сущность.
            FavoriteCategory.IMAGES        -> "📸 ${ocr.detections?.firstOrNull()?.label ?: "Изображение"}"
            // Физика и химия часто начинаются с служебных markdown-заголовков, поэтому чистим текст.
            FavoriteCategory.PHYSICS       -> "⚛️ ${buildFavoriteTitleFromText(resultText, "Физика")}"
            FavoriteCategory.CHEMISTRY     -> "🧪 ${buildFavoriteTitleFromText(resultText, "Химия")}"
            // Для остальных категорий берём первую содержательную строку.
            else -> buildFavoriteTitleFromText(resultText, cat.displayName)
        }
    }

    // Преобразует backend-дату в timestamp или возвращает текущее время.
    private fun parseTimestamp(createdAt: String?): Long {
        // Если сервер не прислал дату, fallback на текущее время, чтобы сортировка не ломалась.
        if (createdAt == null) return System.currentTimeMillis()
        return runCatching {
            // формат от Spring: "2026-05-10T12:34:56" или массив [2026,5,10,12,34,56]
            java.time.LocalDateTime.parse(createdAt)
                .toInstant(java.time.ZoneOffset.UTC)
                .toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
    }
}
