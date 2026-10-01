package com.automatelinux.bursa.data

import androidx.compose.runtime.mutableStateListOf
import com.automatelinux.bursa.data.model.INDEX
import com.automatelinux.bursa.data.model.Saved
import com.automatelinux.bursa.data.model.Tracked
import com.automatelinux.bursa.data.model.refKey
import kotlinx.serialization.json.Json

/**
 * What the user follows and holds. It lives on the phone and nowhere else — the server
 * only ever sees which quotes are asked for, never a quantity.
 */
class Portfolio(private val store: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    val items = mutableStateListOf<Tracked>()
    /** The last few papers opened, newest first — what the search screen shows before typing. */
    val recent = mutableStateListOf<Tracked>()

    init {
        val saved = store.get(KEY)?.let { runCatching { json.decodeFromString(Saved.serializer(), it) }.getOrNull() }
        if (saved != null) {
            items.addAll(saved.items)
            recent.addAll(saved.recent)
        }
    }

    private fun save() = store.put(KEY, json.encodeToString(Saved.serializer(), Saved(items.toList(), recent.toList())))

    fun find(kind: String, id: String): Tracked? = items.firstOrNull { it.kind == kind && it.id == id }
    fun isTracked(kind: String, id: String): Boolean = find(kind, id) != null

    val holdings: List<Tracked> get() = items.filter { it.held }
    val watching: List<Tracked> get() = items.filter { !it.held }

    /** Comma-joined keys for /api/quotes, or null when nothing is followed. */
    fun quoteIds(): String? = items.takeIf { it.isNotEmpty() }?.joinToString(",") { it.key }

    fun follow(t: Tracked) {
        if (isTracked(t.kind, t.id)) return
        items.add(t.copy(qty = null, avgCost = null))
        save()
    }

    fun unfollow(kind: String, id: String) {
        if (items.removeAll { it.kind == kind && it.id == id }) save()
    }

    /** Record a holding; following is implied. An index cannot be held. */
    fun setHolding(t: Tracked, qty: Double, avgCost: Double?) {
        require(t.kind != INDEX) { "an index cannot be held" }
        val held = t.copy(qty = qty, avgCost = avgCost)
        val i = items.indexOfFirst { it.kind == t.kind && it.id == t.id }
        if (i >= 0) items[i] = held else items.add(held)
        save()
    }

    /** Drop the holding but keep following the paper. */
    fun clearHolding(kind: String, id: String) {
        val i = items.indexOfFirst { it.kind == kind && it.id == id }
        if (i < 0) return
        items[i] = items[i].copy(qty = null, avgCost = null)
        save()
    }

    fun opened(t: Tracked) {
        recent.removeAll { refKey(it.kind, it.id) == t.key }
        recent.add(0, t.copy(qty = null, avgCost = null))
        while (recent.size > MAX_RECENT) recent.removeAt(recent.lastIndex)
        save()
    }

    private companion object {
        const val KEY = "saved"
        const val MAX_RECENT = 8
    }
}
