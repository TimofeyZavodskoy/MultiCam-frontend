package ru.hotdog.multicam.model

// Определяет категорию избранного по метке, данным ответа и текстовым признакам.
fun detectFavoriteCategory(
    text: String?,
    tag: String? = null,
    hasNutrition: Boolean = false,
    hasSearchResults: Boolean = false,
    hasDetectionsOnly: Boolean = false
): FavoriteCategory {
    // Если сервер уже отдал тип, не спорим с ним.
    val normalizedTag = tag?.trim()?.lowercase()
    categoryFromTag(normalizedTag)?.let { return it }

    // Питание и поиск объектов имеют приоритет над текстовой эвристикой.
    if (hasNutrition) return FavoriteCategory.FOOD
    if (hasSearchResults) return FavoriteCategory.OBJECT_SEARCH
    if (hasDetectionsOnly) return FavoriteCategory.IMAGES

    // Дальше работаем только с нормализованным текстом.
    val normalizedText = text?.lowercase().orEmpty()
    return when {
        // Пустой ответ сохраняем как обычный текст, чтобы не плодить ложные категории.
        normalizedText.isBlank() -> FavoriteCategory.TEXT
        // Химия раньше математики: у неё часто встречаются уравнения и формулы.
        looksLikeChemistry(normalizedText) -> FavoriteCategory.CHEMISTRY
        // Физика тоже любит символы и числа, поэтому проверяем её раньше математики.
        looksLikePhysics(normalizedText) -> FavoriteCategory.PHYSICS
        // Только если ничего из вышеперечисленного не подошло, считаем это математикой.
        looksLikeMath(normalizedText) -> FavoriteCategory.MATH
        else -> FavoriteCategory.TEXT
    }
}

// Уточняет категорию сохранённого элемента по метке и тексту.
fun normalizeFavoriteCategory(
    category: FavoriteCategory,
    text: String?,
    tag: String? = null
): FavoriteCategory {
    // Если есть явная метка — используем её.
    categoryFromTag(tag?.trim()?.lowercase())?.let { return it }
    // Категории, отличные от математики, не переклассифицируем по тексту.
    if (category != FavoriteCategory.MATH) return category

    // Старые записи "математика" часто на самом деле физика или химия.
    val normalizedText = text?.lowercase().orEmpty()
    return when {
        looksLikeChemistry(normalizedText) -> FavoriteCategory.CHEMISTRY
        looksLikePhysics(normalizedText) -> FavoriteCategory.PHYSICS
        else -> category
    }
}

// Строит короткий заголовок избранного из первой содержательной строки ответа.
fun buildFavoriteTitleFromText(
    text: String?,
    fallback: String = "Ответ",
    maxLength: Int = 50
): String {
    // Идём по строкам сверху вниз, потому что первая "настоящая" строка обычно и есть заголовок.
    val candidate = text
        ?.lineSequence()
        ?.mapNotNull(::cleanTitleLine)
        ?.firstOrNull { it.isNotBlank() }
        ?: cleanTitleLine(fallback)
        ?: fallback

    // Схлопываем лишние пробелы и режем длинные заголовки, чтобы карточка не расползалась.
    return candidate
        .replace(Regex("""\s+"""), " ")
        .trim()
        .let { if (it.length > maxLength) it.take(maxLength).trimEnd() else it }
        .ifBlank { fallback }
}

