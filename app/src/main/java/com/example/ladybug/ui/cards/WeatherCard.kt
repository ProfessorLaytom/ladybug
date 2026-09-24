package com.example.ladybug.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ladybug.model.SearchResult
import java.util.Locale

@Composable
fun WeatherCard(result: SearchResult.Weather) {
    if (result.dailyForecasts.isEmpty()) return

    // THE INTERACTIVE STATE
    var selectedDayIndex by remember { mutableIntStateOf(0) }
    var selectedHourIndex by remember { mutableStateOf<Int?>(null) } // Null means the whole Day is selected

    val selectedDay = result.dailyForecasts.getOrElse(selectedDayIndex) { result.dailyForecasts.first() }
    val selectedHour = selectedHourIndex?.let { selectedDay.hourlyForecasts.getOrNull(it) }

    // DYNAMIC HEADER DATA: Swaps seamlessly between Day aggregate and Hour specific
    val displayDateText = if (selectedHour != null) "${selectedDay.dateLabel} at ${selectedHour.time}" else selectedDay.dateLabel
    val displayEmoji = selectedHour?.symbolCode ?: selectedDay.symbolCode
    val displayTemp = selectedHour?.let { "${it.temp.toInt()}°C" } ?: "${selectedDay.maxTemp.toInt()}° / ${selectedDay.minTemp.toInt()}°"
    val displayWind = selectedHour?.windSpeed ?: selectedDay.windSpeed
    val displayHum = selectedHour?.humidity ?: selectedDay.humidity
    val displayPrecip = selectedHour?.precipitationAmount ?: selectedDay.precipitationAmount

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // 1. HEADER (Dynamically driven by state)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = result.cityName.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // The plain-text indicator of what time context we are looking at
                    Text(
                        text = displayDateText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayTemp,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(text = weatherCodeToEmoji(displayEmoji), fontSize = 64.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. DETAILS ROW (Dynamically driven by state)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WeatherDetailItem(icon = "💨", label = "${String.format(Locale.US, "%.1f", displayWind)} m/s")
                WeatherDetailItem(icon = "💧", label = "${displayHum.toInt()}%")
                // Format safely to 1 decimal place for millimeters
                WeatherDetailItem(icon = "☔", label = "${String.format(Locale.US, "%.1f", displayPrecip)} mm")
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 3. DAILY TABS
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(result.dailyForecasts) { index, daily ->
                    val isSelected = index == selectedDayIndex
                    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface


                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(containerColor)
                            .clickable {
                                selectedDayIndex = index
                                selectedHourIndex = null // Reset the hour when changing days!
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = daily.dateLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = weatherCodeToEmoji(daily.symbolCode), fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${daily.maxTemp.toInt()}° / ${daily.minTemp.toInt()}°",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. INTERACTIVE HOURLY FORECAST
            Text(
                text = "Hourly - ${selectedDay.dateLabel}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(selectedDay.hourlyForecasts) { index, hourly ->
                    val isSelected = index == selectedHourIndex
                    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)


                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(containerColor)
                            .clickable { selectedHourIndex = index } // Clicking selects this specific hour
                            .padding(8.dp)
                    ) {
                        Text(
                            text = hourly.time,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = weatherCodeToEmoji(hourly.symbolCode), fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${hourly.temp.toInt()}°",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // FIX: Precipitation AMOUNT is now strictly rendered no matter what
                        if (hourly.precipitationAmount > 0) {
                            Text(
                                text = "💧 ${String.format(Locale.US, "%.1f", hourly.precipitationAmount)} mm",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else {
                            // Keeps the box height consistent when there is no rain
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherDetailItem(icon: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun weatherCodeToEmoji(code: String): String {
    val lowerCode = code.lowercase()
    return when {
        lowerCode.contains("clearsky") -> "☀️"
        lowerCode.contains("fair") || lowerCode.contains("partlycloudy") -> "⛅"
        lowerCode.contains("cloudy") -> "☁️"
        lowerCode.contains("rain") && lowerCode.contains("thunder") -> "⛈️"
        lowerCode.contains("rain") -> "🌧️"
        lowerCode.contains("sleet") -> "🌨️"
        lowerCode.contains("snow") -> "❄️"
        lowerCode.contains("fog") -> "🌫️"
        else -> "🌡️"
    }
}