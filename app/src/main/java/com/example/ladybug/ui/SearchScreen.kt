package com.example.ladybug.ui

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.example.ladybug.MainViewModel
import com.example.ladybug.R
import com.example.ladybug.model.AutocompleteSuggestion
import com.example.ladybug.model.SearchResult
import com.example.ladybug.providers.normalizeForSearch
import com.example.ladybug.searxng.SearxngServerManager
import com.example.ladybug.ui.cards.AppSearchCard
import com.example.ladybug.ui.cards.CocktailNotFoundCard
import com.example.ladybug.ui.cards.CocktailRecipeCard
import com.example.ladybug.ui.cards.CocktailSuggestionsCard
import com.example.ladybug.ui.cards.ConversionOptionsCard
import com.example.ladybug.ui.cards.CurrencyCard
import com.example.ladybug.ui.cards.FishCard
import com.example.ladybug.ui.cards.FishHistoryCard
import com.example.ladybug.ui.cards.SearchHistoryCard
import com.example.ladybug.ui.cards.UnitConversionCard
import com.example.ladybug.ui.cards.WeatherCard
import com.example.ladybug.ui.cards.WebResultCard
import com.example.ladybug.ui.cards.WikipediaCard
import com.example.ladybug.ui.cards.WorldClockCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * COMPONENT 1: The Search Bar (Unified Flush Expandable Box)
 */