// Убирает служебные markdown-префиксы и строки, которые не должны попадать в заголовок.
private fun cleanTitleLine(line: String): String? {
    // Пустые и code fence строки не несут полезного заголовка.
    var value = line.trim()
    if (value.isBlank()) return null
    if (value.startsWith("```")) return null

    // Снимаем markdown-разметку начала строки: заголовки, списки, нумерацию.
    value = value
        .replace(Regex("""^#{1,6}\s*"""), "")
        .replace(Regex("""^[-*+]\s+"""), "")
        .replace(Regex("""^\d+\.\s+"""), "")
        .trim()
        .trim('*', '_', '`')
        .trim()

    if (value.isBlank()) return null

    // Нормализуем строку для проверки на служебные шаблоны вроде "Анализ задачи".
    val normalized = value
        .lowercase()
        .replace(Regex("""[.:;!?-]+$"""), "")
        .trim()

    // Эти строки — не ответ, а шапка генератора или поясняющий блок.
    if (normalized in serviceTitleLines || serviceTitlePrefixes.any { normalized.startsWith(it) }) {
        return null
    }

    // Сценарий "Ответ: ..." должен вернуть только сам ответ.
    val labelMatch = Regex(
        """^(ответ|итог|вывод|результат|answer|result|conclusion)\s*[:.-]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    ).matchEntire(value)
    if (labelMatch != null) return labelMatch.groupValues[2].trim().ifBlank { null }

    return value
}

// Точные служебные строки, которые часто появляются как заголовки в сгенерированном ответе.
private val serviceTitleLines = setOf(
    "анализ",
    "анализ задачи",
    "разбор",
    "разбор задачи",
    "решение",
    "ход решения",
    "ход мыслей",
    "рассуждение",
    "объяснение",
    "дано",
    "analysis",
    "task analysis",
    "solution",
    "reasoning",
    "explanation"
)

// Префиксы, которые считаем служебными даже если к ним дописан хвост.
private val serviceTitlePrefixes = setOf(
    "анализ задачи",
    "разбор задачи",
    "ход решения",
    "ход мыслей",
    "task analysis"
)

// Переводит серверный tag в локальную категорию избранного.
private fun categoryFromTag(tag: String?): FavoriteCategory? {
    return when (tag) {
        "food", "nutrition", "еда", "питание" -> FavoriteCategory.FOOD
        "math", "mathematics", "математика", "матеша" -> FavoriteCategory.MATH
        "physics", "физика" -> FavoriteCategory.PHYSICS
        "chemistry", "chem", "химия" -> FavoriteCategory.CHEMISTRY
        "object_search", "search", "поиск" -> FavoriteCategory.OBJECT_SEARCH
        "image", "images", "изображение", "изображения" -> FavoriteCategory.IMAGES
        "text", "ocr", "текст" -> FavoriteCategory.TEXT
        else -> null
    }
}

// Грубая эвристика для химии.
// Основана на словах-триггерах и на видах формул, где есть химические элементы и реакции.
private fun looksLikeChemistry(text: String): Boolean {
    // Сначала пробуем словари, потому что они дешевле и обычно точнее для описательного текста.
    val chemistryWords = listOf(
        "хим", "молекул", "атом", "реакц", "реагент", "продукт",
        "кислот", "основани", "щелоч", "соль", "оксид", "валент",
        "моль", "моляр", "электрол", "окислен", "восстанов",
        "chemical", "chemistry", "molecule", "atom", "reaction", "reagent",
        "acid", "base", "oxide", "molar", "oxidation", "reduction"
    )
    if (chemistryWords.any { text.contains(it) }) return true

    // Если слов не хватило, смотрим на формулу вида H2SO4 + NaOH -> ...
    val formula = Regex("""\b(?:h|he|li|be|b|c|n|o|f|ne|na|mg|al|si|p|s|cl|ar|k|ca|fe|cu|zn|ag|au|hg|pb|br|i)(?:\d+)?(?:[a-z]{0,2}\d*)+\b""")
    return formula.containsMatchIn(text) && (text.contains("=") || text.contains("->") || text.contains("→"))
}

// Грубая эвристика для физики.
// Здесь ищем характерные слова, единицы измерения и типичные обозначения величин.
private fun looksLikePhysics(text: String): Boolean {
    // Словарь физических терминов работает лучше, чем попытка искать только символы.
    val physicsWords = listOf(
        "физ", "сила", "скорост", "ускорен", "масса", "энерг", "импульс",
        "давлен", "мощност", "работа", "напряжен", "ток", "сопротивлен",
        "заряд", "поле", "частот", "период", "амплитуд", "ньютон", "джоуль",
        "ватт", "паскаль", "кулон", "ом", "physics", "force", "velocity",
        "speed", "acceleration", "mass", "energy", "momentum", "pressure",
        "power", "voltage", "current", "resistance", "charge", "frequency"
    )
    if (physicsWords.any { text.contains(it) }) return true

    // Отдельно ловим типовые единицы, потому что физические задачи часто состоят именно из них.
    val units = Regex("""\b(н|дж|вт|па|кпа|ом|а|в|кл|гц|м/с|м/с\^2|m/s|m/s\^2|n|j|w|pa|hz)\b""")
    return units.containsMatchIn(text)
}

// Распознаёт математический текст по уравнениям и спецсимволам.
private fun looksLikeMath(text: String): Boolean {
    return text.contains("=") ||
            text.contains("²") ||
            text.contains("√") ||
            text.contains("∫") ||
            text.contains("∑") ||
            text.contains("∞")
}
