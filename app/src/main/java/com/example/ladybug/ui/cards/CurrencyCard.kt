package com.example.ladybug.ui.cards

import androidx.compose.runtime.Composable
import com.example.ladybug.model.SearchResult

@Composable
fun CurrencyCard(result: SearchResult.Currency) {
    UnifiedConversionCard(
        initialType = ConversionType.CURRENCY,
        initialValue = result.initialValue,
        initialFromUnit = result.initialFromCurrency,
        initialToUnit = result.initialToCurrency,
        initialRates = result.rates
    )
}
