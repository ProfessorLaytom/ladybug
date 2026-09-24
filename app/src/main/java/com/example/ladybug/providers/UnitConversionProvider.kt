package com.example.ladybug.providers

import com.example.ladybug.model.SearchResult
import com.example.ladybug.model.SearchResult.UnitConversion.Category

class UnitConversionProvider : SearchProvider {

    // Regex looks for a number (decimals allowed), optional space, and letters/symbols
    // Example matches: "5lbs", "10.5 kg", "100 km/h"
    private val unitRegex = Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*([a-zA-Z/]+)$")

    override suspend fun performSearch(query: String): SearchResult {
        val cleanQuery = query.trim().lowercase()

        return try {
            val match = unitRegex.find(cleanQuery) ?: return SearchResult.NoMatch

            val value = match.groupValues[1].toDoubleOrNull() ?: return SearchResult.NoMatch
            val rawUnit = match.groupValues[2]

            // 1. Identify the input unit and its category
            val (fromUnit, category) = identifyUnitAndCategory(rawUnit) ?: return SearchResult.NoMatch

            // 2. Determine the default "Target" unit based on the category
            val toUnit = when (category) {
                Category.WEIGHT -> if (fromUnit == "kg") "lb" else "kg"
                Category.LENGTH -> if (fromUnit == "m") "ft" else "m"
                Category.VELOCITY -> if (fromUnit == "km/h") "m/s" else "km/h"
                Category.VOLUME -> if (fromUnit == "l") "gal" else "l"
            }

            SearchResult.UnitConversion(
                initialValue = value,
                initialFromUnit = fromUnit,
                initialToUnit = toUnit,
                category = category
            )
        } catch (e: Exception) {
            // Failsafe: If anything goes wrong, ignore and don't crash the app
            SearchResult.NoMatch
        }
    }

    // Helper to map user slang to standard unit keys
    private fun identifyUnitAndCategory(unit: String): Pair<String, Category>? {
        return when (unit) {
            "kg", "kilo", "kilograms" -> Pair("kg", Category.WEIGHT)
            "lb", "lbs", "pounds", "pound" -> Pair("lb", Category.WEIGHT)
            "g", "grams", "gram" -> Pair("g", Category.WEIGHT)
            "oz", "ounce", "ounces" -> Pair("oz", Category.WEIGHT)

            "m", "meter", "meters" -> Pair("m", Category.LENGTH)
            "km", "kilometers" -> Pair("km", Category.LENGTH)
            "cm", "centimeters" -> Pair("cm", Category.LENGTH)
            "in", "inch", "inches" -> Pair("in", Category.LENGTH)
            "ft", "foot", "feet" -> Pair("ft", Category.LENGTH)
            "mi", "mile", "miles" -> Pair("mi", Category.LENGTH)

            "kmh", "km/h" -> Pair("km/h", Category.VELOCITY)
            "mph" -> Pair("mph", Category.VELOCITY)
            "m/s", "ms" -> Pair("m/s", Category.VELOCITY)

            "l", "liter", "liters", "litre" -> Pair("l", Category.VOLUME)
            "ml", "milliliter", "milliliters" -> Pair("ml", Category.VOLUME)
            "gal", "gallon", "gallons" -> Pair("gal", Category.VOLUME)
            "qt", "quart", "quarts" -> Pair("qt", Category.VOLUME)
            "pt", "pint", "pints" -> Pair("pt", Category.VOLUME)
            "cup", "cups" -> Pair("cup", Category.VOLUME)

            else -> null
        }
    }
}