@Composable
fun SearchInputBar(
    modifier: Modifier = Modifier,
    query: String,
    suggestions: List<AutocompleteSuggestion> = emptyList(),
    onQueryChange: (String) -> Unit,
    onKeyboardSearch: (String) -> Unit,
    onMagicSuggestionClick: (String) -> Unit = onQueryChange,
    onWebSuggestionClick: (String) -> Unit = {},
    onWebSuggestionFill: (String) -> Unit = onQueryChange,
    autoFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Manage TextFieldValue explicitly so we can control the cursor position
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = query, selection = TextRange(query.length)))
    }

    // When the external query changes (e.g., clicking an autocomplete suggestion),
    // update the text and force the cursor (selection) to the very end of the new word.
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    val textChanged = newValue.text != textFieldValue.text
                    textFieldValue = newValue
                    if (textChanged || newValue.text != query) {
                        onQueryChange(newValue.text)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
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
                            textFieldValue = TextFieldValue(text = "", selection = TextRange(0))
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_close_24),
                                contentDescription = "Clear text"
                            )
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            if (suggestions.isNotEmpty()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    thickness = 1.dp
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    suggestions.take(4).forEach { suggestion ->
                        if (suggestion.isMagicCommand) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onMagicSuggestionClick(suggestion.text) }
                                    .padding(horizontal = 16.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_magic_wand),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = suggestion.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            keyboardController?.hide()
                                            onWebSuggestionClick(suggestion.text)
                                        }
                                        .padding(start = 16.dp, end = 8.dp, top = 11.dp, bottom = 11.dp)
                                ) {
                                    Text(
                                        text = suggestion.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onWebSuggestionFill(suggestion.text) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "↖",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.surface
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
    val autocompleteSuggestions by viewModel.autocompleteSuggestions.collectAsState()
    val selectedSearchEngine by viewModel.selectedSearchEngine.collectAsState()
    val previousBarQuery by viewModel.previousBarQuery.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showHelpDialog by remember { mutableStateOf(false) }
    var showFishHistoryDialog by remember { mutableStateOf(false) }
    var showSearchHistoryDialog by remember { mutableStateOf(false) }
    var discoveredFishList by remember { mutableStateOf<List<SearchResult.Fish>>(emptyList()) }

    val hasActiveMagicCard = remember(results) { viewModel.hasActiveMagicCard(results) }
    val visibleSuggestions = remember(query, hasActiveMagicCard, autocompleteSuggestions) {
        if (query.isNotBlank() && !hasActiveMagicCard) {
            autocompleteSuggestions.take(4)
        } else {
            emptyList()
        }
    }

    val topResultKey = results.firstOrNull()?.let { resultKey(it) }

    // Keep the LazyColumn anchored at the very top (index 0, offset 0) when typing or when a new card pops in
    LaunchedEffect(query, results.size, topResultKey) {
        if (results.isNotEmpty()) {
            listState.scrollToItem(0, 0)
        }
    }

    LaunchedEffect(showFishHistoryDialog) {
        if (showFishHistoryDialog) {
            discoveredFishList = viewModel.getDiscoveredFish()
        }
    }

    fun launchWebLink(link: SearchResult.WebLink) {
        viewModel.recordWebSearch(link)
        if (link.url.startsWith("http://127.0.0.1:8888")) {
            scope.launch {
                SearxngServerManager.awaitReady(context)
                val customTabsIntent = CustomTabsIntent.Builder().build()
                customTabsIntent.launchUrl(context, link.url.toUri())
            }
        } else {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(context, link.url.toUri())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        // 1. THE UNIFIED FLUSH SEARCH BAR + AUTOCOMPLETE
        SearchInputBar(
            query = query,
            suggestions = visibleSuggestions,
            onQueryChange = { newText -> viewModel.onQueryChanged(newText) },
            onMagicSuggestionClick = { command -> viewModel.onQueryChanged(command) },
            onWebSuggestionClick = { suggestion ->
                val link = viewModel.buildWebLink(suggestion)
                launchWebLink(link)
            },
            onWebSuggestionFill = { suggestion -> viewModel.onQueryChanged(suggestion) },
            autoFocus = autoFocus,
            onKeyboardSearch = {
                val topResult = results.firstOrNull()
                val appCard = results.filterIsInstance<SearchResult.AppSearch>().firstOrNull()
                val normQuery = query.trim().normalizeForSearch()
                val singleOrExactApp = appCard?.apps?.firstOrNull()?.takeIf { firstApp ->
                    topResult is SearchResult.AppSearch &&
                        (appCard.apps.size == 1 || firstApp.normalizedName == normQuery)
                }

                if (singleOrExactApp != null) {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(singleOrExactApp.packageName)
                    launchIntent?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(it)
                    }
                } else {
                    val webCard = results.filterIsInstance<SearchResult.WebLink>().firstOrNull()
                    if (webCard != null) {
                        launchWebLink(webCard)
                    }
                }
            }
        )

        // 2. EMPTY SEARCH QUICK ACTIONS (Past Discovered Fish + Help Popup + Search History)
        if (query.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmptyScreenPillButton(
                    iconRes = R.drawable.ic_trevally,
                    contentDescription = "Fishdex",
                    onClick = { showFishHistoryDialog = true },
                    iconModifier = Modifier.size(width = 32.dp, height = 15.5.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                EmptyScreenPillButton(
                    iconRes = R.drawable.ic_magic_wand,
                    contentDescription = "Commandes Magiques",
                    onClick = { showHelpDialog = true }
                )
                Spacer(modifier = Modifier.width(8.dp))
                EmptyScreenPillButton(
                    iconRes = R.drawable.ic_scroll,
                    contentDescription = "Historique",
                    onClick = { showSearchHistoryDialog = true }
                )
            }
        }

        // 3. THE RESULTS AREA
        if (results.isEmpty() && query.isNotEmpty()) {
            Text(
                text = "Searching...",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = results,
                    key = { result -> resultKey(result) },
                    contentType = { it::class.simpleName }
                ) { result ->
                    when (result) {
                        is SearchResult.WebLink -> WebResultCard(
                            result = result,
                            onOpened = { viewModel.recordWebSearch(result) }
                        )
                        is SearchResult.ConversionOptions -> ConversionOptionsCard()
                        is SearchResult.UnitConversion -> UnitConversionCard(result = result)
                        is SearchResult.AppSearch -> AppSearchCard(result = result)
                        is SearchResult.Wikipedia -> WikipediaCard(
                            result = result,
                            onArticleOpened = { article -> viewModel.recordWikipediaArticle(result, article) }
                        )
                        is SearchResult.Currency -> CurrencyCard(result = result)
                        is SearchResult.Weather -> WeatherCard(result = result)
                        is SearchResult.WorldClock -> WorldClockCard(result = result)
                        is SearchResult.Fish -> FishCard(
                            result = result,
                            onDismiss = if (result.isDailyFish) {
                                { viewModel.dismissDailyFish() }
                            } else null
                        )
                        is SearchResult.FishHistory -> FishHistoryCard(result = result)
                        is SearchResult.CocktailNotFound -> CocktailNotFoundCard(result = result)
                        is SearchResult.CocktailRecipe -> {
                            val prevQuery = previousBarQuery
                            val prevLabel = prevQuery
                                ?.removePrefix("bar")
                                ?.removePrefix("BAR")
                                ?.trim()
                            CocktailRecipeCard(
                                result = result,
                                previousSearchLabel = prevLabel,
                                onBackToSuggestions = if (prevQuery != null) {
                                    { viewModel.returnToPreviousBarQuery() }
                                } else null
                            )
                        }
                        is SearchResult.CocktailSuggestions -> CocktailSuggestionsCard(result) { selectedDrink ->
                            viewModel.onCocktailSuggestionSelected(selectedDrink)
                        }
                        SearchResult.NoMatch -> {}
                    }
                }
            }
        }
    }

    // 4. PAST DISCOVERED FISH DIALOG (From Empty Search Button)
    if (showFishHistoryDialog) {
        Dialog(onDismissRequest = { showFishHistoryDialog = false }) {
            FishHistoryCard(
                result = SearchResult.FishHistory(discoveredFish = discoveredFishList),
                onClose = { showFishHistoryDialog = false }
            )
        }
    }

    // 5. HELP / COMMANDS POPUP CARD
    if (showHelpDialog) {
        HelpCommandsDialog(
            onDismiss = { showHelpDialog = false },
            onTryCommand = { exampleQuery ->
                showHelpDialog = false
                viewModel.onQueryChanged(exampleQuery)
            }
        )
    }

    // 6. 📜 SEARCH HISTORY POPUP (web searches, direct URLs & opened Wikipedia articles)
    if (showSearchHistoryDialog) {
        Dialog(onDismissRequest = { showSearchHistoryDialog = false }) {
            SearchHistoryCard(
                entries = searchHistory,
                selectedEngine = selectedSearchEngine,
                onEngineSelected = { engine -> viewModel.selectSearchEngine(engine) },
                onEntryClick = { entry ->
                    // Only place the text back into the search bar; nothing is opened automatically
                    showSearchHistoryDialog = false
                    viewModel.onQueryChanged(entry.searchBarText)
                },
                onClose = { showSearchHistoryDialog = false }
            )
        }
    }
}

private fun resultKey(result: SearchResult): String = when (result) {
    is SearchResult.Fish -> if (result.isDailyFish) "DailyFish" else "Fish_${result.scientificName}"
    is SearchResult.FishHistory -> "FishHistory"
    is SearchResult.CocktailRecipe -> "CocktailRecipe"
    is SearchResult.CocktailSuggestions -> "CocktailSuggestions"
    is SearchResult.CocktailNotFound -> "CocktailNotFound"
    is SearchResult.ConversionOptions -> "ConversionOptions"
    is SearchResult.WebLink -> "WebLink"
    is SearchResult.Weather -> "Weather"
    is SearchResult.WorldClock -> "WorldClock_${result.singleLocation?.id ?: "Favorites"}"
    is SearchResult.Currency -> "Currency"
    is SearchResult.UnitConversion -> "UnitConversion"
    is SearchResult.Wikipedia -> "Wikipedia"
    is SearchResult.AppSearch -> "AppSearch"
    SearchResult.NoMatch -> "NoMatch"
}

// ============================================================================
// EMPTY SEARCH QUICK ACTION BUTTONS CONFIGURATION
// ============================================================================
private val PILL_CONTAINER_WIDTH = 50.dp
private val PILL_CONTAINER_HEIGHT = 36.dp

@Composable
private fun EmptyScreenPillButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    iconModifier: Modifier = Modifier.size(22.dp)
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(width = PILL_CONTAINER_WIDTH, height = PILL_CONTAINER_HEIGHT),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.surface,
                modifier = iconModifier
            )
        }
    }
}

