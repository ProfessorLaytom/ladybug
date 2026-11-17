package com.example.ladybug.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ladybug.R
import com.example.ladybug.model.SearchResult
import com.example.ladybug.model.WorldLocation
import com.example.ladybug.providers.WorldClockProvider
import kotlinx.coroutines.delay

@Composable
fun WorldClockCard(
    result: SearchResult.WorldClock,
    modifier: Modifier = Modifier
) {
    if (result.singleLocation != null) {
        WorldClockSingleCard(
            location = result.singleLocation,
            matchedByCountry = result.matchedByCountry,
            modifier = modifier
        )
    } else {
        WorldClockFavoritesCard(modifier = modifier)
    }
}

@Composable
fun WorldClockSingleCard(
    location: WorldLocation,
    matchedByCountry: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        WorldClockProvider.ensureFavoritesLoaded(context)
    }

    val favorites by WorldClockProvider.favoritesFlow.collectAsState()
    val isFavorite = favorites.contains(location.id)

    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(15_000L)
            value = System.currentTimeMillis()
        }
    }

    val snapshot = remember(location, nowMillis) {
        WorldClockProvider.computeSnapshot(location, nowMillis)
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
            // Top Row: City + Capital/Country Subtitle + Heart Favorite Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = location.cityName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = snapshot.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = if (location.isCapital) FontStyle.Normal else FontStyle.Italic,
                        fontWeight = if (location.isCapital) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (matchedByCountry && location.isCapital) {
                        Text(
                            text = "Capitale de ${location.countryNameFr} • Capital of ${location.countryNameEn}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Heart (Favorite) Button — exact same coloring scheme as FishCard
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable {
                            WorldClockProvider.toggleFavorite(context, location.id)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(
                                id = if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                            ),
                            contentDescription = if (isFavorite) "Liked" else "Not liked",
                            tint = if (isFavorite) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Middle Row: Big 24h Time + Day/Night Emoji
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = snapshot.time24h,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = snapshot.dayEmoji,
                    fontSize = 52.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Tier 2 Pills: Relative Day + Date & Hour Offset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "📅 ${snapshot.relativeDayLabel} • ${snapshot.dateLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "🕒 ${snapshot.offsetLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WorldClockFavoritesCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        WorldClockProvider.ensureFavoritesLoaded(context)
    }

    val favorites by WorldClockProvider.favoritesFlow.collectAsState()
    var selectedLocation by remember { mutableStateOf<WorldLocation?>(null) }

    val favoriteLocations = remember(favorites) {
        WorldClockProvider.getFavoriteLocations(favorites)
    }

    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(15_000L)
            value = System.currentTimeMillis()
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
                Text(
                    text = "🌍 Horloge Mondiale (24h)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Tape 'heure [ville/pays]' (ex: 'time us', 'heure tokyo') pour chercher ou ajouter aux favoris ❤️",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (favoriteLocations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucune ville en favori ❤️\nCherche 'heure paris' ou 'time us' pour en ajouter !",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = favoriteLocations,
                        key = { it.id }
                    ) { location ->
                        val isFav = favorites.contains(location.id)
                        val snapshot = remember(location, nowMillis) {
                            WorldClockProvider.computeSnapshot(location, nowMillis)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { selectedLocation = location }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = snapshot.dayEmoji,
                                fontSize = 26.sp
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = location.cityName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = snapshot.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${snapshot.relativeDayLabel} • ${snapshot.offsetLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = snapshot.time24h,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = {
                                    WorldClockProvider.toggleFavorite(context, location.id)
                                }
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                                    ),
                                    contentDescription = if (isFav) "Liked" else "Not liked",
                                    tint = if (isFav) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedLocation?.let { loc ->
        Dialog(onDismissRequest = { selectedLocation = null }) {
            WorldClockSingleCard(location = loc)
        }
    }
}
