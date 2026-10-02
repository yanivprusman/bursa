package com.automatelinux.bursa.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.automatelinux.bursa.data.model.Overview
import com.automatelinux.bursa.data.model.Quote
import com.automatelinux.bursa.data.model.QuotesResponse
import com.automatelinux.bursa.data.model.refKey
import com.automatelinux.bursa.nav.Navigator
import com.automatelinux.bursa.nav.Tab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer

/** Things only the Android shell can do; implemented in :app's MainActivity. */
interface Platform {
    val appVersion: String
    /** Opens a URL in the browser. Returns false when nothing can handle it. */
    fun openUrl(url: String): Boolean
}

/** App-wide state: the market, the user's list, the latest quote for everything on it. */
class AppContext(
    val api: Api,
    private val store: KeyValueStore,
    val platform: Platform,
    private val scope: CoroutineScope,
) {
    val portfolio = Portfolio(store, api, scope)
    val account = TradingAccount(store, api, scope)
    val nav = Navigator(if (portfolio.items.isEmpty() && account.summary?.positions.isNullOrEmpty()) Tab.Market else Tab.Mine)

    /** True between onStart and onStop — nothing polls while the app is in the background. */
    var active by mutableStateOf(false)

    val overview = Resource(api, scope, "/api/market", Overview.serializer())
    val marketOpen: Boolean get() = overview.data?.open == true

    /**
     * The newest quote seen for each paper, by [refKey]. Kept apart from any one request so
     * that following a new paper does not blank the rest of the list while it loads.
     */
    val quotes = mutableStateMapOf<String, Quote>()
    var quotesRefreshing by mutableStateOf(false)
        private set
    var quotesError by mutableStateOf<String?>(null)
        private set
    /** Papers the exchange no longer answers for (delisted, redeemed), by [refKey]. */
    val quotesMissing = mutableStateMapOf<String, String>()
    private var quotesLoading = false

    /** Decoded logos by company id. A key that maps to null is a company known to have none. */
    private val logos = mutableStateMapOf<String, ImageBitmap?>()
    private val logosAsked = mutableSetOf<String>()

    /**
     * The logo for [companyId] if it has arrived, else null — and the first call for an id
     * starts fetching it, so the tile fills in by itself when it lands.
     */
    fun logo(companyId: String): ImageBitmap? {
        if (logosAsked.add(companyId)) {
            scope.launch {
                try {
                    logos[companyId] = api.logo(companyId)?.let { decodeImage(it) }
                } catch (e: Exception) {
                    // Offline: leave it unasked so the next screen tries again.
                    logosAsked.remove(companyId)
                }
            }
        }
        return logos[companyId]
    }

    init {
        store.get(QUOTES_KEY)
            ?.let { runCatching { api.json.decodeFromString(ListSerializer(Quote.serializer()), it) }.getOrNull() }
            ?.forEach { quotes[refKey(it.kind, it.id)] = it }
    }

    /** Take a quote that arrived on some other screen (a detail page) as the newest. */
    fun learn(q: Quote) {
        quotes[refKey(q.kind, q.id)] = q
        if (refKey(q.kind, q.id) in mineKeys()) persistQuotes()
    }

    /** Everything the Mine tab prices: what is followed, held, or waiting to be bought or sold. */
    private fun mineKeys(): Set<String> {
        val a = account.summary
        return buildSet {
            portfolio.items.forEach { add(it.key) }
            a?.positions?.forEach { add(it.paper.key) }
            a?.pending?.forEach { add(it.paper.key) }
        }
    }

    private fun persistQuotes() {
        val kept = mineKeys().mapNotNull { quotes[it] }
        store.put(QUOTES_KEY, api.json.encodeToString(ListSerializer(Quote.serializer()), kept))
    }

    fun refreshQuotes(byUser: Boolean = false) {
        val ids = mineKeys().takeIf { it.isNotEmpty() }?.joinToString(",")
        if (ids == null) {
            quotesError = null
            return
        }
        if (quotesLoading && !byUser) return
        scope.launch {
            val started = Clock.System.now()
            if (byUser) quotesRefreshing = true
            quotesLoading = true
            try {
                val r = api.get("/api/quotes?ids=$ids", QuotesResponse.serializer(), remember = false)
                r.quotes.forEach { quotes[refKey(it.kind, it.id)] = it }
                quotesMissing.clear()
                r.missing.forEach { quotesMissing[refKey(it.kind, it.id)] = it.error }
                quotesError = null
                persistQuotes()
            } catch (e: Exception) {
                quotesError = e.message ?: "משהו השתבש"
            } finally {
                if (byUser) {
                    val spent = (Clock.System.now() - started).inWholeMilliseconds
                    if (spent < MIN_PULL_MS) delay(MIN_PULL_MS - spent)
                    quotesRefreshing = false
                }
                quotesLoading = false
            }
        }
    }

    fun refreshHome(byUser: Boolean = false) {
        overview.refresh(byUser)
        refreshQuotes(byUser)
        // The desktop can change the list and trade too; if it did, price what is new.
        portfolio.sync(onChanged = { refreshQuotes() })
        account.refresh(onChanged = { refreshQuotes() })
    }

    private companion object {
        const val QUOTES_KEY = "quotes"
    }
}

val LocalApp = staticCompositionLocalOf<AppContext> { error("AppContext not provided") }
