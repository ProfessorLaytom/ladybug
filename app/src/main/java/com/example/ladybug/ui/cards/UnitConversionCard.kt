package com.example.ladybug.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ladybug.model.SearchResult
import com.example.ladybug.model.SearchResult.UnitConversion.Category
import java.util.Locale
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitConversionCard(result: SearchResult.UnitConversion) {
    // 1. LOCAL STATE
    // We initialize the state with the data from the ViewModel, but allow the user to change it locally
    var fromUnit by remember { mutableStateOf(result.initialFromUnit) }
    var toUnit by remember { mutableStateOf(result.initialToUnit) }

    var fromValueText by remember { mutableStateOf(formatDouble(result.initialValue)) }
    var toValueText by remember {
        mutableStateOf(formatDouble(convert(result.initialValue, fromUnit, toUnit, result.category)))
    }

    // Helper lists for the dropdowns
    val availableUnits = when (result.category) {
        Category.WEIGHT -> listOf("kg", "lb", "g", "oz")
        Category.LENGTH -> listOf("m", "km", "cm", "in", "ft", "mi")
        Category.VELOCITY -> listOf("km/h", "mph", "m/s")
        Category.VOLUME -> listOf("l", "ml", "gal", "qt", "pt", "cup")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Unit Converter",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                    onValueChange = { newValue ->
                        fromValueText = newValue
                        val parsed = newValue.toDoubleOrNull()
                        toValueText = if (parsed != null) {
                            formatDouble(convert(parsed, fromUnit, toUnit, result.category))
                        } else {
                            ""
                        }
                    },
                    selectedUnit = fromUnit,
                    units = availableUnits,
                    onUnitChange = { newUnit ->
                        fromUnit = newUnit
                        val parsed = fromValueText.toDoubleOrNull()
                        if (parsed != null) {
                            toValueText = formatDouble(convert(parsed, fromUnit, toUnit, result.category))
                        }
                    }
                )

                Text("=", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)

                // RIGHT SIDE (Output) - acts dynamically as input too!
                ConversionInput(
                    modifier = Modifier.weight(1f),
                    value = toValueText,
                    onValueChange = { newValue ->
                        toValueText = newValue
                        val parsed = newValue.toDoubleOrNull()
                        fromValueText = if (parsed != null) {
                            // Reverse the conversion direction!
                            formatDouble(convert(parsed, toUnit, fromUnit, result.category))
                        } else {
                            ""
                        }
                    },
                    selectedUnit = toUnit,
                    units = availableUnits,
                    onUnitChange = { newUnit ->
                        toUnit = newUnit
                        val parsed = fromValueText.toDoubleOrNull()
                        if (parsed != null) {
                            toValueText = formatDouble(convert(parsed, fromUnit, toUnit, result.category))
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
    onValueChange: (String) -> Unit,
    selectedUnit: String,
    units: List<String>,
    onUnitChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // The Number Input Field
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
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
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                // ---------------------------------------------------------
                // THE FIX IS HERE
                // We now explicitly define the type of dropdown anchor this is.
                // ---------------------------------------------------------
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
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                units.forEach { unit ->
                    DropdownMenuItem(
                        text = { Text(unit) },
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

// --- CONVERSION MATH ENGINE ---

private fun formatDouble(value: Double): String {
    // Avoids showing "5.0", shows "5" instead, but keeps decimals if needed (e.g., "5.5")
    return if (value % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
    }
}

private fun convert(value: Double, fromUnit: String, toUnit: String, category: Category): Double {
    if (fromUnit == toUnit) return value

    // Convert everything to a standard base unit first, then to the target unit
    val baseValue = when (category) {
        Category.WEIGHT -> value * getWeightMultiplierToBase(fromUnit)
        Category.LENGTH -> value * getLengthMultiplierToBase(fromUnit)
        Category.VELOCITY -> value * getVelocityMultiplierToBase(fromUnit)
        Category.VOLUME -> value * getVolumeMultiplierToBase(fromUnit)
    }

    return when (category) {
        Category.WEIGHT -> baseValue / getWeightMultiplierToBase(toUnit)
        Category.LENGTH -> baseValue / getLengthMultiplierToBase(toUnit)
        Category.VELOCITY -> baseValue / getVelocityMultiplierToBase(toUnit)
        Category.VOLUME -> baseValue / getVolumeMultiplierToBase(toUnit)
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