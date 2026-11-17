package com.example.ladybug.ui.cards

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.ladybug.model.SearchResult
import com.example.ladybug.model.SearchResult.UnitConversion.Category
import com.example.ladybug.providers.CurrencyProvider
import com.example.ladybug.providers.WorldClockProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ConversionType(
    val label: String,
    val shortTitle: String,
    val subtitle: String,
    val unitCategory: Category?
) {
    CURRENCY("💱 Currency", "Live Exchange Rates", "EUR, USD, GBP...", null),
    TIME("🕒 Time", "24h Capital Time Converter", "00:00–23:59 (FR, US, AU...)", Category.TIME),
    TEMPERATURE("🌡️ Temperature", "Unit Converter", "°C, °F, K", Category.TEMPERATURE),
    LENGTH("📏 Length", "Unit Converter", "m, km, cm, ft, in, mi", Category.LENGTH),
    WEIGHT("⚖️ Weight", "Unit Converter", "kg, lb, g, oz", Category.WEIGHT),
    VOLUME("🧪 Volume", "Unit Converter", "l, ml, gal, qt, pt, cup", Category.VOLUME),
    VELOCITY("🚀 Speed", "Unit Converter", "km/h, mph, m/s", Category.VELOCITY);

    fun defaultUnits(): Pair<String, String> = when (this) {
        CURRENCY -> "EUR" to "AUD"
        TIME -> "FR - Paris (France)" to "AU - Canberra (Australia)"
        WEIGHT -> "kg" to "lb"
        LENGTH -> "m" to "ft"
        VELOCITY -> "km/h" to "m/s"
        VOLUME -> "l" to "gal"
        TEMPERATURE -> "°C" to "°F"
    }
}

fun Category.toConversionType(): ConversionType = when (this) {
    Category.WEIGHT -> ConversionType.WEIGHT
    Category.LENGTH -> ConversionType.LENGTH
    Category.VELOCITY -> ConversionType.VELOCITY
    Category.VOLUME -> ConversionType.VOLUME
    Category.TEMPERATURE -> ConversionType.TEMPERATURE
    Category.TIME -> ConversionType.TIME
}

