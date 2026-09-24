package com.example.ladybug.ui

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.ladybug.MainViewModel
import com.example.ladybug.R
import com.example.ladybug.model.SearchResult
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue // NEW
import androidx.compose.ui.text.TextRange // NEW
import kotlinx.coroutines.delay
import androidx.core.net.toUri

// Card Imports
import com.example.ladybug.ui.cards.WebResultCard
import com.example.ladybug.ui.cards.AppSearchCard
import com.example.ladybug.ui.cards.CocktailNotFoundCard
import com.example.ladybug.ui.cards.CurrencyCard
import com.example.ladybug.ui.cards.FishCard
import com.example.ladybug.ui.cards.UnitConversionCard
import com.example.ladybug.ui.cards.WeatherCard
import com.example.ladybug.ui.cards.WikipediaCard
// --- NEW COCKTAIL IMPORTS ---
import com.example.ladybug.ui.cards.CocktailRecipeCard
import com.example.ladybug.ui.cards.CocktailSuggestionsCard

/**
 * COMPONENT 1: The Search Bar
 */
@Composable
fun SearchInputBar(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    onKeyboardSearch: (String) -> Unit,
    autoFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // NEW: We manage the TextFieldValue explicitly so we can control the cursor position
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = query, selection = TextRange(query.length)))
    }

    // NEW: When the external query changes (e.g., clicking an autocomplete suggestion),
    // we update the text and force the cursor (selection) to the very end of the new word.
    LaunchedEffect(query) {
        if (query != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = query,
                selection = TextRange(query.length)
            )
        }
    }

    LaunchedEffect(autoFocus) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    OutlinedTextField(
        value = textFieldValue, // We now pass the TextFieldValue instead of the raw String
        onValueChange = { newValue ->
            textFieldValue = newValue
            onQueryChange(newValue.text) // Keep the ViewModel in sync
        },

        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .focusRequester(focusRequester),

        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),

        keyboardActions = KeyboardActions(
            onSearch = {
                onKeyboardSearch(query)
                keyboardController?.hide()
            }
        ),

        placeholder = { Text("salut beauté 😘") },

        leadingIcon = {
            Image(
                painter = painterResource(id = R.drawable.icon_c),
                contentDescription = "Custom Search Icon",
                modifier = Modifier.size(24.dp)
            )
        },

        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    onQueryChange("")
                    textFieldValue = TextFieldValue(text = "", selection = TextRange(0)) // Reset cursor
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_close_24),
                        contentDescription = "Clear text"
                    )
                }
            }
        },

        singleLine = true,
        shape = RoundedCornerShape(percent = 20),

        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

/**
 * COMPONENT 2: The Main Screen Assembly
 */
@Composable
fun MainSearchScreen(
    viewModel: MainViewModel,
    autoFocus: Boolean = false
) {

    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {

        // 3. THE SEARCH BAR
        SearchInputBar(
            query = query,
            onQueryChange = { newText -> viewModel.onQueryChanged(newText) },
            autoFocus = autoFocus,
            onKeyboardSearch = {
                val webCard = results.filterIsInstance<SearchResult.WebLink>().firstOrNull()

                if (webCard != null) {
                    val customTabsIntent = CustomTabsIntent.Builder().build()
                    customTabsIntent.launchUrl(context, webCard.url.toUri())
                }
            }
        )

        // 4. THE RESULTS AREA
        if (results.isEmpty() && query.isNotEmpty()) {
            Text(
                text = "Searching...",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(results) { result ->
                    when (result) {
                        is SearchResult.WebLink -> WebResultCard(result = result)
                        is SearchResult.UnitConversion -> UnitConversionCard(result = result)
                        is SearchResult.AppSearch -> AppSearchCard(result = result)
                        is SearchResult.Wikipedia -> WikipediaCard(result = result)
                        is SearchResult.Currency -> CurrencyCard(result = result)
                        is SearchResult.Weather -> WeatherCard(result = result)
                        is SearchResult.Fish -> FishCard(result)

                        // --- NEW: COCKTAIL GALLERY ROUTES ---
                        is SearchResult.CocktailNotFound -> CocktailNotFoundCard(result)
                        is SearchResult.CocktailRecipe -> CocktailRecipeCard(result)
                        is SearchResult.CocktailSuggestions -> CocktailSuggestionsCard(result) { selectedDrink ->
                            viewModel.onQueryChanged("bar $selectedDrink")

                        }

                        SearchResult.NoMatch -> {}
                    }
                }
            }
        }
    }
}