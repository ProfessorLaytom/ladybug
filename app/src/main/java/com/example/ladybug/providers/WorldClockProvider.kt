package com.example.ladybug.providers

import android.content.Context
import androidx.core.content.edit
import com.example.ladybug.model.SearchResult
import com.example.ladybug.model.WorldLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class WorldClockProvider(context: Context) : SearchProvider {

    init {
        ensureFavoritesLoaded(context)
    }

    // Matches bare "heure", "heures", "time", "clock", "horloge" or "<trigger> <place>"
    private val clockRegex = Regex("^(?i)(?:heure|heures|time|clock|horloge)(?:\\s+(?:in|a|à|at|de|du|en|au|aux)\\s+|\\s+)?(.*)$")

    override suspend fun performSearch(query: String): SearchResult {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 4) return SearchResult.NoMatch

        val match = clockRegex.find(cleanQuery) ?: return SearchResult.NoMatch
        val placeQuery = match.groupValues[1].trim()

        // 1. Bare "heure" / "time" / "clock" -> show ❤️ Favorite cities/capitals card
        if (placeQuery.isEmpty()) {
            return SearchResult.WorldClock(singleLocation = null, matchedByCountry = false)
        }

        // 2. Specific city, country, or country shorthand (e.g. "time us", "heure tokyo", "time france")
        val (location, matchedByCountry) = resolveLocation(placeQuery) ?: return SearchResult.NoMatch
        return SearchResult.WorldClock(
            singleLocation = location,
            matchedByCountry = matchedByCountry
        )
    }

    data class ClockSnapshot(
        val time24h: String,
        val isDaytime: Boolean,
        val dayEmoji: String,
        val relativeDayLabel: String,
        val dateLabel: String,
        val offsetLabel: String,
        val subtitle: String
    )

    private data class CatalogEntry(
        val location: WorldLocation,
        val cityAliases: List<String>,
        val countryAliases: List<String> = emptyList()
    )

    companion object {
        private const val PREFS_NAME = "WorldClockPrefs"
        private const val KEY_FAVORITES = "favorite_location_ids"
        private const val KEY_INITIALIZED_DEFAULTS = "initialized_defaults"

        val DEFAULT_FAVORITE_IDS = listOf(
            "FR_PARIS",
            "AU_SYDNEY",
            "JP_TOKYO",
            "CN_BEIJING",
            "US_NEW_YORK",
            "GB_LONDON",
            "IL_JERUSALEM"
        )

        private val _favoritesFlow = MutableStateFlow<Set<String>>(DEFAULT_FAVORITE_IDS.toSet())
        val favoritesFlow: StateFlow<Set<String>> = _favoritesFlow.asStateFlow()
        @Volatile
        private var favoritesInitialized = false

        fun ensureFavoritesLoaded(context: Context) {
            if (!favoritesInitialized) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val hasInit = prefs.getBoolean(KEY_INITIALIZED_DEFAULTS, false)
                if (!hasInit) {
                    val defaults = DEFAULT_FAVORITE_IDS.toSet()
                    prefs.edit {
                        putStringSet(KEY_FAVORITES, defaults)
                        putBoolean(KEY_INITIALIZED_DEFAULTS, true)
                    }
                    _favoritesFlow.value = defaults
                } else {
                    val saved = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()
                    _favoritesFlow.value = saved
                }
                favoritesInitialized = true
            }
        }

        fun toggleFavorite(context: Context, locationId: String): Boolean {
            ensureFavoritesLoaded(context)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = _favoritesFlow.value.toMutableSet()
            val isNowFav = if (current.contains(locationId)) {
                current.remove(locationId)
                false
            } else {
                current.add(locationId)
                true
            }
            prefs.edit { putStringSet(KEY_FAVORITES, current) }
            _favoritesFlow.value = current.toSet()
            return isNowFav
        }

        fun getFavoriteLocations(favoriteIds: Set<String>): List<WorldLocation> {
            val byId = catalog.associateBy { it.location.id }
            // Preserve priority order for default favorites, then append any newly favorited ones
            val orderedIds = DEFAULT_FAVORITE_IDS.filter { favoriteIds.contains(it) } +
                favoriteIds.filter { !DEFAULT_FAVORITE_IDS.contains(it) }
            return orderedIds.mapNotNull { byId[it]?.location }
        }

        /**
         * Computes live 24h time (00:00–23:59, never AM/PM), day/night emoji,
         * relative day (Aujourd'hui / Demain / Hier), and hour offset from phone's local timezone.
         */
        fun computeSnapshot(
            location: WorldLocation,
            nowMillis: Long = System.currentTimeMillis()
        ): ClockSnapshot {
            val targetTz = TimeZone.getTimeZone(location.timeZoneId)
            val localTz = TimeZone.getDefault()

            val targetCal = Calendar.getInstance(targetTz).apply { timeInMillis = nowMillis }
            val localCal = Calendar.getInstance(localTz).apply { timeInMillis = nowMillis }

            val hour24 = targetCal.get(Calendar.HOUR_OF_DAY)
            val minute = targetCal.get(Calendar.MINUTE)
            val time24h = String.format(Locale.US, "%02d:%02d", hour24, minute)

            val isDaytime = hour24 in 6..19
            val dayEmoji = if (isDaytime) "☀️" else "🌙"

            // Compare calendar day (Target vs Phone Local)
            val targetYear = targetCal.get(Calendar.YEAR)
            val localYear = localCal.get(Calendar.YEAR)
            val targetDay = targetCal.get(Calendar.DAY_OF_YEAR)
            val localDay = localCal.get(Calendar.DAY_OF_YEAR)

            val dayShift = when {
                targetYear == localYear -> targetDay - localDay
                targetYear > localYear -> 1
                else -> -1
            }

            val relativeDayLabel = when {
                dayShift > 0 -> "Demain (+1j)"
                dayShift < 0 -> "Hier (-1j)"
                else -> "Aujourd'hui"
            }

            val dateFormatter = SimpleDateFormat("EEE d MMM", Locale.getDefault()).apply {
                timeZone = targetTz
            }
            val dateLabel = dateFormatter.format(Date(nowMillis))

            val diffMinutes = (targetTz.getOffset(nowMillis) - localTz.getOffset(nowMillis)) / 60000
            val offsetLabel = formatOffsetMinutes(diffMinutes)

            val countryDisplay = if (location.countryNameEn.equals(location.countryNameFr, ignoreCase = true)) {
                location.countryNameEn
            } else {
                "${location.countryNameEn} (${location.countryNameFr})"
            }

            val subtitle = if (location.isCapital) {
                "🏛️ Capital of $countryDisplay"
            } else {
                countryDisplay
            }

            return ClockSnapshot(
                time24h = time24h,
                isDaytime = isDaytime,
                dayEmoji = dayEmoji,
                relativeDayLabel = relativeDayLabel,
                dateLabel = dateLabel,
                offsetLabel = offsetLabel,
                subtitle = subtitle
            )
        }

        private fun formatOffsetMinutes(diffMinutes: Int): String {
            if (diffMinutes == 0) return "Même heure (0h)"
            val sign = if (diffMinutes > 0) "+" else "-"
            val absMins = abs(diffMinutes)
            val hours = absMins / 60
            val mins = absMins % 60
            return if (mins == 0) {
                "$sign${hours}h"
            } else {
                String.format(Locale.US, "%s%dh%02d", sign, hours, mins)
            }
        }

        /**
         * Resolves a city name, country name (EN/FR), or country shorthand (e.g. "us", "usa", "fr", "au", "uk")
         * to a WorldLocation and a flag indicating whether it was matched via a country query.
         * Country queries always resolve to the capital of that country.
         */
        fun resolveLocation(rawQuery: String): Pair<WorldLocation, Boolean>? {
            val norm = rawQuery.trim().normalizeForSearch()
            if (norm.isEmpty()) return null

            // 1. Exact match on country alias or country code -> returns that country's capital
            catalog.firstOrNull { entry ->
                entry.location.isCapital && (
                    entry.location.countryCode.lowercase() == norm ||
                        entry.countryAliases.any { it == norm }
                    )
            }?.let { return it.location to true }

            // 2. Exact match on city alias
            catalog.firstOrNull { entry ->
                entry.cityAliases.any { it == norm }
            }?.let { return it.location to false }

            // 3. Prefix match on city alias or country alias (for 3+ chars)
            if (norm.length >= 3) {
                catalog.firstOrNull { entry ->
                    entry.location.isCapital && entry.countryAliases.any { it.startsWith(norm) }
                }?.let { return it.location to true }

                catalog.firstOrNull { entry ->
                    entry.cityAliases.any { it.startsWith(norm) }
                }?.let { return it.location to false }
            }

            // 4. Fallback: search standard IANA TimeZone IDs (e.g. "denver" -> "America/Denver")
            if (norm.length >= 3) {
                val matchedZoneId = TimeZone.getAvailableIDs().firstOrNull { tzId ->
                    val cityPart = tzId.substringAfterLast('/').replace('_', ' ').normalizeForSearch()
                    cityPart == norm || cityPart.startsWith(norm)
                }
                if (matchedZoneId != null) {
                    val cityDisplay = matchedZoneId.substringAfterLast('/').replace('_', ' ')
                    val regionDisplay = matchedZoneId.substringBefore('/')
                    return WorldLocation(
                        id = "IANA_$matchedZoneId",
                        cityName = cityDisplay,
                        countryNameEn = regionDisplay,
                        countryNameFr = regionDisplay,
                        countryCode = "🌐",
                        timeZoneId = matchedZoneId,
                        isCapital = false
                    ) to false
                }
            }

            return null
        }

        /**
         * Returns the ordered list of locations for the UnifiedConversionCard dropdown ("conv" -> Time).
         * Prioritizes capitals matching the app's currency priority list first, followed by all other capitals & major cities.
         */
        fun getConverterLocations(): List<WorldLocation> {
            val priorityIds = listOf(
                "FR_PARIS",
                "AU_CANBERRA",
                "AU_SYDNEY",
                "JP_TOKYO",
                "CN_BEIJING",
                "US_WASHINGTON",
                "US_NEW_YORK",
                "GB_LONDON",
                "IL_JERUSALEM"
            )
            val byId = catalog.associateBy { it.location.id }
            val priority = priorityIds.mapNotNull { byId[it]?.location }
            val remainingCapitals = catalog
                .map { it.location }
                .filter { it.isCapital && !priorityIds.contains(it.id) }
                .sortedBy { it.countryNameEn }
            val remainingCities = catalog
                .map { it.location }
                .filter { !it.isCapital && !priorityIds.contains(it.id) }
                .sortedBy { it.cityName }
            return priority + remainingCapitals + remainingCities
        }

        fun findByConverterLabel(label: String): WorldLocation? {
            return catalog.firstOrNull { it.location.converterLabel == label }?.location
        }

        // ====================================================================
        // CURATED WORLD CAPITALS & MAJOR CITIES CATALOG (EN & FR + SHORTHANDS)
        // ====================================================================
        private val catalog: List<CatalogEntry> = listOf(
            // --- PRIORITY CAPITALS & CITIES ---
            CatalogEntry(
                location = WorldLocation("FR_PARIS", "Paris", "France", "France", "FR", "Europe/Paris", isCapital = true),
                cityAliases = listOf("paris"),
                countryAliases = listOf("fr", "fra", "france", "hexagone")
            ),
            CatalogEntry(
                location = WorldLocation("AU_CANBERRA", "Canberra", "Australia", "Australie", "AU", "Australia/Sydney", isCapital = true),
                cityAliases = listOf("canberra"),
                countryAliases = listOf("au", "aus", "australia", "australie", "oz")
            ),
            CatalogEntry(
                location = WorldLocation("AU_SYDNEY", "Sydney", "Australia", "Australie", "AU", "Australia/Sydney", isCapital = false),
                cityAliases = listOf("sydney", "syd")
            ),
            CatalogEntry(
                location = WorldLocation("JP_TOKYO", "Tokyo", "Japan", "Japon", "JP", "Asia/Tokyo", isCapital = true),
                cityAliases = listOf("tokyo", "tyo"),
                countryAliases = listOf("jp", "jpn", "japan", "japon")
            ),
            CatalogEntry(
                location = WorldLocation("CN_BEIJING", "Beijing", "China", "Chine", "CN", "Asia/Shanghai", isCapital = true),
                cityAliases = listOf("beijing", "pekin", "pek"),
                countryAliases = listOf("cn", "chn", "china", "chine", "prc")
            ),
            CatalogEntry(
                location = WorldLocation("US_WASHINGTON", "Washington, D.C.", "United States", "États-Unis", "US", "America/New_York", isCapital = true),
                cityAliases = listOf("washington", "washington dc", "dc"),
                countryAliases = listOf("us", "usa", "united states", "united states of america", "etats-unis", "etats unis", "amerique", "america")
            ),
            CatalogEntry(
                location = WorldLocation("US_NEW_YORK", "New York", "United States", "États-Unis", "US", "America/New_York", isCapital = false),
                cityAliases = listOf("new york", "nyc", "ny", "manhattan")
            ),
            CatalogEntry(
                location = WorldLocation("GB_LONDON", "London", "United Kingdom", "Royaume-Uni", "GB", "Europe/London", isCapital = true),
                cityAliases = listOf("london", "londres", "lon"),
                countryAliases = listOf("uk", "gb", "gbr", "united kingdom", "royaume-uni", "royaume uni", "angleterre", "england", "great britain", "grande-bretagne", "britain")
            ),
            CatalogEntry(
                location = WorldLocation("IL_JERUSALEM", "Jerusalem", "Israel", "Israël", "IL", "Asia/Jerusalem", isCapital = true),
                cityAliases = listOf("jerusalem", "jlm"),
                countryAliases = listOf("il", "isr", "israel")
            ),
            CatalogEntry(
                location = WorldLocation("IL_TEL_AVIV", "Tel Aviv", "Israel", "Israël", "IL", "Asia/Jerusalem", isCapital = false),
                cityAliases = listOf("tel aviv", "tlv")
            ),

            // --- OTHER CAPITALS BY COUNTRY ---
            CatalogEntry(
                location = WorldLocation("CA_OTTAWA", "Ottawa", "Canada", "Canada", "CA", "America/Toronto", isCapital = true),
                cityAliases = listOf("ottawa"),
                countryAliases = listOf("ca", "can", "canada")
            ),
            CatalogEntry(
                location = WorldLocation("DE_BERLIN", "Berlin", "Germany", "Allemagne", "DE", "Europe/Berlin", isCapital = true),
                cityAliases = listOf("berlin"),
                countryAliases = listOf("de", "deu", "ger", "germany", "allemagne")
            ),
            CatalogEntry(
                location = WorldLocation("ES_MADRID", "Madrid", "Spain", "Espagne", "ES", "Europe/Madrid", isCapital = true),
                cityAliases = listOf("madrid"),
                countryAliases = listOf("es", "esp", "spain", "espagne")
            ),
            CatalogEntry(
                location = WorldLocation("IT_ROME", "Rome", "Italy", "Italie", "IT", "Europe/Rome", isCapital = true),
                cityAliases = listOf("rome", "roma"),
                countryAliases = listOf("it", "ita", "italy", "italie")
            ),
            CatalogEntry(
                location = WorldLocation("CH_BERN", "Bern", "Switzerland", "Suisse", "CH", "Europe/Zurich", isCapital = true),
                cityAliases = listOf("bern", "berne"),
                countryAliases = listOf("ch", "che", "sui", "switzerland", "suisse")
            ),
            CatalogEntry(
                location = WorldLocation("BE_BRUSSELS", "Brussels", "Belgium", "Belgique", "BE", "Europe/Brussels", isCapital = true),
                cityAliases = listOf("brussels", "bruxelles", "bru"),
                countryAliases = listOf("be", "bel", "belgium", "belgique")
            ),
            CatalogEntry(
                location = WorldLocation("NL_AMSTERDAM", "Amsterdam", "Netherlands", "Pays-Bas", "NL", "Europe/Amsterdam", isCapital = true),
                cityAliases = listOf("amsterdam", "ams"),
                countryAliases = listOf("nl", "nld", "netherlands", "pays-bas", "pays bas", "hollande", "holland")
            ),
            CatalogEntry(
                location = WorldLocation("PT_LISBON", "Lisbon", "Portugal", "Portugal", "PT", "Europe/Lisbon", isCapital = true),
                cityAliases = listOf("lisbon", "lisbonne", "lisboa"),
                countryAliases = listOf("pt", "prt", "portugal")
            ),
            CatalogEntry(
                location = WorldLocation("IE_DUBLIN", "Dublin", "Ireland", "Irlande", "IE", "Europe/Dublin", isCapital = true),
                cityAliases = listOf("dublin"),
                countryAliases = listOf("ie", "irl", "ireland", "irlande")
            ),
            CatalogEntry(
                location = WorldLocation("AT_VIENNA", "Vienna", "Austria", "Autriche", "AT", "Europe/Vienna", isCapital = true),
                cityAliases = listOf("vienna", "vienne", "wien"),
                countryAliases = listOf("at", "aut", "austria", "autriche")
            ),
            CatalogEntry(
                location = WorldLocation("GR_ATHENS", "Athens", "Greece", "Grèce", "GR", "Europe/Athens", isCapital = true),
                cityAliases = listOf("athens", "athenes"),
                countryAliases = listOf("gr", "grc", "greece", "grece")
            ),
            CatalogEntry(
                location = WorldLocation("SE_STOCKHOLM", "Stockholm", "Sweden", "Suède", "SE", "Europe/Stockholm", isCapital = true),
                cityAliases = listOf("stockholm"),
                countryAliases = listOf("se", "swe", "sweden", "suede")
            ),
            CatalogEntry(
                location = WorldLocation("NO_OSLO", "Oslo", "Norway", "Norvège", "NO", "Europe/Oslo", isCapital = true),
                cityAliases = listOf("oslo"),
                countryAliases = listOf("no", "nor", "norway", "norvege")
            ),
            CatalogEntry(
                location = WorldLocation("DK_COPENHAGEN", "Copenhagen", "Denmark", "Danemark", "DK", "Europe/Copenhagen", isCapital = true),
                cityAliases = listOf("copenhagen", "copenhague"),
                countryAliases = listOf("dk", "dnk", "denmark", "danemark")
            ),
            CatalogEntry(
                location = WorldLocation("FI_HELSINKI", "Helsinki", "Finland", "Finlande", "FI", "Europe/Helsinki", isCapital = true),
                cityAliases = listOf("helsinki"),
                countryAliases = listOf("fi", "fin", "finland", "finlande")
            ),
            CatalogEntry(
                location = WorldLocation("IS_REYKJAVIK", "Reykjavik", "Iceland", "Islande", "IS", "Atlantic/Reykjavik", isCapital = true),
                cityAliases = listOf("reykjavik"),
                countryAliases = listOf("is", "isl", "iceland", "islande")
            ),
            CatalogEntry(
                location = WorldLocation("PL_WARSAW", "Warsaw", "Poland", "Pologne", "PL", "Europe/Warsaw", isCapital = true),
                cityAliases = listOf("warsaw", "varsovie"),
                countryAliases = listOf("pl", "pol", "poland", "pologne")
            ),
            CatalogEntry(
                location = WorldLocation("CZ_PRAGUE", "Prague", "Czechia", "Tchéquie", "CZ", "Europe/Prague", isCapital = true),
                cityAliases = listOf("prague", "praha"),
                countryAliases = listOf("cz", "cze", "czechia", "czech republic", "tchequie", "republique tcheque")
            ),
            CatalogEntry(
                location = WorldLocation("HU_BUDAPEST", "Budapest", "Hungary", "Hongrie", "HU", "Europe/Budapest", isCapital = true),
                cityAliases = listOf("budapest"),
                countryAliases = listOf("hu", "hun", "hungary", "hongrie")
            ),
            CatalogEntry(
                location = WorldLocation("RO_BUCHAREST", "Bucharest", "Romania", "Roumanie", "RO", "Europe/Bucharest", isCapital = true),
                cityAliases = listOf("bucharest", "bucarest"),
                countryAliases = listOf("ro", "rou", "romania", "roumanie")
            ),
            CatalogEntry(
                location = WorldLocation("UA_KYIV", "Kyiv", "Ukraine", "Ukraine", "UA", "Europe/Kyiv", isCapital = true),
                cityAliases = listOf("kyiv", "kiev"),
                countryAliases = listOf("ua", "ukr", "ukraine")
            ),
            CatalogEntry(
                location = WorldLocation("RU_MOSCOW", "Moscow", "Russia", "Russie", "RU", "Europe/Moscow", isCapital = true),
                cityAliases = listOf("moscow", "moscou"),
                countryAliases = listOf("ru", "rus", "russia", "russie")
            ),
            CatalogEntry(
                location = WorldLocation("TR_ANKARA", "Ankara", "Turkey", "Turquie", "TR", "Europe/Istanbul", isCapital = true),
                cityAliases = listOf("ankara"),
                countryAliases = listOf("tr", "tur", "turkey", "turquie", "turkiye")
            ),
            CatalogEntry(
                location = WorldLocation("IN_NEW_DELHI", "New Delhi", "India", "Inde", "IN", "Asia/Kolkata", isCapital = true),
                cityAliases = listOf("new delhi", "delhi"),
                countryAliases = listOf("in", "ind", "india", "inde")
            ),
            CatalogEntry(
                location = WorldLocation("KR_SEOUL", "Seoul", "South Korea", "Corée du Sud", "KR", "Asia/Seoul", isCapital = true),
                cityAliases = listOf("seoul"),
                countryAliases = listOf("kr", "kor", "south korea", "korea", "coree du sud", "coree")
            ),
            CatalogEntry(
                location = WorldLocation("TW_TAIPEI", "Taipei", "Taiwan", "Taïwan", "TW", "Asia/Taipei", isCapital = true),
                cityAliases = listOf("taipei"),
                countryAliases = listOf("tw", "twn", "taiwan")
            ),
            CatalogEntry(
                location = WorldLocation("TH_BANGKOK", "Bangkok", "Thailand", "Thaïlande", "TH", "Asia/Bangkok", isCapital = true),
                cityAliases = listOf("bangkok", "bkk"),
                countryAliases = listOf("th", "tha", "thailand", "thailande")
            ),
            CatalogEntry(
                location = WorldLocation("VN_HANOI", "Hanoi", "Vietnam", "Viêt Nam", "VN", "Asia/Ho_Chi_Minh", isCapital = true),
                cityAliases = listOf("hanoi"),
                countryAliases = listOf("vn", "vnm", "vietnam", "viet nam")
            ),
            CatalogEntry(
                location = WorldLocation("ID_JAKARTA", "Jakarta", "Indonesia", "Indonésie", "ID", "Asia/Jakarta", isCapital = true),
                cityAliases = listOf("jakarta"),
                countryAliases = listOf("id", "idn", "indonesia", "indonesie")
            ),
            CatalogEntry(
                location = WorldLocation("SG_SINGAPORE", "Singapore", "Singapore", "Singapour", "SG", "Asia/Singapore", isCapital = true),
                cityAliases = listOf("singapore", "singapour", "sg", "sin"),
                countryAliases = listOf("sg", "sgp", "singapore", "singapour")
            ),
            CatalogEntry(
                location = WorldLocation("MY_KUALA_LUMPUR", "Kuala Lumpur", "Malaysia", "Malaisie", "MY", "Asia/Kuala_Lumpur", isCapital = true),
                cityAliases = listOf("kuala lumpur", "kl"),
                countryAliases = listOf("my", "mys", "malaysia", "malaisie")
            ),
            CatalogEntry(
                location = WorldLocation("PH_MANILA", "Manila", "Philippines", "Philippines", "PH", "Asia/Manila", isCapital = true),
                cityAliases = listOf("manila", "manille"),
                countryAliases = listOf("ph", "phl", "philippines")
            ),
            CatalogEntry(
                location = WorldLocation("PK_ISLAMABAD", "Islamabad", "Pakistan", "Pakistan", "PK", "Asia/Karachi", isCapital = true),
                cityAliases = listOf("islamabad"),
                countryAliases = listOf("pk", "pak", "pakistan")
            ),
            CatalogEntry(
                location = WorldLocation("NZ_WELLINGTON", "Wellington", "New Zealand", "Nouvelle-Zélande", "NZ", "Pacific/Auckland", isCapital = true),
                cityAliases = listOf("wellington"),
                countryAliases = listOf("nz", "nzl", "new zealand", "nouvelle-zelande", "nouvelle zelande")
            ),
            CatalogEntry(
                location = WorldLocation("AE_ABU_DHABI", "Abu Dhabi", "United Arab Emirates", "Émirats arabes unis", "AE", "Asia/Dubai", isCapital = true),
                cityAliases = listOf("abu dhabi", "abou dabi"),
                countryAliases = listOf("ae", "uae", "eau", "united arab emirates", "emirats arabes unis", "emirats")
            ),
            CatalogEntry(
                location = WorldLocation("SA_RIYADH", "Riyadh", "Saudi Arabia", "Arabie saoudite", "SA", "Asia/Riyadh", isCapital = true),
                cityAliases = listOf("riyadh", "riyad"),
                countryAliases = listOf("sa", "sau", "ksa", "saudi arabia", "arabie saoudite")
            ),
            CatalogEntry(
                location = WorldLocation("QA_DOHA", "Doha", "Qatar", "Qatar", "QA", "Asia/Qatar", isCapital = true),
                cityAliases = listOf("doha"),
                countryAliases = listOf("qa", "qat", "qatar")
            ),
            CatalogEntry(
                location = WorldLocation("LB_BEIRUT", "Beirut", "Lebanon", "Liban", "LB", "Asia/Beirut", isCapital = true),
                cityAliases = listOf("beirut", "beyrouth"),
                countryAliases = listOf("lb", "lbn", "lebanon", "liban")
            ),
            CatalogEntry(
                location = WorldLocation("EG_CAIRO", "Cairo", "Egypt", "Égypte", "EG", "Africa/Cairo", isCapital = true),
                cityAliases = listOf("cairo", "le caire", "caire"),
                countryAliases = listOf("eg", "egy", "egypt", "egypte")
            ),
            CatalogEntry(
                location = WorldLocation("MA_RABAT", "Rabat", "Morocco", "Maroc", "MA", "Africa/Casablanca", isCapital = true),
                cityAliases = listOf("rabat"),
                countryAliases = listOf("ma", "mar", "morocco", "maroc")
            ),
            CatalogEntry(
                location = WorldLocation("DZ_ALGIERS", "Algiers", "Algeria", "Algérie", "DZ", "Africa/Algiers", isCapital = true),
                cityAliases = listOf("algiers", "alger"),
                countryAliases = listOf("dz", "dza", "algeria", "algerie")
            ),
            CatalogEntry(
                location = WorldLocation("TN_TUNIS", "Tunis", "Tunisia", "Tunisie", "TN", "Africa/Tunis", isCapital = true),
                cityAliases = listOf("tunis"),
                countryAliases = listOf("tn", "tun", "tunisia", "tunisie")
            ),
            CatalogEntry(
                location = WorldLocation("SN_DAKAR", "Dakar", "Senegal", "Sénégal", "SN", "Africa/Dakar", isCapital = true),
                cityAliases = listOf("dakar"),
                countryAliases = listOf("sn", "sen", "senegal")
            ),
            CatalogEntry(
                location = WorldLocation("NG_ABUJA", "Abuja", "Nigeria", "Nigéria", "NG", "Africa/Lagos", isCapital = true),
                cityAliases = listOf("abuja"),
                countryAliases = listOf("ng", "nga", "nigeria")
            ),
            CatalogEntry(
                location = WorldLocation("KE_NAIROBI", "Nairobi", "Kenya", "Kenya", "KE", "Africa/Nairobi", isCapital = true),
                cityAliases = listOf("nairobi"),
                countryAliases = listOf("ke", "ken", "kenya")
            ),
            CatalogEntry(
                location = WorldLocation("ZA_PRETORIA", "Pretoria", "South Africa", "Afrique du Sud", "ZA", "Africa/Johannesburg", isCapital = true),
                cityAliases = listOf("pretoria"),
                countryAliases = listOf("za", "zaf", "rsa", "south africa", "afrique du sud")
            ),
            CatalogEntry(
                location = WorldLocation("BR_BRASILIA", "Brasília", "Brazil", "Brésil", "BR", "America/Sao_Paulo", isCapital = true),
                cityAliases = listOf("brasilia"),
                countryAliases = listOf("br", "bra", "brazil", "bresil")
            ),
            CatalogEntry(
                location = WorldLocation("AR_BUENOS_AIRES", "Buenos Aires", "Argentina", "Argentine", "AR", "America/Argentina/Buenos_Aires", isCapital = true),
                cityAliases = listOf("buenos aires"),
                countryAliases = listOf("ar", "arg", "argentina", "argentine")
            ),
            CatalogEntry(
                location = WorldLocation("MX_MEXICO_CITY", "Mexico City", "Mexico", "Mexique", "MX", "America/Mexico_City", isCapital = true),
                cityAliases = listOf("mexico city", "ciudad de mexico", "cdmx"),
                countryAliases = listOf("mx", "mex", "mexico", "mexique")
            ),
            CatalogEntry(
                location = WorldLocation("CL_SANTIAGO", "Santiago", "Chile", "Chili", "CL", "America/Santiago", isCapital = true),
                cityAliases = listOf("santiago"),
                countryAliases = listOf("cl", "chl", "chile", "chili")
            ),
            CatalogEntry(
                location = WorldLocation("CO_BOGOTA", "Bogotá", "Colombia", "Colombie", "CO", "America/Bogota", isCapital = true),
                cityAliases = listOf("bogota"),
                countryAliases = listOf("co", "col", "colombia", "colombie")
            ),
            CatalogEntry(
                location = WorldLocation("PE_LIMA", "Lima", "Peru", "Pérou", "PE", "America/Lima", isCapital = true),
                cityAliases = listOf("lima"),
                countryAliases = listOf("pe", "per", "peru", "perou")
            ),

            // --- OTHER MAJOR NON-CAPITAL WORLD CITIES ---
            CatalogEntry(
                location = WorldLocation("US_LOS_ANGELES", "Los Angeles", "United States", "États-Unis", "US", "America/Los_Angeles", isCapital = false),
                cityAliases = listOf("los angeles", "la", "hollywood", "california", "californie")
            ),
            CatalogEntry(
                location = WorldLocation("US_SAN_FRANCISCO", "San Francisco", "United States", "États-Unis", "US", "America/Los_Angeles", isCapital = false),
                cityAliases = listOf("san francisco", "sf")
            ),
            CatalogEntry(
                location = WorldLocation("US_CHICAGO", "Chicago", "United States", "États-Unis", "US", "America/Chicago", isCapital = false),
                cityAliases = listOf("chicago")
            ),
            CatalogEntry(
                location = WorldLocation("US_MIAMI", "Miami", "United States", "États-Unis", "US", "America/New_York", isCapital = false),
                cityAliases = listOf("miami", "florida", "floride")
            ),
            CatalogEntry(
                location = WorldLocation("US_HONOLULU", "Honolulu", "United States", "États-Unis", "US", "Pacific/Honolulu", isCapital = false),
                cityAliases = listOf("honolulu", "hawaii", "hawai")
            ),
            CatalogEntry(
                location = WorldLocation("CA_MONTREAL", "Montréal", "Canada", "Canada", "CA", "America/Toronto", isCapital = false),
                cityAliases = listOf("montreal", "quebec")
            ),
            CatalogEntry(
                location = WorldLocation("CA_TORONTO", "Toronto", "Canada", "Canada", "CA", "America/Toronto", isCapital = false),
                cityAliases = listOf("toronto")
            ),
            CatalogEntry(
                location = WorldLocation("CA_VANCOUVER", "Vancouver", "Canada", "Canada", "CA", "America/Vancouver", isCapital = false),
                cityAliases = listOf("vancouver")
            ),
            CatalogEntry(
                location = WorldLocation("AU_MELBOURNE", "Melbourne", "Australia", "Australie", "AU", "Australia/Melbourne", isCapital = false),
                cityAliases = listOf("melbourne")
            ),
            CatalogEntry(
                location = WorldLocation("AU_PERTH", "Perth", "Australia", "Australie", "AU", "Australia/Perth", isCapital = false),
                cityAliases = listOf("perth")
            ),
            CatalogEntry(
                location = WorldLocation("AU_BRISBANE", "Brisbane", "Australia", "Australie", "AU", "Australia/Brisbane", isCapital = false),
                cityAliases = listOf("brisbane")
            ),
            CatalogEntry(
                location = WorldLocation("CN_SHANGHAI", "Shanghai", "China", "Chine", "CN", "Asia/Shanghai", isCapital = false),
                cityAliases = listOf("shanghai")
            ),
            CatalogEntry(
                location = WorldLocation("HK_HONG_KONG", "Hong Kong", "China (SAR)", "Hong Kong", "HK", "Asia/Hong_Kong", isCapital = false),
                cityAliases = listOf("hong kong", "hk"),
                countryAliases = listOf("hk", "hkg")
            ),
            CatalogEntry(
                location = WorldLocation("JP_OSAKA", "Osaka", "Japan", "Japon", "JP", "Asia/Tokyo", isCapital = false),
                cityAliases = listOf("osaka", "kyoto")
            ),
            CatalogEntry(
                location = WorldLocation("AE_DUBAI", "Dubai", "United Arab Emirates", "Émirats arabes unis", "AE", "Asia/Dubai", isCapital = false),
                cityAliases = listOf("dubai", "dxb")
            ),
            CatalogEntry(
                location = WorldLocation("TR_ISTANBUL", "Istanbul", "Turkey", "Turquie", "TR", "Europe/Istanbul", isCapital = false),
                cityAliases = listOf("istanbul")
            ),
            CatalogEntry(
                location = WorldLocation("CH_GENEVA", "Geneva", "Switzerland", "Suisse", "CH", "Europe/Zurich", isCapital = false),
                cityAliases = listOf("geneva", "geneve", "zurich")
            ),
            CatalogEntry(
                location = WorldLocation("ES_BARCELONA", "Barcelona", "Spain", "Espagne", "ES", "Europe/Madrid", isCapital = false),
                cityAliases = listOf("barcelona", "barcelone")
            ),
            CatalogEntry(
                location = WorldLocation("IT_MILAN", "Milan", "Italy", "Italie", "IT", "Europe/Rome", isCapital = false),
                cityAliases = listOf("milan", "milano")
            ),
            CatalogEntry(
                location = WorldLocation("BR_SAO_PAULO", "São Paulo", "Brazil", "Brésil", "BR", "America/Sao_Paulo", isCapital = false),
                cityAliases = listOf("sao paulo", "rio", "rio de janeiro")
            ),
            CatalogEntry(
                location = WorldLocation("IN_MUMBAI", "Mumbai", "India", "Inde", "IN", "Asia/Kolkata", isCapital = false),
                cityAliases = listOf("mumbai", "bombay", "bangalore")
            ),
            CatalogEntry(
                location = WorldLocation("NZ_AUCKLAND", "Auckland", "New Zealand", "Nouvelle-Zélande", "NZ", "Pacific/Auckland", isCapital = false),
                cityAliases = listOf("auckland")
            ),
            CatalogEntry(
                location = WorldLocation("ID_BALI", "Bali (Denpasar)", "Indonesia", "Indonésie", "ID", "Asia/Makassar", isCapital = false),
                cityAliases = listOf("bali", "denpasar")
            ),
            CatalogEntry(
                location = WorldLocation("MA_CASABLANCA", "Casablanca", "Morocco", "Maroc", "MA", "Africa/Casablanca", isCapital = false),
                cityAliases = listOf("casablanca", "marrakech")
            ),
            CatalogEntry(
                location = WorldLocation("ZA_CAPE_TOWN", "Cape Town", "South Africa", "Afrique du Sud", "ZA", "Africa/Johannesburg", isCapital = false),
                cityAliases = listOf("cape town", "le cap", "johannesburg")
            )
        )
    }
}
