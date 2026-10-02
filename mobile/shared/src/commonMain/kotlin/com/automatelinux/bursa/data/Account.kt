package com.automatelinux.bursa.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.automatelinux.bursa.data.model.AccountSummary
import com.automatelinux.bursa.data.model.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The practice trading account: pretend cash, real prices. It lives on the owner's server
 * and is shared with the desktop. The phone only ever asks for an order by side and
 * quantity — the server prices it from the exchange — so nothing here is optimistic: what
 * is on screen is what the server answered. The last answer is kept so the app opens with it.
 */
class TradingAccount(private val store: KeyValueStore, private val api: Api, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    var summary by mutableStateOf<AccountSummary?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        summary = store.get(KEY)?.let { runCatching { json.decodeFromString(AccountSummary.serializer(), it) }.getOrNull() }
    }

    private fun take(a: AccountSummary) {
        summary = a.copy(order = null)
        error = null
        store.put(KEY, json.encodeToString(AccountSummary.serializer(), summary!!))
    }

    /** Read the account; this is also what fills a waiting order once its paper has opened. */
    fun refresh(onChanged: () -> Unit = {}) {
        scope.launch {
            try {
                val before = summary
                take(api.account())
                if (before?.rev != summary?.rev) onChanged()
            } catch (e: Exception) {
                error = e.message ?: "משהו השתבש"
            }
        }
    }

    private suspend fun run(op: JsonObject): AccountSummary {
        val after = api.accountOp(op)
        take(after)
        return after
    }

    /** A market order. Returns the order as the server left it — filled, waiting or rejected. Throws when refused. */
    suspend fun order(side: String, id: String, qty: Long): Order =
        run(buildJsonObject { put("op", "order"); put("side", side); put("id", id); put("qty", qty) }).order
            ?: throw ApiException("תשובה לא צפויה מהשרת")

    suspend fun cancel(orderId: String) {
        run(buildJsonObject { put("op", "cancel"); put("orderId", orderId) })
    }

    suspend fun reset() {
        run(buildJsonObject { put("op", "reset"); put("confirm", true) })
    }

    private companion object {
        const val KEY = "account"
    }
}
