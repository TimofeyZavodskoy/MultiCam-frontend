package com.example.multicam.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.example.multicam.model.FavoriteCategory
import com.example.multicam.model.FavoriteItem
import com.example.multicam.ui.component.FavoritesDrawerContent
import com.example.multicam.ui.component.MarkdownText
import com.example.multicam.ui.component.NutritionCard
import com.example.multicam.ui.component.ProductLinksCard
import kotlinx.coroutines.launch

// ── Category detection ────────────────────────────────────────────────────────

private fun detectCategory(vm: ImageViewModel): FavoriteCategory = when {
    vm.nutritionData != null          -> FavoriteCategory.FOOD
    vm.searchResult.isNotEmpty()      -> FavoriteCategory.OBJECT_SEARCH
    vm.detections?.isNotEmpty() == true && vm.result == null -> FavoriteCategory.IMAGES
    vm.result?.let { text ->
        text.contains("=") || text.contains("²") || text.contains("√") ||
                text.contains("∫") || text.contains("∑") || text.contains("∞")
    } == true -> FavoriteCategory.MATH
    else -> FavoriteCategory.TEXT
}

private fun buildTitle(vm: ImageViewModel): String = when {
    vm.nutritionData != null ->
        "🍽 ${vm.nutritionData!!.calories} ккал"
    vm.searchResult.isNotEmpty() ->
        "🔍 ${vm.detections?.firstOrNull()?.label ?: "Объект"}"
    vm.detections?.isNotEmpty() == true ->
        "📸 ${vm.detections!!.firstOrNull()?.label ?: "Изображение"}"
    else ->
        vm.result?.lines()?.firstOrNull { it.isNotBlank() }
            ?.take(50)?.trim() ?: "Ответ"
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ImageViewModel = viewModel(),
    favoritesVm: FavoritesViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context       = LocalContext.current
    val drawerState   = rememberDrawerState(DrawerValue.Closed)
    val scope         = rememberCoroutineScope()
    val listState     = rememberLazyListState()

    var selectedUri          by remember { mutableStateOf<Uri?>(null) }
    var isReasoningVisible   by remember { mutableStateOf(false) }
    var imageIntrinsicSize   by remember { mutableStateOf<IntSize?>(null) }

    LaunchedEffect(selectedUri) { imageIntrinsicSize = null }
    LaunchedEffect(viewModel.error) {
        viewModel.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedUri = uri
    }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            ModalDrawerSheet { FavoritesDrawerContent(vm = favoritesVm) }
        }
    ) {
        Scaffold(
            modifier = modifier,
            topBar   = {
                TopAppBar(
                    title = {
                        Text("MultiCam", fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Избранное")
                        }
                    }
                )
            }
        ) { innerPadding ->

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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedUri != null) {
                            AsyncImage(
                                model        = selectedUri,
                                contentDescription = null,
                                modifier     = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                onState      = { state ->
                                    if (state is AsyncImagePainter.State.Success) {
                                        val sz = state.painter.intrinsicSize
                                        if (sz.width > 0 && sz.height > 0) {
                                            imageIntrinsicSize = IntSize(sz.width.toInt(), sz.height.toInt())
                                        }
                                    }
                                }
                            )

                            val intrinsic = imageIntrinsicSize
                            viewModel.detections
                                ?.takeIf { it.isNotEmpty() && intrinsic != null }
                                ?.let { dets ->
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val scaleX   = size.width  / intrinsic!!.width
                                        val scaleY   = size.height / intrinsic.height
                                        val scale    = minOf(scaleX, scaleY)
                                        val renderedW = intrinsic.width  * scale
                                        val renderedH = intrinsic.height * scale
                                        val ox       = (size.width  - renderedW) / 2f
                                        val oy       = (size.height - renderedH) / 2f

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
                            Text("Фото не выбрано", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // ── Buttons ───────────────────────────────────────────────────
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { launcher.launch("image/*") }) {
                            Text("Выбрать фото")
                        }
                        Button(
                            onClick  = { selectedUri?.let { viewModel.analyzeImage(context, it) } },
                            enabled  = selectedUri != null && !viewModel.isLoading
                        ) {
                            Text("Анализировать")
                        }
                    }
                }

                // ── Results ───────────────────────────────────────────────────
                when {
                    viewModel.isLoading -> item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Анализируем...")
                        }
                    }

                    viewModel.error != null -> item {
                        Text(viewModel.error!!, color = MaterialTheme.colorScheme.error)
                    }

                    else -> {
                        // Nutrition card
                        viewModel.nutritionData?.let { nutrition ->
                            item { NutritionCard(data = nutrition) }
                        }

                        // Text result card
                        viewModel.result?.let { resultText ->
                            val parts     = resultText.split("#### Ход мыслей")
                            val solution  = parts[0]
                            val reasoning = if (parts.size > 1) parts[1] else null

                            item {
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
                                            favoritesVm.add(
                                                FavoriteItem(
                                                    id           = id,
                                                    timestamp    = System.currentTimeMillis(),
                                                    category     = detectCategory(viewModel),
                                                    title        = buildTitle(viewModel),
                                                    resultText   = resultText,
                                                    calories     = viewModel.nutritionData?.calories,
                                                    proteins     = viewModel.nutritionData?.proteins,
                                                    fats         = viewModel.nutritionData?.fats,
                                                    carbs        = viewModel.nutritionData?.carbs
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        // Detections-only card (no text, no nutrition)
                        if (viewModel.nutritionData == null && viewModel.result == null) {
                            viewModel.detections?.takeIf { it.isNotEmpty() }?.let { dets ->
                                item {
                                    DetectionsCard(
                                        detections      = dets.map { it.label },
                                        currentResultId = viewModel.currentResultId,
                                        isLiked         = favoritesVm.contains(viewModel.currentResultId ?: ""),
                                        onLike          = {
                                            val id = viewModel.currentResultId ?: return@DetectionsCard
                                            if (favoritesVm.contains(id)) {
                                                favoritesVm.remove(id)
                                            } else {
                                                favoritesVm.add(
                                                    FavoriteItem(
                                                        id        = id,
                                                        timestamp = System.currentTimeMillis(),
                                                        category  = FavoriteCategory.IMAGES,
                                                        title     = buildTitle(viewModel)
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Product links
                        viewModel.searchResult.takeIf { it.isNotEmpty() }?.let { links ->
                            item {
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

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun LikeButton(isLiked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector        = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (isLiked) "Убрать из избранного" else "В избранное",
            tint = if (isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    MarkdownText(markdown = solution)
                }
                LikeButton(
                    isLiked = isLiked,
                    enabled = currentResultId != null,
                    onClick = onLike
                )
            }

            reasoning?.let {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleReasoning),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Ход решения",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(if (isReasoningVisible) "▲" else "▼")
                }
                AnimatedVisibility(visible = isReasoningVisible) {
                    MarkdownText(markdown = it, modifier = Modifier.padding(top = 8.dp))
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "Обнаруженные объекты",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                LikeButton(
                    isLiked = isLiked,
                    enabled = currentResultId != null,
                    onClick = onLike
                )
            }
            Spacer(Modifier.height(8.dp))
            detections.forEach { label -> Text("• $label") }
        }
    }
}