package com.example.ladybug.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.ladybug.R
import com.example.ladybug.data.SearchHistoryStore
import com.example.ladybug.model.SearchEngine
import com.example.ladybug.model.SearchHistoryEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 📜 Search history popup content (opened from the empty-search pill button).
 * Lists every saved internet search (newest first), grouped by day.
 * Tapping an entry only places its text back into the search bar (nothing is opened automatically).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchHistoryCard(
    entries: List<SearchHistoryEntry>,
    onEntryClick: (SearchHistoryEntry) -> Unit,
    onClose: () -> Unit,
    selectedEngine: SearchEngine = SearchEngine.SEARXNG,
    onEngineSelected: (SearchEngine) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var engineDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        SearchHistoryStore.ensureLoaded(context)
    }

    val rows = remember(entries) { buildHistoryRows(entries) }

    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) {
            listState.scrollToItem(0, 0)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_scroll),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Historique (${entries.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { engineDropdownExpanded = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = selectedEngine.shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                maxLines = 1
                            )
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.surfaceVariant) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineDropdownExpanded)
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = engineDropdownExpanded,
                        onDismissRequest = { engineDropdownExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        SearchEngine.entries.forEach { engineOption ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = engineOption.shortLabel,
                                        fontWeight = if (engineOption == selectedEngine) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    engineDropdownExpanded = false
                                    onEngineSelected(engineOption)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_close_24),
                        contentDescription = "Close history",
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Aucune recherche pour l'instant",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_scroll),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { index, row ->
                            when (row) {
                                is HistoryRow.DayHeader -> "header_${row.label}"
                                is HistoryRow.Item -> "item_${row.entry.timestamp}_$index"
                            }
                        },
                        contentType = { _, row ->
                            when (row) {
                                is HistoryRow.DayHeader -> "header"
                                is HistoryRow.Item -> "item"
                            }
                        }
                    ) { _, row ->
                        when (row) {
                            is HistoryRow.DayHeader -> Text(
                                text = row.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp)
                            )
                            is HistoryRow.Item -> SearchHistoryRow(
                                entry = row.entry,
                                timeLabel = row.timeLabel,
                                onClick = { onEntryClick(row.entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHistoryRow(
    entry: SearchHistoryEntry,
    timeLabel: String,
    onClick: () -> Unit
) {
    val emoji = when (entry.type) {
        SearchHistoryEntry.Type.WEB -> "🔍"
        SearchHistoryEntry.Type.URL -> "🌐"
        SearchHistoryEntry.Type.WIKIPEDIA -> "📖"
    }

    val subtitle = when (entry.type) {
        SearchHistoryEntry.Type.WIKIPEDIA -> {
            val lang = wikipediaLanguage(entry.url)
            listOfNotNull(
                timeLabel,
                lang,
                entry.query.takeIf { it.isNotBlank() && !it.equals(entry.title, ignoreCase = true) }?.let { "« $it »" }
            ).joinToString(" · ")
        }
        else -> timeLabel
    }

    // Tier 2 row (crimson surface with blush text), matching FishHistoryCard rows
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 20.sp)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.searchBarText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private sealed class HistoryRow {
    data class DayHeader(val label: String) : HistoryRow()
    data class Item(val entry: SearchHistoryEntry, val timeLabel: String) : HistoryRow()
}

/** Flattens newest-first entries into day headers ("Aujourd'hui", "Hier", "lun. 24 oct.") + rows. */
private fun buildHistoryRows(entries: List<SearchHistoryEntry>): List<HistoryRow> {
    if (entries.isEmpty()) return emptyList()

    val timeFormat = SimpleDateFormat("HH:mm", Locale.FRENCH)
    val dayFormat = SimpleDateFormat("EEE d MMM", Locale.FRENCH)
    val dayYearFormat = SimpleDateFormat("EEE d MMM yyyy", Locale.FRENCH)

    val now = Calendar.getInstance()
    val todayKey = dayKey(now)
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now.timeInMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }
    val yesterdayKey = dayKey(yesterday)
    val currentYear = now.get(Calendar.YEAR)

    val rows = mutableListOf<HistoryRow>()
    val cal = Calendar.getInstance()
    var lastDayKey: Int? = null

    entries.forEach { entry ->
        cal.timeInMillis = entry.timestamp
        val key = dayKey(cal)
        if (key != lastDayKey) {
            lastDayKey = key
            val date = Date(entry.timestamp)
            val label = when {
                key == todayKey -> "Aujourd'hui"
                key == yesterdayKey -> "Hier"
                cal.get(Calendar.YEAR) == currentYear -> dayFormat.format(date)
                else -> dayYearFormat.format(date)
            }
            rows.add(HistoryRow.DayHeader(label))
        }
        rows.add(HistoryRow.Item(entry, timeFormat.format(Date(entry.timestamp))))
    }
    return rows
}

private fun dayKey(cal: Calendar): Int = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)

/** Extracts "EN" / "FR" from a Wikipedia article URL host (e.g. fr.wikipedia.org). */
private fun wikipediaLanguage(url: String): String? {
    val host = try {
        url.toUri().host
    } catch (_: Exception) {
        null
    } ?: return null
    return host.substringBefore('.').takeIf { it.length in 2..3 }?.uppercase(Locale.ROOT)
}
