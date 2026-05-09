package com.example.multicam.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ProteinColor = Color(0xFF4FC3F7) // голубой
private val FatColor     = Color(0xFFFFB74D) // оранжевый
private val CarbColor    = Color(0xFF81C784) // зелёный

data class NutritionData(
    val calories: Int,
    val proteins: Int,
    val fats: Int,
    val carbs: Int
)

@Composable
fun NutritionCard(data: NutritionData) {
    val total = (data.proteins + data.fats + data.carbs).toFloat().coerceAtLeast(1f)

    val proteinSweep = (data.proteins / total) * 360f
    val fatSweep     = (data.fats / total) * 360f
    val carbSweep    = (data.carbs / total) * 360f

    // Анимация заполнения диаграммы
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }
    val progress = animProgress.value

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Заголовок + калории
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Питательная ценность",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                CaloriesBadge(calories = data.calories)
            }

            // Диаграмма + легенда
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Круговая диаграмма (пончик)
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 22.dp.toPx()
                        val inset = strokeWidth / 2f
                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                        val topLeft = Offset(inset, inset)

                        // Фоновый круг
                        drawArc(
                            color = Color.White.copy(alpha = 0.08f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )

                        var startAngle = -90f

                        // Белки
                        val pSweep = proteinSweep * progress
                        if (pSweep > 0.5f) {
                            drawArc(
                                color = ProteinColor,
                                startAngle = startAngle,
                                sweepAngle = pSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                        }
                        startAngle += proteinSweep

                        // Жиры
                        val fSweep = fatSweep * progress
                        if (fSweep > 0.5f) {
                            drawArc(
                                color = FatColor,
                                startAngle = startAngle,
                                sweepAngle = fSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                        }
                        startAngle += fatSweep

                        // Углеводы
                        val cSweep = carbSweep * progress
                        if (cSweep > 0.5f) {
                            drawArc(
                                color = CarbColor,
                                startAngle = startAngle,
                                sweepAngle = cSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                        }
                    }

                    // Текст в центре
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${data.calories}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "ккал",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                // Легенда — макронутриенты
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    MacroRow(
                        color = ProteinColor,
                        label = "Белки",
                        value = data.proteins,
                        percent = if (total > 0) (data.proteins / total * 100).toInt() else 0
                    )
                    MacroRow(
                        color = FatColor,
                        label = "Жиры",
                        value = data.fats,
                        percent = if (total > 0) (data.fats / total * 100).toInt() else 0
                    )
                    MacroRow(
                        color = CarbColor,
                        label = "Углеводы",
                        value = data.carbs,
                        percent = if (total > 0) (data.carbs / total * 100).toInt() else 0
                    )
                }
            }
        }
    }
}

@Composable
private fun CaloriesBadge(calories: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$calories ккал",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun MacroRow(color: Color, label: String, value: Int, percent: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Цветной кружок-индикатор
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        // Название
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.weight(1f)
        )
        // Граммы + процент
        Text(
            text = "${value}г",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$percent%",
            fontSize = 12.sp,
            color = color,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp)
        )
    }
}
