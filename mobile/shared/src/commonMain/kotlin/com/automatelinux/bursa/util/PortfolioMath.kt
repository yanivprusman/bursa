package com.automatelinux.bursa.util

/**
 * What a holding is worth. TASE quotes every traded security in agorot, so
 * shekels = quantity × price / 100 — for a share, an ETF unit and a bond's par value alike.
 */
data class HoldingValue(
    /** Shekels, at the last price. */
    val value: Double,
    /** Shekels gained or lost since the previous close; null when there is no previous close. */
    val dayChange: Double?,
    /** Shekels paid; null when no purchase price was entered. */
    val cost: Double?,
    /** Shekels gained or lost since purchase; null without a purchase price. */
    val gain: Double?,
    val gainPct: Double?,
)

fun holdingValue(qty: Double, avgCost: Double?, last: Double, base: Double?): HoldingValue {
    val value = qty * last / 100
    val cost = avgCost?.let { qty * it / 100 }
    val gain = cost?.let { value - it }
    return HoldingValue(
        value = value,
        dayChange = base?.let { qty * (last - it) / 100 },
        cost = cost,
        gain = gain,
        gainPct = if (gain != null && cost != null && cost > 0) gain / cost * 100 else null,
    )
}

data class PortfolioTotals(
    val value: Double,
    val dayChange: Double,
    /** Against what the same holdings were worth at the previous close. */
    val dayChangePct: Double?,
    /** Summed only over holdings that have a purchase price. */
    val gain: Double?,
    val gainPct: Double?,
    /** True when some holding has no purchase price, so [gain] does not cover the whole portfolio. */
    val gainIsPartial: Boolean,
)

fun portfolioTotals(holdings: List<HoldingValue>): PortfolioTotals {
    val value = holdings.sumOf { it.value }
    val dayChange = holdings.sumOf { it.dayChange ?: 0.0 }
    val previous = value - dayChange
    val costed = holdings.filter { it.cost != null }
    val cost = costed.sumOf { it.cost ?: 0.0 }
    val gain = if (costed.isEmpty()) null else costed.sumOf { it.gain ?: 0.0 }
    return PortfolioTotals(
        value = value,
        dayChange = dayChange,
        dayChangePct = if (previous > 0) dayChange / previous * 100 else null,
        gain = gain,
        gainPct = if (gain != null && cost > 0) gain / cost * 100 else null,
        gainIsPartial = costed.isNotEmpty() && costed.size < holdings.size,
    )
}
