package com.example.ladybug.providers

import com.example.ladybug.model.SearchResult
import com.example.ladybug.model.SearchResult.UnitConversion.Category

class UnitConversionProvider : SearchProvider {

    // Matches optional negative sign, number (dot or comma decimals), optional space, and unit letters/symbols
    // Example matches: "5lbs", "10,5 kg", "100 km/h", "-5 °c", "72f"
    private val unitRegex = Regex("^(-?[0-9]+(?:[.,][0-9]+)?)\\s*([a-zA-Z/°]+)$")

    // Matches 24h time conversion queries (0..23 with 'h' or ':mm'), e.g.:
    // "14h us", "14:30 tokyo", "14h30 paris to tokyo", "0h fr to au", "18h to sydney"
    private val timeConversionRegex = Regex(
        "^(?i)([01]?[0-9]|2[0-3])(?:\\s*h\\s*([0-5][0-9])?|\\s*:\\s*([0-5][0-9]))\\s*(.+)$"
    )
    private val targetSplitRegex = Regex("(?i)\\s+(?:to|in|en|vers|->|a|à)\\s+")
    private val leadingToRegex = Regex("^(?i)(?:to|in|en|vers|->|a|à)\\s+(.+)$")
    private val leadingPrepRegex = Regex("^(?i)(?:in|at|a|à|de|du|en|au|aux)\\s+(.+)$")

    override suspend fun performSearch(query: String): SearchResult {
        val cleanQuery = query.trim().lowercase()

        if (cleanQuery == "conv" || cleanQuery == "conversion") {
            return SearchResult.ConversionOptions
        }

        // 1. Check 24h Time Conversion syntax first (e.g. "14h us", "14:30 fr to jp")
        parseTimeConversionQuery(cleanQuery)?.let { return it }

        return try {
            val match = unitRegex.find(cleanQuery) ?: return SearchResult.NoMatch

            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                ?: return SearchResult.NoMatch
            val rawUnit = match.groupValues[2]

            // 2. Identify the input unit and its category
            val (fromUnit, category) = identifyUnitAndCategory(rawUnit) ?: return SearchResult.NoMatch

            // Disallow negative values for physical quantities other than temperature
            if (value < 0 && category != Category.TEMPERATURE) {
                return SearchResult.NoMatch
            }

            // 3. Determine the default "Target" unit based on the category
            val toUnit = when (category) {
                Category.WEIGHT -> if (fromUnit == "kg") "lb" else "kg"
                Category.LENGTH -> if (fromUnit == "m") "ft" else "m"
                Category.VELOCITY -> if (fromUnit == "km/h") "m/s" else "km/h"
                Category.VOLUME -> if (fromUnit == "l") "gal" else "l"
                Category.TEMPERATURE -> if (fromUnit == "°C") "°F" else "°C"
                Category.TIME -> fromUnit
            }

            SearchResult.UnitConversion(
                initialValue = value,
                initialFromUnit = fromUnit,
                initialToUnit = toUnit,
                category = category
            )
        } catch (_: Exception) {
            SearchResult.NoMatch
        }
    }

    private fun parseTimeConversionQuery(cleanQuery: String): SearchResult.UnitConversion? {
        val match = timeConversionRegex.find(cleanQuery) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        if (hour !in 0..23) return null

        val minuteStr = match.groupValues[2].ifEmpty { match.groupValues[3] }
        val minute = if (minuteStr.isEmpty()) 0 else (minuteStr.toIntOrNull() ?: return null)
        if (minute !in 0..59) return null

        val remainder = match.groupValues[4].trim()
        if (remainder.isEmpty()) return null

        val defaultParis = WorldClockProvider.resolveLocation("fr")?.first ?: return null
        val defaultCanberra = WorldClockProvider.resolveLocation("au")?.first ?: return null

        // Case A: "14h to tokyo" (implicit source = Paris / France)
        val leadingToMatch = leadingToRegex.find(remainder)
        if (leadingToMatch != null) {
            val targetPlace = leadingToMatch.groupValues[1].trim()
            val toLocation = WorldClockProvider.resolveLocation(targetPlace)?.first ?: return null
            val fromLocation = if (toLocation.id == defaultParis.id) defaultCanberra else defaultParis
            return SearchResult.UnitConversion(
                initialValue = (hour * 60 + minute).toDouble(),
                initialFromUnit = fromLocation.converterLabel,
                initialToUnit = toLocation.converterLabel,
                category = Category.TIME
            )
        }

        // Case B: "14h paris to tokyo" or "14h us"
        val parts = remainder.split(targetSplitRegex, limit = 2)
        val rawFrom = leadingPrepRegex.find(parts[0].trim())?.groupValues?.get(1) ?: parts[0].trim()
        val fromLocation = WorldClockProvider.resolveLocation(rawFrom)?.first ?: return null

        val toLocation = if (parts.size > 1 && parts[1].isNotBlank()) {
            WorldClockProvider.resolveLocation(parts[1].trim())?.first ?: return null
        } else {
            if (fromLocation.id == defaultParis.id) defaultCanberra else defaultParis
        }

        return SearchResult.UnitConversion(
            initialValue = (hour * 60 + minute).toDouble(),
            initialFromUnit = fromLocation.converterLabel,
            initialToUnit = toLocation.converterLabel,
            category = Category.TIME
        )
    }

    // Helper to map user slang to standard unit keys
    private fun identifyUnitAndCategory(unit: String): Pair<String, Category>? {
        return when (unit) {
            "kg", "kilo", "kilograms", "kilogram" -> Pair("kg", Category.WEIGHT)
            "lb", "lbs", "pounds", "pound" -> Pair("lb", Category.WEIGHT)
            "g", "grams", "gram" -> Pair("g", Category.WEIGHT)
            "oz", "ounce", "ounces" -> Pair("oz", Category.WEIGHT)

            "m", "meter", "meters", "metre", "metres" -> Pair("m", Category.LENGTH)
            "km", "kilometers", "kilometer", "kilometre", "kilometres" -> Pair("km", Category.LENGTH)
            "cm", "centimeters", "centimeter", "centimetre", "centimetres" -> Pair("cm", Category.LENGTH)
            "in", "inch", "inches" -> Pair("in", Category.LENGTH)
            "ft", "foot", "feet" -> Pair("ft", Category.LENGTH)
            "mi", "mile", "miles" -> Pair("mi", Category.LENGTH)

            "kmh", "km/h" -> Pair("km/h", Category.VELOCITY)
            "mph" -> Pair("mph", Category.VELOCITY)
            "m/s", "ms" -> Pair("m/s", Category.VELOCITY)

            "l", "liter", "liters", "litre", "litres" -> Pair("l", Category.VOLUME)
            "ml", "milliliter", "milliliters", "millilitre", "millilitres" -> Pair("ml", Category.VOLUME)
            "gal", "gallon", "gallons" -> Pair("gal", Category.VOLUME)
            "qt", "quart", "quarts" -> Pair("qt", Category.VOLUME)
            "pt", "pint", "pints" -> Pair("pt", Category.VOLUME)
            "cup", "cups" -> Pair("cup", Category.VOLUME)

            "c", "°c", "celsius" -> Pair("°C", Category.TEMPERATURE)
            "f", "°f", "fahrenheit" -> Pair("°F", Category.TEMPERATURE)
            "k", "kelvin" -> Pair("K", Category.TEMPERATURE)

            else -> null
        }
    }
}
