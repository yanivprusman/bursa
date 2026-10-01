package com.automatelinux.bursa

import com.automatelinux.bursa.util.holdingValue
import com.automatelinux.bursa.util.portfolioTotals
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortfolioMathTest {
    private fun near(expected: Double, actual: Double?) =
        assertTrue(actual != null && abs(expected - actual) < 1e-6, "expected $expected, got $actual")

    @Test fun pricesAreAgorot() {
        // 100 Teva at 12,160 agorot = 12,160 shekels.
        val v = holdingValue(qty = 100.0, avgCost = 10000.0, last = 12160.0, base = 12310.0)
        near(12160.0, v.value)
        near(-150.0, v.dayChange)
        near(10000.0, v.cost)
        near(2160.0, v.gain)
        near(21.6, v.gainPct)
    }

    @Test fun noCostMeansNoGain() {
        val v = holdingValue(qty = 10.0, avgCost = null, last = 500.0, base = 480.0)
        near(50.0, v.value)
        near(2.0, v.dayChange)
        assertNull(v.cost)
        assertNull(v.gain)
        assertNull(v.gainPct)
    }

    @Test fun noPreviousCloseMeansNoDayChange() {
        assertNull(holdingValue(10.0, null, 500.0, null).dayChange)
    }

    @Test fun totalsAddUp() {
        val a = holdingValue(100.0, 10000.0, 12160.0, 12310.0) // 12,160 / -150 / gain 2,160
        val b = holdingValue(50.0, 8000.0, 7522.0, 7400.0)     //  3,761 /  +61 / gain  -239
        val t = portfolioTotals(listOf(a, b))
        near(15921.0, t.value)
        near(-89.0, t.dayChange)
        // Against yesterday's worth of the same holdings: 16,010.
        near(-89.0 / 16010.0 * 100, t.dayChangePct)
        near(1921.0, t.gain)
        near(1921.0 / 14000.0 * 100, t.gainPct)
        assertFalse(t.gainIsPartial)
    }

    @Test fun gainIsPartialWhenACostIsMissing() {
        val t = portfolioTotals(listOf(holdingValue(100.0, 10000.0, 12160.0, 12310.0), holdingValue(10.0, null, 500.0, 480.0)))
        near(2160.0, t.gain)
        assertTrue(t.gainIsPartial)
    }

    @Test fun emptyPortfolio() {
        val t = portfolioTotals(emptyList())
        assertEquals(0.0, t.value)
        assertNull(t.dayChangePct)
        assertNull(t.gain)
        assertFalse(t.gainIsPartial)
    }
}
