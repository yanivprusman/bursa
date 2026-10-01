package com.automatelinux.bursa.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.KSerializer

/** How often a visible screen re-reads live numbers while the exchange is trading. */
const val LIVE_REFRESH_MS = 30_000L

/** A pull-to-refresh that answers in 50ms looks ignored; hold the spinner long enough to read. */
const val MIN_PULL_MS = 600L

/**
 * One GET endpoint as UI state: the copy from last time is shown at once, then replaced
 * from the network. A failed refresh keeps the old numbers and says so through [error].
 */
class Resource<T>(
    private val api: Api,
    private val scope: CoroutineScope,
    val path: String,
    private val ser: KSerializer<T>,
) {
    var data by mutableStateOf(api.cached(path, ser))
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** True when what is on screen is last time's copy because the refresh failed. */
    val stale: Boolean get() = error != null && data != null

    fun refresh(byUser: Boolean = false) {
        if (loading && !byUser) return
        scope.launch {
            val started = Clock.System.now()
            if (byUser) refreshing = true
            loading = true
            try {
                data = api.get(path, ser)
                error = null
            } catch (e: Exception) {
                error = e.message ?: "משהו השתבש"
            } finally {
                if (byUser) {
                    val spent = (Clock.System.now() - started).inWholeMilliseconds
                    if (spent < MIN_PULL_MS) delay(MIN_PULL_MS - spent)
                    refreshing = false
                }
                loading = false
            }
        }
    }
}

/**
 * A screen's own endpoint. It loads when the screen opens, again whenever the app comes
 * back to the foreground, and every [LIVE_REFRESH_MS] while [live] says the market is open.
 */
@Composable
fun <T> rememberResource(path: String, ser: KSerializer<T>, live: () -> Boolean = { false }): Resource<T> {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val r = remember(path) { Resource(app.api, scope, path, ser) }
    LaunchedEffect(path, app.active) {
        if (!app.active) return@LaunchedEffect
        r.refresh()
        while (true) {
            delay(LIVE_REFRESH_MS)
            if (live()) r.refresh()
        }
    }
    return r
}
