package ru.hotdog.multicam.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import kotlinx.coroutines.launch
import ru.hotdog.multicam.model.FavoriteCategory
import ru.hotdog.multicam.model.FavoriteItem
import ru.hotdog.multicam.model.buildFavoriteTitleFromText
import ru.hotdog.multicam.model.detectFavoriteCategory
import ru.hotdog.multicam.ui.component.FavoritesDrawerContent
import ru.hotdog.multicam.ui.component.NutritionCard
import ru.hotdog.multicam.ui.component.ProductLinksCard
import ru.hotdog.multicam.ui.component.ResultText

// ── Category / title helpers ──────────────────────────────────────────────────

// На этом экране категорию нужно вычислять локально, потому что от неё зависит вкладка избранного.
// Приоритеты важны: сначала явные server tags, потом еда/поиск/изображения, потом уже текстовые эвристики.
private fun detectCategory(vm: ImageViewModel): FavoriteCategory =
    detectFavoriteCategory(
        text = vm.result,
        tag = vm.rawResponse?.tag,
        hasNutrition = vm.nutritionData != null,
        hasSearchResults = vm.searchResult.isNotEmpty(),
        hasDetectionsOnly = vm.detections?.isNotEmpty() == true && vm.result == null
    )

// Заголовок должен быть коротким и осмысленным, иначе карточки в избранном превращаются в мусорный лог.
private fun buildTitle(vm: ImageViewModel): String = when {
    vm.nutritionData != null    -> "🍽 ${vm.nutritionData!!.calories} ккал"
    vm.searchResult.isNotEmpty() -> "🔍 ${vm.detections?.firstOrNull()?.label ?: "Объект"}"
    vm.detections?.isNotEmpty() == true ->
        "📸 ${vm.detections!!.firstOrNull()?.label ?: "Изображение"}"
    else -> buildFavoriteTitleFromText(vm.result)
}

