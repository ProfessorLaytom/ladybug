package com.example.ladybug.ui.cards

import androidx.compose.foundation.background
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
import java.util.Locale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyCard(result: SearchResult.Currency) {
    var fromCurrency by remember { mutableStateOf(result.initialFromCurrency) }
    var toCurrency by remember { mutableStateOf(result.initialToCurrency) }

    var fromValueText by remember { mutableStateOf(formatCurrency(result.initialValue)) }
    var toValueText by remember {
        mutableStateOf(formatCurrency(convertCurrency(result.initialValue, fromCurrency, toCurrency, result.rates)))
    }

    // ---------------------------------------------------------
    // THE SMART SORTING LOGIC
    // ---------------------------------------------------------
    val availableCurrencies = remember(result.rates) {
        val priorityCodes = listOf("EUR", "AUD", "JPY", "CNY", "USD", "GBP", "ILS")

        // 1. Get all codes that are in our priority list AND exist in the API data
        val priority = priorityCodes.filter { result.rates.containsKey(it) }

        // 2. Get everything else and sort it alphabetically
        val others = result.rates.keys
            .filter { !priorityCodes.contains(it) }
            .sorted()

        // 3. Combine them: Priority first, then the rest
        priority + others
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
                text = "Live Exchange Rates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // LEFT SIDE (Input)
                CurrencyInput(
                    modifier = Modifier.weight(1f),
                    value = fromValueText,
                    onValueChange = { newValue ->
                        fromValueText = newValue
                        val parsed = newValue.toDoubleOrNull()
                        toValueText = if (parsed != null) {
                            formatCurrency(convertCurrency(parsed, fromCurrency, toCurrency, result.rates))
                        } else {
                            ""
                        }
                    },
                    selectedCurrency = fromCurrency,
                    currencies = availableCurrencies,
                    onCurrencyChange = { newCurrency ->
                        fromCurrency = newCurrency
                        val parsed = fromValueText.toDoubleOrNull()
                        if (parsed != null) {
                            toValueText = formatCurrency(convertCurrency(parsed, fromCurrency, toCurrency, result.rates))
                        }
                    }
                )

                Text("=", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)

                // RIGHT SIDE (Output / Reverse Input)
                CurrencyInput(
                    modifier = Modifier.weight(1f),
                    value = toValueText,
                    onValueChange = { newValue ->
                        toValueText = newValue
                        val parsed = newValue.toDoubleOrNull()
                        fromValueText = if (parsed != null) {
                            formatCurrency(convertCurrency(parsed, toCurrency, fromCurrency, result.rates))
                        } else {
                            ""
                        }
                    },
                    selectedCurrency = toCurrency,
                    currencies = availableCurrencies,
                    onCurrencyChange = { newCurrency ->
                        toCurrency = newCurrency
                        val parsed = fromValueText.toDoubleOrNull()
                        if (parsed != null) {
                            toValueText = formatCurrency(convertCurrency(parsed, fromCurrency, toCurrency, result.rates))
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyInput(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    selectedCurrency: String,
    currencies: List<String>,
    onCurrencyChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // 1. The Number Input Field (Remains unchanged)
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

        // 2. The Dropdown Anchor
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            OutlinedTextField(
                value = selectedCurrency,
                onValueChange = {},
                readOnly = true, // We keep it strictly read-only as you requested!
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, // Set to NotEditable
                        enabled = true
                    )
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            // 3. THE HIGH-PERFORMANCE CUSTOM LAZY DROPDOWN
            if (expanded) {
                Popup(
                    alignment = Alignment.TopStart,
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    Surface(
                        modifier = Modifier
                            // This brilliant modifier forces our custom popup to be the exact
                            // same width as the text field it is attached to!
                            .exposedDropdownSize()
                            .padding(top = 4.dp), // A tiny gap below the text field
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 4.dp,
                        tonalElevation = 3.dp
                    ) {
                        // The LazyColumn completely destroys the lag.
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 250.dp) // Limits height so the menu doesn't take up the whole screen
                        ) {
                            items(currencies) { currency ->
                                DropdownMenuItem(
                                    text = { Text(currency) },
                                    onClick = {
                                        onCurrencyChange(currency)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- CURRENCY MATH ENGINE ---

private fun formatCurrency(value: Double): String {
    // Standard currency formatting (always show 2 decimal places)
    return String.format(Locale.US, "%.2f", value)
}

private fun convertCurrency(value: Double, fromCode: String, toCode: String, rates: Map<String, Double>): Double {
    if (fromCode == toCode) return value

    val fromRate = rates[fromCode] ?: 1.0
    val toRate = rates[toCode] ?: 1.0

    // The API uses EUR as the base (1.0).
    // We convert the input to EUR first, then multiply by the target rate.
    val valueInEuro = value / fromRate
    return valueInEuro * toRate
}