@Composable
private fun HelpCommandsDialog(
    onDismiss: () -> Unit,
    onTryCommand: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_magic_wand),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Commandes Magiques",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_close_24),
                            contentDescription = "Close help",
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                HelpSection(
                    title = "🌍 Horloge Mondiale (24h)",
                    examples = listOf("heure", "time us", "heure tokyo", "time au", "time france", "time paris"),
                    onExampleClick = onTryCommand
                )

                HelpSection(
                    title = "🕒 Convertisseur d'Heure (24h)",
                    examples = listOf("14h us", "14h to tokyo", "14h fr to au", "14h30 tokyo", "0h fr to au"),
                    onExampleClick = onTryCommand
                )

                HelpSection(
                    title = "📏 Convertisseur (Unités, Devises, 'conv')",
                    examples = listOf("conv", "5 kg", "100 km/h", "72f", "20c", "10 l", "5m"),
                    onExampleClick = onTryCommand
                )

                HelpSection(
                    title = "💱 Convertisseur de Devises",
                    examples = listOf("100 usd", "50€", "$25", "100₪", "50 aud"),
                    onExampleClick = onTryCommand
                )

                HelpSection(
                    title = "🍹 Bar à Cocktails",
                    examples = listOf("bar", "bar mojito", "bar gin", "bar prosecco"),
                    onExampleClick = onTryCommand
                )

                HelpSection(
                    title = "⛅ Météo (7 jours & par heure)",
                    examples = listOf("météo", "weather paris", "meteo tokyo"),
                    onExampleClick = onTryCommand
                )

                HelpSection(
                    title = "🐟 Poissons & Collection",
                    examples = listOf("fish pls", "my fish", "fishdex", "mes poissons", "daily fish"),
                    onExampleClick = onTryCommand
                )

                HelpSection(
                    title = "📖 Wikipédia (EN & FR)",
                    examples = listOf("loutre", "marie curie fr", "saturne fr"),
                    onExampleClick = onTryCommand
                )

                HelpSection(
                    title = "📱 Applications & Web",
                    examples = listOf("www.wikipedia.org"),
                    onExampleClick = onTryCommand
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HelpSection(
    title: String,
    examples: List<String>,
    onExampleClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            examples.forEach { example ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onExampleClick(example) }
                ) {
                    Text(
                        text = example,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