// ── Screen ────────────────────────────────────────────────────────────────────

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
    // drawerState нужен, чтобы управлять боковым меню с избранным.
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    // Coroutine scope позволяет открывать/закрывать drawer из обработчиков кнопок.
    val scope       = rememberCoroutineScope()
    // Состояние списка нужно, чтобы Compose корректно сохранял позицию и не пересоздавал скролл лишний раз.
    val listState   = rememberLazyListState()

    // Выбранная пользователем картинка.
    var selectedUri        by remember { mutableStateOf<Uri?>(null) }
    // Флаг для показа/скрытия "хода мыслей" в текстовой карточке.
    var isReasoningVisible by remember { mutableStateOf(false) }
    // Размер картинки нужен для правильного пересчёта координат overlay-детекций.
    var imageIntrinsicSize by remember { mutableStateOf<IntSize?>(null) }

    // Когда меняется выбранный файл, старый размер картинки уже невалиден.
    LaunchedEffect(selectedUri) { imageIntrinsicSize = null }
    // Ошибку показываем через Toast, чтобы не ломать текущий экран.
    LaunchedEffect(viewModel.error) {
        viewModel.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    // Activity Result API нужен для выбора изображения из галереи.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedUri = uri
    }

    // Боковое меню с избранным.
    ModalNavigationDrawer(
        drawerState   = drawerState,
        gesturesEnabled = true,  // свайп влево закрывает
        scrimColor = DrawerDefaults.scrimColor,  // клик на затемнение закрывает
        drawerContent = {
            // Сам drawer-sheet с категориями и списком сохранённых ответов.
            ModalDrawerSheet { FavoritesDrawerContent(vm = favoritesVm) }
        }
    ) {
        // Scaffold даёт стандартную структуру: top bar + content.
        Scaffold(
            modifier = modifier,
            topBar   = {
                TopAppBar(
                    title = { Text("MultiCam", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        // Кнопка меню открывает drawer с избранным.
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Избранное")
                        }
                    },
                    actions = {
                        if (isGuest) GuestBadge(onClick = onRegisterClick)
                    }
                )
            }
        ) { innerPadding ->

            // Основной контент экрана: список карточек, собранный через LazyColumn.
            LazyColumn(
                state       = listState,
                modifier    = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
            ) {

                // ── Image preview ─────────────────────────────────────────────
                item {
                    // Блок превью нужен, чтобы пользователь сразу видел загруженное изображение и детекции.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedUri != null) {
                            // Показываем изображение с сохранением пропорций.
                            AsyncImage(
                                model              = selectedUri,
                                contentDescription = null,
                                modifier           = Modifier.fillMaxSize(),
                                contentScale       = ContentScale.Fit,
                                onState            = { state ->
                                    if (state is AsyncImagePainter.State.Success) {
                                        val sz = state.painter.intrinsicSize
                                        if (sz.width > 0 && sz.height > 0) {
                                            imageIntrinsicSize = IntSize(sz.width.toInt(), sz.height.toInt())
                                        }
                                    }
                                }
                            )

                            // Overlay рисуем только если уже знаем фактический размер исходного изображения.
                            val intrinsic = imageIntrinsicSize
                            viewModel.detections
                                ?.takeIf { it.isNotEmpty() && intrinsic != null }
                                ?.let { dets ->
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        // Пересчитываем координаты bbox из нормализованной системы в пиксели экрана.
                                        val scaleX    = size.width  / intrinsic!!.width
                                        val scaleY    = size.height / intrinsic.height
                                        val scale     = minOf(scaleX, scaleY)
                                        val renderedW = intrinsic.width  * scale
                                        val renderedH = intrinsic.height * scale
                                        val ox        = (size.width  - renderedW) / 2f
                                        val oy        = (size.height - renderedH) / 2f

                                        dets.forEach { det ->
                                            val b  = det.bbox
                                            val cx = ox + (b.x + b.width  / 2f) * renderedW
                                            val cy = oy + (b.y + b.height / 2f) * renderedH
                                            drawCircle(Color.Black.copy(alpha = 0.35f), 14f, Offset(cx + 2f, cy + 2f))
                                            drawCircle(Color(0xFFFF3B30), 12f, Offset(cx, cy))
                                            drawCircle(Color.White, 12f, Offset(cx, cy), style = Stroke(width = 2.5f))
                                        }
                                    }
                                }
                        } else {
                            // Если изображения нет, показываем нейтральный плейсхолдер.
                            Text("Фото не выбрано", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // ── Buttons ───────────────────────────────────────────────────
                item {
                    // Две основные команды экрана: выбрать файл и отправить его на анализ.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { launcher.launch("image/*") }) {
                            Text("Выбрать фото")
                        }
                        Button(
                            onClick = { selectedUri?.let { viewModel.analyzeImage(context, it) } },
                            enabled = selectedUri != null && !viewModel.isLoading
                        ) {
                            Text("Анализировать")
                        }
                    }
                }

                // ── Results ───────────────────────────────────────────────────
                when {
                    viewModel.isLoading -> item {
                        // Пока сервер отвечает, показываем компактный индикатор загрузки.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Анализируем...")
                        }
                    }

                    viewModel.error != null -> item {
                        // Ошибка с сервера или сети выводится прямо здесь.
                        Text(viewModel.error!!, color = MaterialTheme.colorScheme.error)
                    }

                    else -> {
                        // ── Nutrition card ────────────────────────────────────
                        viewModel.nutritionData?.let { nutrition ->
                            item {
                                // Карточка питания хранится отдельно, потому что у неё другой формат данных.
                                NutritionCard(
                                    data        = nutrition,
                                    isLiked     = favoritesVm.contains(viewModel.currentResultId ?: ""),
                                    likeEnabled = viewModel.currentResultId != null,
                                    onLike      = {
                                        val id = viewModel.currentResultId ?: return@NutritionCard
                                        if (favoritesVm.contains(id)) {
                                            favoritesVm.remove(id)
                                        } else {
                                            // Для nutrition категория фиксированная, эвристика не нужна.
                                            val cat = FavoriteCategory.FOOD
                                            favoritesVm.add(
                                                item = FavoriteItem(
                                                    id = id,
                                                    timestamp = System.currentTimeMillis(),
                                                    category = cat,
                                                    title = buildTitle(viewModel),
                                                    calories = nutrition.calories,
                                                    proteins = nutrition.proteins,
                                                    fats = nutrition.fats,
                                                    carbs = nutrition.carbs
                                                ),
                                                rawResponse = viewModel.rawResponse,
                                                category    = cat
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        // ── Text result card ──────────────────────────────────
                        viewModel.result?.let { resultText ->
                            // Ответ может содержать служебный блок "ход мыслей", его выносим отдельно.
                            val parts     = resultText.split("#### Ход мыслей")
                            val solution  = parts[0]
                            val reasoning = if (parts.size > 1) parts[1] else null

                            item {
                                // На карточке ответа лайк должен сохранять и текст, и raw payload.
                                ResultCard(
                                    solution           = solution,
                                    reasoning          = reasoning,
                                    isReasoningVisible = isReasoningVisible,
                                    onToggleReasoning  = { isReasoningVisible = !isReasoningVisible },
                                    currentResultId    = viewModel.currentResultId,
                                    isLiked            = favoritesVm.contains(viewModel.currentResultId ?: ""),
                                    onLike             = {
                                        val id = viewModel.currentResultId ?: return@ResultCard
                                        if (favoritesVm.contains(id)) {
                                            favoritesVm.remove(id)
                                        } else {
                                            // Категорию определяем по содержимому, чтобы физика/химия не попадали в math.
                                            val cat = detectCategory(viewModel)
                                            favoritesVm.add(
                                                item = FavoriteItem(
                                                    id         = id,
                                                    timestamp  = System.currentTimeMillis(),
                                                    category   = cat,
                                                    title      = buildTitle(viewModel),
                                                    resultText = resultText,
                                                    calories   = viewModel.nutritionData?.calories,
                                                    proteins   = viewModel.nutritionData?.proteins,
                                                    fats       = viewModel.nutritionData?.fats,
                                                    carbs      = viewModel.nutritionData?.carbs
                                                ),
                                                rawResponse = viewModel.rawResponse,
                                                category    = cat
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        // ── Detections-only card ──────────────────────────────
                        if (viewModel.nutritionData == null && viewModel.result == null) {
                            viewModel.detections?.takeIf { it.isNotEmpty() }?.let { dets ->
                                item {
                                    // Если есть только объекты, сохраняем это как отдельный сценарий "изображения".
                                    DetectionsCard(
                                        detections      = dets.map { it.label },
                                        currentResultId = viewModel.currentResultId,
                                        isLiked         = favoritesVm.contains(viewModel.currentResultId ?: ""),
                                        onLike          = {
                                            val id = viewModel.currentResultId ?: return@DetectionsCard
                                            if (favoritesVm.contains(id)) {
                                                favoritesVm.remove(id)
                                            } else {
                                                // Для такого ответа категория также фиксированная.
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

                        // ── Product links ─────────────────────────────────────
                        viewModel.searchResult.takeIf { it.isNotEmpty() }?.let { links ->
                            item {
                                // Блок ссылок на товары выводим только когда бэкенд действительно вернул search results.
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
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun GuestBadge(onClick: () -> Unit) {
    // Отдельный chip для гостя, чтобы не смешивать его с основными действиями.
    Surface(
        onClick         = onClick,
        shape           = RoundedCornerShape(20.dp),
        color           = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation  = 2.dp,
        modifier        = Modifier.padding(end = 8.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.Person,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier           = Modifier.size(14.dp)
            )
            Text(
                text       = "Регистрация",
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun LikeButton(isLiked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    // Лайк визуально усиливаем анимацией масштаба и цветом, чтобы состояние читалось мгновенно.
    val scale by animateFloatAsState(
        targetValue   = if (isLiked) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "heartScale"
    )
    val tint by animateColorAsState(
        targetValue = if (isLiked) Color(0xFFE53935)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        label = "heartTint"
    )
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.scale(scale)) {
        Icon(
            imageVector        = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (isLiked) "Убрать из избранного" else "В избранное",
            tint               = tint
        )
    }
}

@Composable
private fun ResultCard(
    solution: String,
    reasoning: String?,
    isReasoningVisible: Boolean,
    onToggleReasoning: () -> Unit,
    currentResultId: String?,
    isLiked: Boolean,
    onLike: () -> Unit
) {
    // Основная карточка текстового результата: слева контент, справа сердце.
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                // Текст рендерится через отдельный слой, чтобы markdown и latex жили в одном месте.
                Box(modifier = Modifier.weight(1f)) {
                    ResultText(text = solution)
                }
                LikeButton(isLiked = isLiked, enabled = currentResultId != null, onClick = onLike)
            }

            reasoning?.let {
                // Разделитель нужен, чтобы визуально отделить итог от объяснения.
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleReasoning),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ход решения", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text(if (isReasoningVisible) "▲" else "▼")
                }
                AnimatedVisibility(visible = isReasoningVisible) {
                    // Блок reasoning проходит через тот же рендерер, что и основной ответ.
                    ResultText(text = it, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun DetectionsCard(
    detections: List<String>,
    currentResultId: String?,
    isLiked: Boolean,
    onLike: () -> Unit
) {
    // У карточки детекций задача проще: показать список объектов и дать сохранить результат как image-only.
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Заголовок здесь намеренно короткий, потому что список ниже уже несёт смысл.
                Text("Обнаруженные объекты", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
                LikeButton(isLiked = isLiked, enabled = currentResultId != null, onClick = onLike)
            }
            Spacer(Modifier.height(8.dp))
            // Лейблы показываем простым списком без дополнительного рендера.
            detections.forEach { label -> Text("• $label") }
        }
    }
}
