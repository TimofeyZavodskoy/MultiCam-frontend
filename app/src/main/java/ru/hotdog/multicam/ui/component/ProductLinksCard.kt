package ru.hotdog.multicam.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.hotdog.multicam.api.dto.SearchResult

// Рисует карточку ссылок на маркетплейсы для найденного объекта.
@Composable
fun ProductLinksCard(
    objectLabel: String,
    searchResult: List<SearchResult>
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🔍 Найти «$objectLabel»",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(Modifier.height(12.dp))

            searchResult.forEach { result ->
                MarketplaceRow(
                    icon = result.icon ?: "\uD83D\uDED2",
                    name = result.marketplace,
                    url = result.url,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.url))
                        context.startActivity(intent)
                    }
               )
               if (result != searchResult.last()) {
                   HorizontalDivider(
                       modifier = Modifier.padding(6.dp),
                       color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)
                   )
               }
            }
        }
    }
}

// Рисует строку маркетплейса и открывает ссылку по нажатию.
@Composable
private fun MarketplaceRow(
    icon: String,
    name: String,
    url: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Emoji-иконка маркетплейса
            Text(text = icon, fontSize = 20.sp)

            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                // Укороченный URL для отображения
                Text(
                    text = url.take(40) + if (url.length > 40) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                )
            }
        }

        // Стрелка → открыть
        Text(
            text = "→",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}