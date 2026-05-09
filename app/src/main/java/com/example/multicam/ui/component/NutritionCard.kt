package com.example.multicam.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ProteinColor = Color(0xFF4FC3F7)
private val FatColor     = Color(0xFFFFB74D)
private val CarbColor    = Color(0xFF81C784)

data class NutritionData(
    val calories: Int,
    val proteins: Int,
    val fats: Int,
    val carbs: Int
)

@Composable
fun NutritionCard(
    data: NutritionData,
    isLiked: Boolean = false,
    likeEnabled: Boolean = false,
    onLike: () -> Unit = {}
) {
    val total = (data.proteins + data.fats + data.carbs).toFloat().coerceAtLeast(1f)

    val proteinSweep = (data.proteins / total) * 360f
    val fatSweep     = (data.fats / total) * 360f
    val carbSweep    = (data.carbs / total) * 360f

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }
    val progress = animProgress.value

    // Like button bounce animation
    val likeScale by animateFloatAsState(
        targetValue   = if (isLiked) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "likeScale"
    )

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
            // ── Header: title + calories badge + like button ──────────────────
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CaloriesBadge(calories = data.calories)

                    // ── Like button ───────────────────────────────────────────
                    IconButton(
                        onClick  = onLike,
                        enabled  = likeEnabled,
                        modifier = Modifier.scale(likeScale)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite
                            else Icons.Default.FavoriteBorder,
                            contentDescription = if (isLiked) "Убрать из избранного"
                            else "В избранное",
                            tint = if (isLiked) Color(0xFFE53935)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // ── Donut chart + legend ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 22.dp.toPx()
                        val inset = strokeWidth / 2f
                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                        val topLeft = Offset(inset, inset)

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

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    MacroRow(
                        color   = ProteinColor,
                        label   = "Белки",
                        value   = data.proteins,
                        percent = if (total > 0) (data.proteins / total * 100).toInt() else 0
                    )
                    MacroRow(
                        color   = FatColor,
                        label   = "Жиры",
                        value   = data.fats,
                        percent = if (total > 0) (data.fats / total * 100).toInt() else 0
                    )
                    MacroRow(
                        color   = CarbColor,
                        label   = "Углеводы",
                        value   = data.carbs,
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
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.weight(1f)
        )
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