@Composable
fun ConversionOptionsCard() {
    var selectedType by remember { mutableStateOf<ConversionType?>(null) }

    val currentSelection = selectedType
    if (currentSelection != null) {
        val (defaultFrom, defaultTo) = currentSelection.defaultUnits()
        UnifiedConversionCard(
            initialType = currentSelection,
            initialValue = null,
            initialFromUnit = defaultFrom,
            initialToUnit = defaultTo
        )
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Choose a conversion",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            val rows = ConversionType.entries.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { option ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedType = option }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Text(
                                        text = option.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnitConversionCard(result: SearchResult.UnitConversion) {
    UnifiedConversionCard(
        initialType = result.category.toConversionType(),
        initialValue = result.initialValue,
        initialFromUnit = result.initialFromUnit,
        initialToUnit = result.initialToUnit
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedConversionCard(
    initialType: ConversionType,
    initialValue: Double?,
    initialFromUnit: String,
    initialToUnit: String,
    initialRates: Map<String, Double>? = null
) {
    val context = LocalContext.current
    val stateKey = remember(initialType, initialValue, initialFromUnit, initialToUnit) {
        listOf(initialType, initialValue, initialFromUnit, initialToUnit)
    }

    var activeType by remember(stateKey) { mutableStateOf(initialType) }
    var rates by remember(initialRates) {
        mutableStateOf(
            if (!initialRates.isNullOrEmpty()) initialRates
            else CurrencyProvider.getCachedRatesImmediate(context)
        )
    }

    var fromUnit by remember(stateKey) { mutableStateOf(initialFromUnit) }
    var toUnit by remember(stateKey) { mutableStateOf(initialToUnit) }

    val effectiveInitialValue = remember(stateKey) {
        if (initialValue != null) {
            initialValue
        } else if (initialType == ConversionType.TIME) {
            currentMinutesInZone(initialFromUnit)
        } else {
            null
        }
    }

    var fromValueText by remember(stateKey) {
        mutableStateOf(
            if (effectiveInitialValue != null) formatForType(effectiveInitialValue, initialType) else ""
        )
    }
    var toValueText by remember(stateKey) {
        mutableStateOf(
            if (effectiveInitialValue != null) {
                formatForType(
                    convertForType(effectiveInitialValue, initialFromUnit, initialToUnit, initialType, rates),
                    initialType
                )
            } else {
                ""
            }
        )
    }

    // Refresh currency rates on IO if the user switches to CURRENCY
    LaunchedEffect(activeType) {
        if (activeType == ConversionType.CURRENCY) {
            val fetched = withContext(Dispatchers.IO) {
                CurrencyProvider.getOrFetchRates(context)
            }
            if (fetched.isNotEmpty()) {
                rates = fetched
                val parsed = parseInputForType(fromValueText, activeType)
                if (parsed != null) {
                    toValueText = formatForType(
                        convertForType(parsed, fromUnit, toUnit, activeType, fetched),
                        activeType
                    )
                }
            }
        }
    }

    val availableCurrencies = remember(rates) {
        val priorityCodes = listOf("EUR", "AUD", "JPY", "CNY", "USD", "GBP", "ILS")
        val priority = priorityCodes.filter { rates.containsKey(it) }
        val others = rates.keys.filter { !priorityCodes.contains(it) }.sorted()
        priority + others
    }

    val availableTimeLocations = remember {
        WorldClockProvider.getConverterLocations().map { it.converterLabel }
    }

    val availableUnits = when (activeType) {
        ConversionType.CURRENCY -> availableCurrencies
        ConversionType.TIME -> availableTimeLocations
        ConversionType.WEIGHT -> listOf("kg", "lb", "g", "oz")
        ConversionType.LENGTH -> listOf("m", "km", "cm", "in", "ft", "mi")
        ConversionType.VELOCITY -> listOf("km/h", "mph", "m/s")
        ConversionType.VOLUME -> listOf("l", "ml", "gal", "qt", "pt", "cup")
        ConversionType.TEMPERATURE -> listOf("°C", "°F", "K")
    }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Title + Category Switcher Dropdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activeType.shortTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { categoryDropdownExpanded = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = activeType.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            )
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.surfaceVariant) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded)
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                            ConversionType.entries.forEach { typeOption ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = typeOption.label,
                                            fontWeight = if (typeOption == activeType) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        categoryDropdownExpanded = false
                                        if (typeOption != activeType) {
                                            val previousType = activeType
                                            activeType = typeOption
                                            val (defFrom, defTo) = typeOption.defaultUnits()
                                            fromUnit = defFrom
                                            toUnit = defTo

                                            if (typeOption == ConversionType.TIME) {
                                                // If user had a 0..23 hour typed, convert it; otherwise pre-fill with current 24h time
                                                val parsedTime = parseInputForType(fromValueText, ConversionType.TIME)
                                                    ?: currentMinutesInZone(defFrom)
                                                fromValueText = formatForType(parsedTime, ConversionType.TIME)
                                                toValueText = formatForType(
                                                    convertForType(parsedTime, defFrom, defTo, ConversionType.TIME, rates),
                                                    ConversionType.TIME
                                                )
                                            } else {
                                                val rawCandidate = if (previousType == ConversionType.TIME) {
                                                    // Extract the hour (0..23) when switching from Time to another converter
                                                    parseInputForType(fromValueText, ConversionType.TIME)?.let { (it / 60.0).toInt().toDouble() }
                                                } else {
                                                    parseInputForType(fromValueText, typeOption)
                                                }

                                                if (rawCandidate != null) {
                                                    val effectiveVal = if (rawCandidate < 0 && typeOption != ConversionType.TEMPERATURE) {
                                                        abs(rawCandidate)
                                                    } else {
                                                        rawCandidate
                                                    }
                                                    fromValueText = formatForType(effectiveVal, typeOption)
                                                    toValueText = formatForType(
                                                        convertForType(effectiveVal, defFrom, defTo, typeOption, rates),
                                                        typeOption
                                                    )
                                                } else {
                                                    fromValueText = ""
                                                    toValueText = ""
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

            Spacer(modifier = Modifier.height(16.dp))

            // The main interactive row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // LEFT SIDE (Input)
                ConversionInput(
                    modifier = Modifier.weight(1f),
                    value = fromValueText,
                    placeholderText = if (activeType == ConversionType.TIME) "0–23 / HH:mm" else null,
                    onValueChange = { newValue ->
                        fromValueText = newValue
                        val parsed = parseInputForType(newValue, activeType)
                        toValueText = if (parsed != null) {
                            formatForType(
                                convertForType(parsed, fromUnit, toUnit, activeType, rates),
                                activeType
                            )
                        } else {
                            ""
                        }
                    },
                    selectedUnit = fromUnit,
                    units = availableUnits,
                    useLazyPopup = activeType == ConversionType.CURRENCY || activeType == ConversionType.TIME,
                    onUnitChange = { newUnit ->
                        fromUnit = newUnit
                        val parsed = parseInputForType(fromValueText, activeType)
                        if (parsed != null) {
                            toValueText = formatForType(
                                convertForType(parsed, fromUnit, toUnit, activeType, rates),
                                activeType
                            )
                        }
                    }
                )

                // Swap Units Button (Tier 2 crimson pill)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val prevFrom = fromUnit
                            fromUnit = toUnit
                            toUnit = prevFrom
                            val parsed = parseInputForType(fromValueText, activeType)
                            if (parsed != null) {
                                toValueText = formatForType(
                                    convertForType(parsed, fromUnit, toUnit, activeType, rates),
                                    activeType
                                )
                            }
                        }
                ) {
                    Text(
                        text = "⇄",
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                // RIGHT SIDE (Output) - acts dynamically as input too!
                ConversionInput(
                    modifier = Modifier.weight(1f),
                    value = toValueText,
                    placeholderText = if (activeType == ConversionType.TIME) "00:00–23:59" else null,
                    onValueChange = { newValue ->
                        toValueText = newValue
                        val parsed = parseInputForType(newValue, activeType)
                        fromValueText = if (parsed != null) {
                            formatForType(
                                convertForType(parsed, toUnit, fromUnit, activeType, rates),
                                activeType
                            )
                        } else {
                            ""
                        }
                    },
                    selectedUnit = toUnit,
                    units = availableUnits,
                    useLazyPopup = activeType == ConversionType.CURRENCY || activeType == ConversionType.TIME,
                    onUnitChange = { newUnit ->
                        toUnit = newUnit
                        val parsed = parseInputForType(fromValueText, activeType)
                        if (parsed != null) {
                            toValueText = formatForType(
                                convertForType(parsed, fromUnit, toUnit, activeType, rates),
                                activeType
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversionInput(
    modifier: Modifier = Modifier,
    value: String,
    placeholderText: String? = null,
    onValueChange: (String) -> Unit,
    selectedUnit: String,
    units: List<String>,
    useLazyPopup: Boolean = false,
    onUnitChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // The Number / 24h Time Input Field
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = if (placeholderText != null) {
                { Text(text = placeholderText, style = MaterialTheme.typography.bodySmall) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // The Dropdown Unit Selector
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            OutlinedTextField(
                value = selectedUnit,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            if (useLazyPopup) {
                if (expanded) {
                    Popup(
                        alignment = Alignment.TopStart,
                        onDismissRequest = { expanded = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        Surface(
                            modifier = Modifier
                                .exposedDropdownSize()
                                .padding(top = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 4.dp,
                            tonalElevation = 3.dp
                        ) {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 250.dp)
                            ) {
                                items(units) { unit ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = unit,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        onClick = {
                                            onUnitChange(unit)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    units.forEach { unit ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = unit,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                onUnitChange(unit)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- CONVERSION MATH ENGINE ---

private val timeInputRegex = Regex("^([01]?[0-9]|2[0-3])\\s*[:h.,]\\s*([0-5][0-9])?[h]?$", RegexOption.IGNORE_CASE)

/**
 * Parses user input according to the active ConversionType.
 * For TIME (24h, 0..23): accepts whole hours ("0" to "23") as well as "14:30", "14h30", "14.30", "14h", or "1430",
 * returning total minutes since midnight (0.0 to 1439.0).
 */
private fun parseInputForType(rawText: String, type: ConversionType): Double? {
    val trimmed = rawText.trim()
    if (trimmed.isEmpty()) return null

    if (type != ConversionType.TIME) {
        return trimmed.replace(',', '.').toDoubleOrNull()
    }

    // 1. Plain integer hour (0 to 23)
    val plainInt = trimmed.toIntOrNull()
    if (plainInt != null) {
        if (plainInt in 0..23) {
            return (plainInt * 60).toDouble()
        }
        // 3 or 4 digit military 24h shorthand (e.g. "930" -> 09:30, "1430" -> 14:30)
        if (trimmed.length in 3..4) {
            val h = trimmed.dropLast(2).toIntOrNull()
            val m = trimmed.takeLast(2).toIntOrNull()
            if (h != null && m != null && h in 0..23 && m in 0..59) {
                return (h * 60 + m).toDouble()
            }
        }
        return null
    }

    // 2. Formatted 24h time ("14:30", "14h30", "14.30", "14,30", "14h")
    val match = timeInputRegex.find(trimmed) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minStr = match.groupValues[2]
    val minute = if (minStr.isEmpty()) {
        0
    } else if (minStr.length == 1 && (trimmed.contains('.') || trimmed.contains(','))) {
        // e.g. "14.3" while typing "14.30" -> 30 mins
        (minStr.toIntOrNull() ?: return null) * 10
    } else {
        minStr.toIntOrNull() ?: return null
    }

    if (hour !in 0..23 || minute !in 0..59) return null
    return (hour * 60 + minute).toDouble()
}

private fun currentMinutesInZone(converterLabel: String): Double {
    val tzId = WorldClockProvider.findByConverterLabel(converterLabel)?.timeZoneId ?: "Europe/Paris"
    val cal = Calendar.getInstance(TimeZone.getTimeZone(tzId))
    return (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)).toDouble()
}

private fun formatForType(value: Double, type: ConversionType): String {
    return when (type) {
        ConversionType.CURRENCY -> String.format(Locale.US, "%.2f", value)
        ConversionType.TIME -> {
            val totalMins = ((value.roundToInt() % 1440) + 1440) % 1440
            val hours24 = totalMins / 60
            val mins = totalMins % 60
            String.format(Locale.US, "%02d:%02d", hours24, mins)
        }
        else -> formatDouble(value)
    }
}

private fun convertForType(
    value: Double,
    fromUnit: String,
    toUnit: String,
    type: ConversionType,
    rates: Map<String, Double>
): Double {
    val category = type.unitCategory
    return if (category == null) {
        convertCurrency(value, fromUnit, toUnit, rates)
    } else {
        convert(value, fromUnit, toUnit, category)
    }
}

private fun convertCurrency(value: Double, fromCode: String, toCode: String, rates: Map<String, Double>): Double {
    if (fromCode == toCode) return value

    val fromRate = rates[fromCode] ?: 1.0
    val toRate = rates[toCode] ?: 1.0

    val valueInEuro = value / fromRate
    return valueInEuro * toRate
}

private fun formatDouble(value: Double): String {
    return if (value % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
    }
}

private fun convert(value: Double, fromUnit: String, toUnit: String, category: Category): Double {
    if (fromUnit == toUnit) return value

    if (category == Category.TEMPERATURE) {
        return convertTemperature(value, fromUnit, toUnit)
    }
    if (category == Category.TIME) {
        return convertTimeMinutes(value, fromUnit, toUnit)
    }

    // Convert everything to a standard base unit first, then to the target unit
    val baseValue = when (category) {
        Category.WEIGHT -> value * getWeightMultiplierToBase(fromUnit)
        Category.LENGTH -> value * getLengthMultiplierToBase(fromUnit)
        Category.VELOCITY -> value * getVelocityMultiplierToBase(fromUnit)
        Category.VOLUME -> value * getVolumeMultiplierToBase(fromUnit)
        Category.TEMPERATURE, Category.TIME -> value
    }

    return when (category) {
        Category.WEIGHT -> baseValue / getWeightMultiplierToBase(toUnit)
        Category.LENGTH -> baseValue / getLengthMultiplierToBase(toUnit)
        Category.VELOCITY -> baseValue / getVelocityMultiplierToBase(toUnit)
        Category.VOLUME -> baseValue / getVolumeMultiplierToBase(toUnit)
        Category.TEMPERATURE, Category.TIME -> baseValue
    }
}

private fun convertTimeMinutes(minutesSinceMidnight: Double, fromLabel: String, toLabel: String): Double {
    val fromTzId = WorldClockProvider.findByConverterLabel(fromLabel)?.timeZoneId ?: "Europe/Paris"
    val toTzId = WorldClockProvider.findByConverterLabel(toLabel)?.timeZoneId ?: "Australia/Sydney"
    val now = System.currentTimeMillis()
    val diffMinutes = (TimeZone.getTimeZone(toTzId).getOffset(now) - TimeZone.getTimeZone(fromTzId).getOffset(now)) / 60000
    val targetTotal = (minutesSinceMidnight.roundToInt() + diffMinutes)
    return (((targetTotal % 1440) + 1440) % 1440).toDouble()
}

private fun convertTemperature(value: Double, fromUnit: String, toUnit: String): Double {
    // Convert to Celsius first
    val celsius = when (fromUnit) {
        "°C" -> value
        "°F" -> (value - 32.0) * 5.0 / 9.0
        "K" -> value - 273.15
        else -> value
    }
    // Convert from Celsius to target
    return when (toUnit) {
        "°C" -> celsius
        "°F" -> (celsius * 9.0 / 5.0) + 32.0
        "K" -> celsius + 273.15
        else -> celsius
    }
}

// Base is KG
private fun getWeightMultiplierToBase(unit: String): Double = when (unit) {
    "kg" -> 1.0
    "g" -> 0.001
    "lb" -> 0.453592
    "oz" -> 0.0283495
    else -> 1.0
}

// Base is Meters
private fun getLengthMultiplierToBase(unit: String): Double = when (unit) {
    "m" -> 1.0
    "km" -> 1000.0
    "cm" -> 0.01
    "mi" -> 1609.34
    "ft" -> 0.3048
    "in" -> 0.0254
    else -> 1.0
}

// Base is m/s
private fun getVelocityMultiplierToBase(unit: String): Double = when (unit) {
    "m/s" -> 1.0
    "km/h" -> 0.277778
    "mph" -> 0.44704
    else -> 1.0
}

private fun getVolumeMultiplierToBase(unit: String): Double = when (unit) {
    "l" -> 1.0
    "ml" -> 0.001
    "gal" -> 3.78541
    "qt" -> 0.946353
    "pt" -> 0.473176
    "cup" -> 0.236588
    else -> 1.0
}
