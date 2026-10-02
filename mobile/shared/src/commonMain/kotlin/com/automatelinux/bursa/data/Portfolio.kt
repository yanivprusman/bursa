package com.automatelinux.bursa.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.automatelinux.bursa.data.model.INDEX
import com.automatelinux.bursa.data.model.Saved
import com.automatelinux.bursa.data.model.Tracked
import com.automatelinux.bursa.data.model.refKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What the owner follows and holds. The list lives on the owner's own server and is shared
 * with the desktop version; this phone keeps a copy so the app opens with it at once and
 * still shows it with no connection.
 *
 * Every change is sent as one operation ("follow this", "hold 50 of that") and the server's
 * answer becomes the list — so the phone and the desktop can both be open and neither
 * overwrites what the other just did. A change is shown immediately; if the server cannot
 * be reached it is taken back and [syncError] says so. Nothing is ever silently dropped.
 */
class Portfolio(private val store: KeyValueStore, private val api: Api, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    val items = mutableStateListOf<Tracked>()
    /** The last few papers opened, newest first — what the search screen shows before typing. Local only. */
    val recent = mutableStateListOf<Tracked>()

    /** Why the list on screen may not be the server's, or why the last change did not stick. */
    var syncError by mutableStateOf<String?>(null)
        private set

    /** What "try again" does: re-send the change that failed, or fetch the list again. */
    private var again: (() -> Unit)? = null

    init {
        val saved = store.get(KEY)?.let { runCatching { json.decodeFromString(Saved.serializer(), it) }.getOrNull() }
        if (saved != null) {
            items.addAll(saved.items)
            recent.addAll(saved.recent)
        }
    }

    private fun saveLocal() = store.put(KEY, json.encodeToString(Saved.serializer(), Saved(items.toList(), recent.toList())))

    private fun replace(list: List<Tracked>) {
        if (items.toList() != list) {
            items.clear()
            items.addAll(list)
        }
        saveLocal()
    }

    fun find(kind: String, id: String): Tracked? = items.firstOrNull { it.kind == kind && it.id == id }
    fun isTracked(kind: String, id: String): Boolean = find(kind, id) != null

    val holdings: List<Tracked> get() = items.filter { it.held }
    val watching: List<Tracked> get() = items.filter { !it.held }

    /** Comma-joined keys for /api/quotes, or null when nothing is followed. */
    fun quoteIds(): String? = items.takeIf { it.isNotEmpty() }?.joinToString(",") { it.key }

    /**
     * Bring the list in line with the server. The first time, a list this phone built before
     * the list moved to the server is handed over (the server takes what it does not have yet).
     */
    fun sync(onChanged: () -> Unit = {}) {
        scope.launch {
            try {
                val before = items.toList()
                val handedOver = store.get(HANDED_OVER) != null
                val list = if (!handedOver && before.isNotEmpty()) {
                    api.listOp(buildJsonObject {
                        put("op", "import")
                        put("items", json.encodeToJsonElement(ListSerializer(Tracked.serializer()), before))
                    })
                } else {
                    api.list()
                }
                store.put(HANDED_OVER, "1")
                replace(list.items)
                syncError = null
                if (list.items != before) onChanged()
            } catch (e: Exception) {
                syncError = e.message ?: "משהו השתבש"
                again = { sync(onChanged) }
            }
        }
    }

    /** Show [optimistic] at once, send [op]; the server's answer is the list, a failure undoes it. */
    private fun change(op: JsonObject, onDone: () -> Unit, optimistic: () -> Unit) {
        val before = items.toList()
        optimistic()
        saveLocal()
        scope.launch {
            try {
                replace(api.listOp(op).items)
                syncError = null
                onDone()
            } catch (e: Exception) {
                replace(before)
                syncError = "השינוי לא נשמר — ${e.message ?: "משהו השתבש"}"
                // The same change, exactly: a failed write is offered again, never dropped.
                again = { change(op, onDone, optimistic) }
            }
        }
    }

    private fun itemJson(t: Tracked) = json.encodeToJsonElement(Tracked.serializer(), t.copy(qty = null, avgCost = null))

    fun follow(t: Tracked, onDone: () -> Unit = {}) {
        if (isTracked(t.kind, t.id)) return
        change(buildJsonObject { put("op", "follow"); put("item", itemJson(t)) }, onDone) {
            items.add(t.copy(qty = null, avgCost = null))
        }
    }

    fun unfollow(kind: String, id: String) {
        if (!isTracked(kind, id)) return
        change(buildJsonObject { put("op", "unfollow"); put("kind", kind); put("id", id) }, {}) {
            items.removeAll { it.kind == kind && it.id == id }
        }
    }

    /** Record a holding; following is implied. An index cannot be held. */
    fun setHolding(t: Tracked, qty: Double, avgCost: Double?, onDone: () -> Unit = {}) {
        require(t.kind != INDEX) { "an index cannot be held" }
        val op = buildJsonObject {
            put("op", "hold")
            put("item", itemJson(t))
            put("qty", qty)
            put("avgCost", avgCost?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
        }
        change(op, onDone) {
            val i = items.indexOfFirst { it.kind == t.kind && it.id == t.id }
            if (i >= 0) items[i] = items[i].copy(qty = qty, avgCost = avgCost) else items.add(t.copy(qty = qty, avgCost = avgCost))
        }
    }

    /** Drop the holding but keep following the paper. */
    fun clearHolding(kind: String, id: String) {
        val i = items.indexOfFirst { it.kind == kind && it.id == id }
        if (i < 0) return
        change(buildJsonObject { put("op", "clearHolding"); put("kind", kind); put("id", id) }, {}) {
            items[i] = items[i].copy(qty = null, avgCost = null)
        }
    }

    fun dismissError() {
        syncError = null
        again = null
    }

    fun retry() {
        val redo = again ?: return
        again = null
        syncError = null
        redo()
    }

    fun opened(t: Tracked) {
        recent.removeAll { refKey(it.kind, it.id) == t.key }
        recent.add(0, t.copy(qty = null, avgCost = null))
        while (recent.size > MAX_RECENT) recent.removeAt(recent.lastIndex)
        saveLocal()
    }

    private companion object {
        const val KEY = "saved"
        /** Set once this phone's own, pre-server list has been given to the server. */
        const val HANDED_OVER = "list-handed-over"
        const val MAX_RECENT = 8
    }
}
