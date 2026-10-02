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
 * One thing the user follows. What is HELD is not here: holdings come only from trades in
 * the practice account ([AccountSummary.positions]).
 */
@Serializable
data class Tracked(
    val kind: String,
    val id: String,
    val name: String,
    val symbol: String? = null,
    val type: String? = null,
    val companyId: String? = null,
) {
    val key: String get() = refKey(kind, id)
}

/** The owner's list as the server keeps it (`/api/list`). */
@Serializable
data class ListResponse(val rev: Int = 0, val items: List<Tracked> = emptyList())

@Serializable
data class Saved(val items: List<Tracked> = emptyList(), val recent: List<Tracked> = emptyList())

// ── the practice trading account (`/api/account`) ───────────────────────────
// Money is in agorot, as the server keeps it.

@Serializable
data class Paper(val id: String, val name: String, val symbol: String? = null, val type: String? = null, val companyId: String? = null) {
    val key: String get() = refKey(SECURITY, id)
    fun tracked() = Tracked(SECURITY, id, name, symbol, type, companyId)
}

@Serializable
data class Position(val paper: Paper, val qty: Double, val cost: Double, val avgCost: Double)

@Serializable
data class Order(
    val id: String,
    val side: String,
    val paper: Paper,
    val qty: Double,
    val placedAt: String,
    val fillFrom: String,
    val status: String,
    val reason: String? = null,
    val reserve: Double = 0.0,
    val closedAt: String? = null,
)

@Serializable
data class Trade(
    val id: String,
    val orderId: String,
    val side: String,
    val paper: Paper,
    val qty: Double,
    val price: Double,
    val gross: Double,
    val fee: Double,
    val at: String,
    val tradeDate: String? = null,
    val how: String,
)

@Serializable
data class TradeRules(val feeRate: Double = 0.001, val feeMin: Double = 500.0)

@Serializable
data class AccountSummary(
    val mode: String = "practice",
    val rev: Int = 0,
    val startCash: Double,
    val cash: Double,
    val available: Double,
    val positions: List<Position> = emptyList(),
    val realized: Double = 0.0,
    val fees: Double = 0.0,
    val pending: List<Order> = emptyList(),
    val orders: List<Order> = emptyList(),
    val trades: List<Trade> = emptyList(),
    val rules: TradeRules = TradeRules(),
    /** Only on an answer to an operation: the order it made, as the server left it. */
    val order: Order? = null,
) {
    fun position(id: String): Position? = positions.firstOrNull { it.paper.id == id }
    fun holds(id: String): Boolean = position(id) != null
}

const val BUY = "buy"
const val SELL = "sell"
