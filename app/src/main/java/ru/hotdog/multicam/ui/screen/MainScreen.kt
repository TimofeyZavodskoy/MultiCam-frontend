package ru.hotdog.multicam.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import kotlinx.coroutines.launch
import ru.hotdog.multicam.model.FavoriteCategory
import ru.hotdog.multicam.model.FavoriteItem
import ru.hotdog.multicam.model.buildFavoriteTitleFromText
import ru.hotdog.multicam.model.detectFavoriteCategory
import ru.hotdog.multicam.ui.component.*

// Хранит цвет, подпись и иконку категории результата.
private data class SubjectAccent(
    val color: Color,       // акцентный цвет полоски и иконки
    val label: String,      // короткий лейбл-тег
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

// Подбирает визуальный акцент по backend-тегу результата.
@Composable
private fun accentForTag(tag: String?): SubjectAccent = when (tag) {
    "math", "mixed" -> SubjectAccent(
        color = Color(0xFF7C6FF7), // фиолетовый — математика
        label = "Математика",
        icon = Icons.Outlined.Calculate
    )
    "physics" -> SubjectAccent(
        color = Color(0xFF378ADD), // синий — физика
        label = "Физика",
        icon = Icons.Outlined.Science
    )
    "chemistry" -> SubjectAccent(
        color = Color(0xFF1D9E75), // зелёный — химия
        label = "Химия",
        icon = Icons.Outlined.Biotech
    )
    "food" -> SubjectAccent(
        color = Color(0xFFEF9F27), // янтарный — еда
        label = "Питание",
        icon = Icons.Outlined.Restaurant
    )
    "objects" -> SubjectAccent(
        color = Color(0xFFD4537E), // розовый — объекты
        label = "Объект",
        icon = Icons.Outlined.Search
    )
    "text" -> SubjectAccent(
        color = Color(0xFF888780), // серый — текст
        label = "Текст",
        icon = Icons.Outlined.TextFields
    )
    else -> SubjectAccent(
        color = Color(0xFF888780),
        label = "Изображение",
        icon = Icons.Outlined.Image
    )
}

// Определяет категорию текущего результата для избранного.
private fun detectCategory(vm: ImageViewModel): FavoriteCategory =
    detectFavoriteCategory(
        text = vm.result,
        tag = vm.rawResponse?.tag,
        hasNutrition = vm.nutritionData != null,
        hasSearchResults = vm.searchResult.isNotEmpty(),
        hasDetectionsOnly = vm.detections?.isNotEmpty() == true && vm.result == null
    )

// Формирует короткий заголовок текущего результата для избранного.
private fun buildTitle(vm: ImageViewModel): String = when {
    vm.nutritionData != null     -> "🍽 ${vm.nutritionData!!.calories} ккал"
    vm.searchResult.isNotEmpty() -> "🔍 ${vm.detections?.firstOrNull()?.label ?: "Объект"}"
    vm.detections?.isNotEmpty() == true ->
        "📸 ${vm.detections!!.firstOrNull()?.label ?: "Изображение"}"
    else -> buildFavoriteTitleFromText(vm.result)
}

// Рисует главный экран выбора изображения, анализа и результата.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isGuest: Boolean = false,
    onRegisterClick: () -> Unit = {},
    viewModel: ImageViewModel = viewModel(),
    favoritesVm: FavoritesViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context     = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()
    val listState   = rememberLazyListState()

    var selectedUri        by remember { mutableStateOf<Uri?>(null) }
    var isReasoningVisible by remember { mutableStateOf(false) }
    var imageSize          by remember { mutableStateOf<androidx.compose.ui.unit.IntSize?>(null) }

    LaunchedEffect(selectedUri) { imageSize = null }
    LaunchedEffect(viewModel.error) {
        viewModel.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedUri = uri
    }

    ModalNavigationDrawer(
        drawerState     = drawerState,
        gesturesEnabled = true,
        drawerContent   = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) { FavoritesDrawerContent(vm = favoritesVm) }
        }
    ) {
        Scaffold(
            modifier = modifier,
            // Прозрачный TopAppBar — контент начинается прямо от системного бара
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text       = "MultiCam",
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector        = Icons.Outlined.Menu,
                                contentDescription = "Избранное",
                                tint               = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        if (isGuest) {
                            // Компактный текстовый badge для гостя
                            TextButton(
                                onClick = onRegisterClick,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text     = "Войти",
                                    style    = MaterialTheme.typography.labelLarge,
                                    color    = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->

            LazyColumn(
                state           = listState,
                modifier        = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding  = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {

                // ── Превью изображения ────────────────────────────────────────
                item {
                    ImagePreviewSection(
                        uri          = selectedUri,
                        detections   = viewModel.detections,
                        onSizeKnown  = { imageSize = it }
                    )
                }

                // ── Кнопки действий ───────────────────────────────────────────
                item {
                    ActionButtonsRow(
                        hasImage  = selectedUri != null,
                        isLoading = viewModel.isLoading,
                        onPick    = { launcher.launch("image/*") },
                        onAnalyze = { selectedUri?.let { viewModel.analyzeImage(context, it) } }
                    )
                }

                // ── Разделитель перед результатами ────────────────────────────
                if (viewModel.isLoading || viewModel.error != null ||
                    viewModel.result != null || viewModel.nutritionData != null ||
                    viewModel.detections?.isNotEmpty() == true) {
                    item {
                        HorizontalDivider(
                            modifier  = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            thickness = 0.5.dp,
                            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                // ── Состояние загрузки ────────────────────────────────────────
                if (viewModel.isLoading) {
                    item { LoadingSection() }
                }

                // ── Карточка питания ──────────────────────────────────────────
                viewModel.nutritionData?.let { nutrition ->
                    item {
                        AnimatedResultCard {
                            NutritionCard(
                                data        = nutrition,
                                isLiked     = favoritesVm.contains(viewModel.currentResultId ?: ""),
                                likeEnabled = viewModel.currentResultId != null,
                                onLike      = {
                                    val id = viewModel.currentResultId ?: return@NutritionCard
                                    if (favoritesVm.contains(id)) {
                                        favoritesVm.remove(id)
                                    } else {
                                        val cat = FavoriteCategory.FOOD
                                        favoritesVm.add(
                                            item = FavoriteItem(
                                                id        = id,
                                                timestamp = System.currentTimeMillis(),
                                                category  = cat,
                                                title     = buildTitle(viewModel),
                                                calories  = nutrition.calories,
                                                proteins  = nutrition.proteins,
                                                fats      = nutrition.fats,
                                                carbs     = nutrition.carbs
                                            ),
                                            rawResponse = viewModel.rawResponse,
                                            category    = cat
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // ── Карточка текстового результата ────────────────────────────
                viewModel.result?.let { resultText ->
                    item {
                        AnimatedResultCard {
                            SubjectResultCard(
                                resultText         = resultText,
                                tag                = viewModel.rawResponse?.tag,
                                isReasoningVisible = isReasoningVisible,
                                onToggleReasoning  = { isReasoningVisible = !isReasoningVisible },
                                currentResultId    = viewModel.currentResultId,
                                isLiked            = favoritesVm.contains(viewModel.currentResultId ?: ""),
                                onLike             = {
                                    val id = viewModel.currentResultId ?: return@SubjectResultCard
                                    if (favoritesVm.contains(id)) {
                                        favoritesVm.remove(id)
                                    } else {
                                        val cat = detectCategory(viewModel)
                                        favoritesVm.add(
                                            item = FavoriteItem(
                                                id         = id,
                                                timestamp  = System.currentTimeMillis(),
                                                category   = cat,
                                                title      = buildTitle(viewModel),
                                                resultText = resultText
                                            ),
                                            rawResponse = viewModel.rawResponse,
                                            category    = cat
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // ── Карточка только-детекций (image без текста) ───────────────
                if (viewModel.nutritionData == null && viewModel.result == null) {
                    viewModel.detections?.takeIf { it.isNotEmpty() }?.let { dets ->
                        item {
                            AnimatedResultCard {
                                DetectionsOnlyCard(
                                    labels          = dets.map { it.label },
                                    currentResultId = viewModel.currentResultId,
                                    isLiked         = favoritesVm.contains(viewModel.currentResultId ?: ""),
                                    onLike          = {
                                        val id = viewModel.currentResultId ?: return@DetectionsOnlyCard
                                        if (favoritesVm.contains(id)) {
                                            favoritesVm.remove(id)
                                        } else {
                                            val cat = FavoriteCategory.IMAGES
                                            favoritesVm.add(
                                                item = FavoriteItem(
                                                    id        = id,
                                                    timestamp = System.currentTimeMillis(),
                                                    category  = cat,
                                                    title     = buildTitle(viewModel)
                                                ),
                                                rawResponse = viewModel.rawResponse,
                                                category    = cat
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Карточка ссылок на маркетплейсы ──────────────────────────
                viewModel.searchResult.takeIf { it.isNotEmpty() }?.let { links ->
                    item {
                        AnimatedResultCard {
                            ProductLinksCard(
                                objectLabel  = viewModel.detections?.firstOrNull()?.label ?: "товар",
                                searchResult = links
                            )
                        }
                    }
                }
            }
        }
    }
}

// Показывает выбранное изображение и кнопку его замены.
@Composable
private fun ImagePreviewSection(
    uri: Uri?,
    detections: List<ru.hotdog.multicam.api.dto.DetectedObj>?,
    onSizeKnown: (androidx.compose.ui.unit.IntSize) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (uri != null) {
            AsyncImage(
                model              = uri,
                contentDescription = null,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop,
                onState            = { state ->
                    if (state is AsyncImagePainter.State.Success) {
                        val sz = state.painter.intrinsicSize
                        if (sz.width > 0f && sz.height > 0f) {
                            onSizeKnown(
                                androidx.compose.ui.unit.IntSize(sz.width.toInt(), sz.height.toInt())
                            )
                        }
                    }
                }
            )

            // Нижний scrim — мягкий переход к белому/тёмному фону
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.6f)
                            )
                        )
                    )
            )

            // Оверлей точек детекций
            detections?.takeIf { it.isNotEmpty() }?.let { dets ->
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    dets.forEach { det ->
                        val b  = det.bbox
                        val cx = (b.x + b.width  / 2f) * size.width
                        val cy = (b.y + b.height / 2f) * size.height
                        // Тень точки
                        drawCircle(Color.Black.copy(alpha = 0.25f), 10f, Offset(cx + 1.5f, cy + 1.5f))
                        // Белое кольцо
                        drawCircle(Color.White, 10f, Offset(cx, cy))
                        // Красная заливка
                        drawCircle(Color(0xFFE24B4A), 7f, Offset(cx, cy))
                    }
                }
            }
        } else {
            // Плейсхолдер
            Column(
                modifier            = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector        = Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    modifier           = Modifier.size(40.dp),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    text  = "Выберите фото",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// Рисует кнопки выбора изображения и запуска анализа.
@Composable
private fun ActionButtonsRow(
    hasImage: Boolean,
    isLoading: Boolean,
    onPick: () -> Unit,
    onAnalyze: () -> Unit
) {
    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick  = onPick,
            modifier = Modifier.weight(1f),
            shape    = RoundedCornerShape(10.dp),
            border   = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector        = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                modifier           = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("Выбрать", style = MaterialTheme.typography.labelLarge)
        }

        Button(
            onClick         = onAnalyze,
            enabled         = hasImage && !isLoading,
            modifier        = Modifier.weight(1f),
            shape           = RoundedCornerShape(10.dp),
            contentPadding  = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            colors          = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor   = MaterialTheme.colorScheme.surface
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color       = MaterialTheme.colorScheme.surface
                )
            } else {
                Icon(
                    imageVector        = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier           = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text          = "Анализировать",
                    style         = MaterialTheme.typography.labelLarge,
                    maxLines      = 1,
                    softWrap      = false,
                    letterSpacing = (-0.3).sp
                )
            }
        }
    }
}

// Показывает индикатор загрузки во время анализа изображения.
@Composable
private fun LoadingSection() {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LinearProgressIndicator(
            modifier    = Modifier.fillMaxWidth(),
            color       = MaterialTheme.colorScheme.primary,
            trackColor  = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text  = "Анализируем изображение…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Оборачивает результат в карточку с анимацией появления.
@Composable
private fun AnimatedResultCard(content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter   = fadeIn(tween(300)) + slideInVertically(
            animationSpec  = tween(300, easing = EaseOutCubic),
            initialOffsetY = { it / 4 }
        )
    ) {
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            content()
        }
    }
}

// Рисует карточку текстового результата с тегом, reasoning и лайком.
@Composable
private fun SubjectResultCard(
    resultText: String,
    tag: String?,
    isReasoningVisible: Boolean,
    onToggleReasoning: () -> Unit,
    currentResultId: String?,
    isLiked: Boolean,
    onLike: () -> Unit
) {
    val accent = accentForTag(tag)

    // Разбиваем «ход мыслей» от основного решения
    val parts     = resultText.split("#### Ход мыслей")
    val solution  = parts[0]
    val reasoning = if (parts.size > 1) parts[1] else null

    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {

            // Цветная полоска слева — визуальный идентификатор предмета
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accent.color)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
            ) {

                // ── Шапка: тег + кнопка лайка ────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // Тег предмета
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accent.color.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier            = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment   = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector        = accent.icon,
                                contentDescription = null,
                                modifier           = Modifier.size(13.dp),
                                tint               = accent.color
                            )
                            Text(
                                text  = accent.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent.color,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Кнопка лайка — минималистичная, без фона
                    LikeIconButton(
                        isLiked = isLiked,
                        enabled = currentResultId != null,
                        onClick = onLike
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── Основной текст результата ─────────────────────────────────
                ResultText(
                    text     = solution,
                    modifier = Modifier.fillMaxWidth()
                )

                // ── Блок «Ход решения» (сворачиваемый) ───────────────────────
                reasoning?.let { think ->
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(8.dp))

                    // Заголовок блока — нажимаемый
                    Row(
                        modifier  = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onToggleReasoning)
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text  = "Ход решения",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = if (isReasoningVisible)
                                Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(
                        visible = isReasoningVisible,
                        enter   = fadeIn() + expandVertically(),
                        exit    = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            ResultText(
                                text     = think,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

// Рисует карточку найденных объектов без текстового ответа.
@Composable
private fun DetectionsOnlyCard(
    labels: List<String>,
    currentResultId: String?,
    isLiked: Boolean,
    onLike: () -> Unit
) {
    val accent = accentForTag("objects")

    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accent.color)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "Обнаружено",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    LikeIconButton(isLiked = isLiked, enabled = currentResultId != null, onClick = onLike)
                }

                Spacer(Modifier.height(10.dp))

                labels.forEach { label ->
                    Row(
                        modifier            = Modifier.padding(vertical = 3.dp),
                        verticalAlignment   = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accent.color)
                        )
                        Text(
                            text  = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// Рисует анимированную кнопку добавления результата в избранное.
@Composable
private fun LikeIconButton(
    isLiked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = if (isLiked) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "likeScale"
    )
    val tint by animateColorAsState(
        targetValue   = if (isLiked) Color(0xFFE24B4A)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        animationSpec = tween(200),
        label         = "likeTint"
    )

    IconButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier
            .size(36.dp)
            .scale(scale)
    ) {
        Icon(
            imageVector        = if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isLiked) "Убрать из избранного" else "В избранное",
            tint               = tint,
            modifier           = Modifier.size(20.dp)
        )
    }
}