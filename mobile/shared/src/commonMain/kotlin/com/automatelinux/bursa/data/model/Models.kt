package com.automatelinux.bursa.data.model

import kotlinx.serialization.Serializable

// The server's shapes — see API.md at the repo root for units.

const val SECURITY = "security"
const val INDEX = "index"

/** "s629014" / "i142": the id the server takes in /api/quotes and the key quotes are kept under. */
fun refKey(kind: String, id: String): String = (if (kind == INDEX) "i" else "s") + id

@Serializable
data class Quote(
    val kind: String,
    val id: String,
    val name: String,
    val symbol: String? = null,
    val type: String? = null,
    val last: Double,
    val base: Double? = null,
    val change: Double? = null,
    val changePct: Double? = null,
    val unit: String = "agorot",
    val tradeDate: String? = null,
    val tradeTime: String? = null,
    /** The issuer, for its logo; null for an index. */
    val companyId: String? = null,
)

@Serializable
data class IndexRow(
    val id: String,
    val name: String,
    val last: Double,
    val changePct: Double? = null,
    val tradeDate: String? = null,
    val tradeTime: String? = null,
    val category: String = "",
    val gainers: Int? = null,
    val decliners: Int? = null,
    val unchanged: Int? = null,
    val turnover: Double? = null,
    val spark: List<Double> = emptyList(),
)

@Serializable
data class Mover(
    val id: String,
    val name: String,
    val last: Double,
    val changePct: Double,
    val turnover: Double? = null,
    val companyId: String? = null,
)

/** One entry on the ticker strip. */
@Serializable
data class TapeItem(val id: String, val name: String, val last: Double, val changePct: Double? = null)

@Serializable
data class Movers(
    val gainers: List<Mover> = emptyList(),
    val losers: List<Mover> = emptyList(),
    val active: List<Mover> = emptyList(),
)

@Serializable
data class Breadth(val gainers: Int? = null, val decliners: Int? = null, val unchanged: Int? = null)

@Serializable
data class SegmentTurnover(val name: String, val value: Double)

@Serializable
data class Overview(
    val open: Boolean = false,
    val tradeDate: String? = null,
    val tradeTime: String? = null,
    val indices: List<IndexRow> = emptyList(),
    val tape: List<TapeItem> = emptyList(),
    val movers: Movers = Movers(),
    val breadth: Breadth? = null,
    val turnovers: List<SegmentTurnover> = emptyList(),
)

@Serializable
data class IndicesResponse(val indices: List<IndexRow> = emptyList())

@Serializable
data class SecurityDetail(
    val id: String,
    val name: String,
    val symbol: String? = null,
    val type: String? = null,
    val last: Double,
    val base: Double? = null,
    val change: Double? = null,
    val changePct: Double? = null,
    val unit: String = "agorot",
    val tradeDate: String? = null,
    val tradeTime: String? = null,
    val longName: String? = null,
    val subType: String? = null,
    val sector: String? = null,
    val isin: String? = null,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val marketCap: Double? = null,
    val turnover: Double? = null,
    val volume: Double? = null,
    val deals: Double? = null,
    val monthYield: Double? = null,
    val yearYield: Double? = null,
    val grossYield: Double? = null,
    val redemptionDate: String? = null,
    val linkage: String? = null,
    val annualInterest: Double? = null,
    val about: String? = null,
    val site: String? = null,
    val companyId: String? = null,
) {
    fun quote() = Quote(SECURITY, id, name, symbol, type, last, base, change, changePct, unit, tradeDate, tradeTime, companyId)
}

@Serializable
data class Component(
    val id: String,
    val name: String,
    val symbol: String? = null,
    val last: Double? = null,
    val changePct: Double? = null,
    val weight: Double? = null,
    val turnover: Double? = null,
    val companyId: String? = null,
)

@Serializable
data class IndexDetail(
    val id: String,
    val name: String,
    val last: Double,
    val base: Double? = null,
    val change: Double? = null,
    val changePct: Double? = null,
    val unit: String = "points",
    val tradeDate: String? = null,
    val tradeTime: String? = null,
    val category: String = "",
    val gainers: Int? = null,
    val decliners: Int? = null,
    val unchanged: Int? = null,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val monthYield: Double? = null,
    val yearYield: Double? = null,
    val marketCap: Double? = null,
    val about: String? = null,
    val components: List<Component> = emptyList(),
) {
    fun quote() = Quote(INDEX, id, name, null, "מדד", last, base, change, changePct, unit, tradeDate, tradeTime)
}

@Serializable
data class MissingQuote(val kind: String, val id: String, val error: String = "")

@Serializable
data class QuotesResponse(val quotes: List<Quote> = emptyList(), val missing: List<MissingQuote> = emptyList())

@Serializable
data class ChartPoint(val t: String, val v: Double)

@Serializable
data class ChartData(val range: String = "", val base: Double? = null, val points: List<ChartPoint> = emptyList())

@Serializable
data class Hit(
    val kind: String,
    val id: String,
    val name: String,
    val symbol: String? = null,
    val type: String? = null,
    val isin: String? = null,
    val companyId: String? = null,
)

@Serializable
data class SearchResponse(val hits: List<Hit> = emptyList())

@Serializable
data class ErrorResponse(val error: String = "")

// ── what stays on the phone ─────────────────────────────────────────────────

/**
 * One thing the user follows. With [qty] it is also a holding: [qty] units bought at
 * [avgCost] agorot each (the cost is optional — without it there is a value but no gain).
 */
@Serializable
data class Tracked(
    val kind: String,
    val id: String,
    val name: String,
    val symbol: String? = null,
    val type: String? = null,
    val qty: Double? = null,
    val avgCost: Double? = null,
    val companyId: String? = null,
) {
    val key: String get() = refKey(kind, id)
    val held: Boolean get() = (qty ?: 0.0) > 0
}

@Serializable
data class Saved(val items: List<Tracked> = emptyList(), val recent: List<Tracked> = emptyList())
