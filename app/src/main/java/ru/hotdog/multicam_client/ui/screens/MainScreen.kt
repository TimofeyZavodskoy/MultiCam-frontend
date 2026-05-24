package ru.hotdog.multicam_client.ui.screens

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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import ru.hotdog.multicam_client.ui.components.MarkdownText
import ru.hotdog.multicam_client.ui.components.NutritionCard

@Composable
fun MainScreen(viewModel: ImageViewModel = viewModel(), modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()
    var isReasoningVisible by remember { mutableStateOf(false) }

    // Реальные размеры загруженного изображения (нужны для точного позиционирования точек)
    var imageIntrinsicSize by remember { mutableStateOf<IntSize?>(null) }
    LaunchedEffect(selectedUri) { imageIntrinsicSize = null }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> selectedUri = uri }

    LaunchedEffect(viewModel.error) {
        viewModel.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Анализ изображения", style = MaterialTheme.typography.headlineMedium)
        }

        // ── Фото ─────────────────────────────────────────────────────────
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
                    // Захватываем intrinsicSize через onState
                    AsyncImage(
                        model = selectedUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Success) {
                                val sz = state.painter.intrinsicSize
                                if (sz.width > 0 && sz.height > 0) {
                                    imageIntrinsicSize = IntSize(
                                        sz.width.toInt(),
                                        sz.height.toInt()
                                    )
                                }
                            }
                        }
                    )

                    // Точки на объектах — правильные координаты с учётом letterbox
                    val intrinsic = imageIntrinsicSize
                    viewModel.detections?.takeIf { it.isNotEmpty() && intrinsic != null }
                        ?.let { dets ->
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Вычисляем реальную область отображения при ContentScale.Fit
                                val scaleX = size.width  / intrinsic!!.width
                                val scaleY = size.height / intrinsic.height
                                val scale  = minOf(scaleX, scaleY)

                                val renderedW = intrinsic.width  * scale
                                val renderedH = intrinsic.height * scale
                                // Смещение из-за letterbox (центрирование)
                                val ox = (size.width  - renderedW) / 2f
                                val oy = (size.height - renderedH) / 2f

                                dets.forEach { det ->
                                    val b  = det.bbox
                                    // Центр bbox → пиксели канваса
                                    val cx = ox + (b.x + b.width  / 2f) * renderedW
                                    val cy = oy + (b.y + b.height / 2f) * renderedH

                                    // Тень
                                    drawCircle(
                                        color  = Color.Black.copy(alpha = 0.35f),
                                        radius = 14f,
                                        center = Offset(cx + 2f, cy + 2f)
                                    )
                                    // Красная точка
                                    drawCircle(
                                        color  = Color(0xFFFF3B30),
                                        radius = 12f,
                                        center = Offset(cx, cy)
                                    )
                                    // Белый ободок
                                    drawCircle(
                                        color  = Color.White,
                                        radius = 12f,
                                        center = Offset(cx, cy),
                                        style  = Stroke(width = 2.5f)
                                    )
                                }
                            }
                        }
                } else {
                    Text("Фото не выбрано", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── Кнопки ───────────────────────────────────────────────────────
        item {
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

        // ── Результат ────────────────────────────────────────────────────
        when {
            viewModel.isLoading -> item {
                CircularProgressIndicator()
                Text("Анализируем...")
            }

            viewModel.error != null -> item {
                Text(viewModel.error!!, color = MaterialTheme.colorScheme.error)
            }

            else -> {
                // КБЖУ
                viewModel.nutritionData?.let { nutrition ->
                    item { NutritionCard(data = nutrition) }
                }

                // Текстовый ответ (math / OCR / описание)
                viewModel.result?.let { resultText ->
                    val parts = resultText.split("#### Ход мыслей")
                    val solution = parts[0]
                    val reasoning = if (parts.size > 1) parts[1] else null

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                MarkdownText(markdown = solution)

                                reasoning?.let {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isReasoningVisible = !isReasoningVisible },
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
                                        MarkdownText(
                                            markdown = it,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Список объектов — только если нет текста и нет КБЖУ
                if (viewModel.nutritionData == null && viewModel.result == null) {
                    viewModel.detections?.takeIf { it.isNotEmpty() }?.let { dets ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Обнаруженные объекты",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    dets.forEach { det ->
                                        Text("• ${det.label}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}