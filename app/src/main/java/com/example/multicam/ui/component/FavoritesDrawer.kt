package com.example.multicam.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.multicam.model.FavoriteCategory
import com.example.multicam.model.FavoriteItem
import com.example.multicam.ui.screen.FavoritesViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FavoritesDrawerContent(vm: FavoritesViewModel) {
    val tabs = FavoriteCategory.values().toList()
    var selectedTab by remember { mutableStateOf(0) }
    var detailItem  by remember { mutableStateOf<FavoriteItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Column {
                Text(
                    text = "⭐ Избранное",
                    style    = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color    = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = "${vm.favorites.size} сохранённых ответов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        // ── Tabs ──────────────────────────────────────────────────────────────
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding      = 0.dp,
            containerColor   = MaterialTheme.colorScheme.surface,
            contentColor     = MaterialTheme.colorScheme.primary,
            divider          = {}
        ) {
            tabs.forEachIndexed { index, cat ->
                Tab(
                    selected = selectedTab == index,
                    onClick  = { selectedTab = index },
                    text = {
                        Text(
                            text = "${cat.emoji} ${cat.displayName}",
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                )
            }
        }
        HorizontalDivider()

        // ── List or empty state ───────────────────────────────────────────────
        val categoryItems = vm.byCategory(tabs[selectedTab])

        if (categoryItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tabs[selectedTab].emoji, fontSize = 52.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Пусто",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Нажмите ♡ на результате,\nчтобы добавить в избранное",
                        style     = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier        = Modifier.fillMaxSize(),
                contentPadding  = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categoryItems, key = { it.id }) { fav ->
                    FavoriteCard(
                        item     = fav,
                        onDelete = { vm.remove(fav.id) },
                        onTap    = { detailItem = fav }
                    )
                }
            }
        }
    }

    // ── Detail dialog ─────────────────────────────────────────────────────────
    detailItem?.let { item ->
        FavoriteDetailDialog(item = item, onDismiss = { detailItem = null })
    }
}

// ── Favorite card ─────────────────────────────────────────────────────────────

@Composable
private fun FavoriteCard(
    item: FavoriteItem,
    onDelete: () -> Unit,
    onTap: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = item.title,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = formatTimestamp(item.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
                if (item.calories != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = "🔥${item.calories} · 💪${item.proteins}г · 🧈${item.fats}г · 🌾${item.carbs}г",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector        = Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
                )
            }
        }
    }
}

// ── Detail dialog ─────────────────────────────────────────────────────────────

@Composable
private fun FavoriteDetailDialog(item: FavoriteItem, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape    = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text       = item.title,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))

                // Nutrition summary
                if (item.calories != null) {
                    listOf(
                        "🔥 Калории" to "${item.calories} ккал",
                        "💪 Белки"   to "${item.proteins} г",
                        "🧈 Жиры"    to "${item.fats} г",
                        "🌾 Углеводы" to "${item.carbs} г"
                    ).forEach { (label, value) ->
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(value, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                }

                // Text result with scroll
                item.resultText?.let { text ->
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(scrollState)
                    ) {
                        Text(text, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(10.dp))
                }

                TextButton(
                    onClick   = onDismiss,
                    modifier  = Modifier.align(Alignment.End)
                ) { Text("Закрыть") }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatTimestamp(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L       -> "только что"
        diff < 3_600_000L    -> "${diff / 60_000} мин назад"
        diff < 86_400_000L   -> "${diff / 3_600_000} ч назад"
        diff < 604_800_000L  -> "${diff / 86_400_000} дн назад"
        else -> SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ts))
    }
}
