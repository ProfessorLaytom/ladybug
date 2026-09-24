package com.example.ladybug.ui.cards

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key // THE FIX: Key import for smooth list updates
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ladybug.R // Ensure this matches your package
import com.example.ladybug.model.SearchResult

@Composable
fun CocktailRecipeCard(result: SearchResult.CocktailRecipe) {
    val cocktail = result.cocktail

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Section: Left (Name/Ingredients) & Right (Glass/Garnish)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Side
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cocktail.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Ingredients List
                    cocktail.ingredients.forEach { ingredient ->
                        Text(
                            text = "• ${ingredient.direction}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Right Side
                Column(
                    modifier = Modifier
                        .width(100.dp)
                        .align(Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Glass Icon
                    Image(
                        painter = painterResource(id = getGlassIcon(cocktail.glass)),
                        contentDescription = cocktail.glass,
                        modifier = Modifier.size(64.dp)
                    )

                    Text(
                        text = cocktail.glass,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    // Garnish (if any)
                    if (!cocktail.garnish.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = cocktail.garnish,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Section: Method
            Text(
                text = "Method",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = cocktail.method,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun CocktailSuggestionsCard(
    result: SearchResult.CocktailSuggestions,
    onSuggestionSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "Did you mean...",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            result.suggestions.forEach { suggestion ->
                // THE FIX: Key tells Compose exactly which element this is so it doesn't redraw the whole list
                key(suggestion) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestionSelected(suggestion) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

// THE NEW CARD: Catching Typos
@Composable
fun CocktailNotFoundCard(result: SearchResult.CocktailNotFound) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            // Uses standard red error colors from Material 3
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No cocktail found for '${result.query}'",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Check your spelling or try another classic!",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Helper function mapping strings to your drawable resources.
private fun getGlassIcon(glassName: String): Int {
    return when (glassName) {
        "Cocktail Glass" -> R.drawable.ic_glass_cocktail
        "Highball Glass" -> R.drawable.ic_glass_highball
        "Old Fashioned Glass" -> R.drawable.ic_glass_old_fashioned
        "Tall Tumbler Glass" -> R.drawable.ic_glass_tall_tumbler
        "Rocks Glass" -> R.drawable.ic_glass_rocks
        "Champagne Glass" -> R.drawable.ic_glass_champagne
        "Flute Glass" -> R.drawable.ic_glass_champagne
        "Double Old Fashioned Glass" -> R.drawable.ic_glass_rocks
        "Hurricane Glass" -> R.drawable.ic_glass_hurricane
        "Small Tumbler Glass" -> R.drawable.ic_glass_small_tumbler
        "Irish Coffee Glass" -> R.drawable.ic_glass_irish_coffee
        "Julep Stainless Steel Cup" -> R.drawable.ic_glass_julep
        "Goblet Glass" -> R.drawable.ic_glass_goblet
        "Wine Glass" -> R.drawable.ic_glass_wine
        "Collins Glass" -> R.drawable.ic_glass_highball
        else -> R.drawable.ic_glass_highball // Fallback icon
    }
}