package com.automatelinux.bursa

import com.automatelinux.bursa.util.Fmt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatTest {
    @Test fun groupsThousands() {
        assertEquals("1,234,567.89", Fmt.fixed(1234567.891, 2))
        assertEquals("999", Fmt.fixed(999.4, 0))
        assertEquals("1,000", Fmt.fixed(999.5, 0))
        assertEquals("0.05", Fmt.fixed(0.05, 2))
    }

    @Test fun negativeThatRoundsToZeroHasNoMinus() {
        assertEquals("0.00", Fmt.fixed(-0.001, 2))
        assertEquals("-0.01", Fmt.fixed(-0.006, 2))
        assertEquals("0.00%", Fmt.pct(-0.004))
    }

    @Test fun pricesKeepOnlyTheDecimalsTheyHave() {
        assertEquals("12,160", Fmt.price(12160.0, "agorot"))
        assertEquals("104.5", Fmt.price(104.5, "agorot"))
        assertEquals("104.53", Fmt.price(104.534, "agorot"))
        assertEquals("4,218.25", Fmt.price(4218.25, "points"))
        assertEquals("4,218.00", Fmt.price(4218.0, "points"))
    }

    @Test fun trimmedNeverEatsWholeZeros() {
        assertEquals("100", Fmt.trimmed(100.0))
        assertEquals("1,200", Fmt.trimmed(1200.0))
        assertEquals("0", Fmt.trimmed(0.0))
    }

    @Test fun percentAlwaysShowsItsSign() {
        assertEquals("+0.34%", Fmt.pct(0.34))
        assertEquals("-1.22%", Fmt.pct(-1.22))
        assertEquals("0.00%", Fmt.pct(0.0))
    }

    @Test fun shekels() {
        assertEquals("₪1,234.50", Fmt.shekels(1234.5))
        assertEquals("₪12,346", Fmt.shekels(12345.67))
        assertEquals("-₪50.00", Fmt.shekels(-50.0))
        assertEquals("₪0.00", Fmt.shekels(-0.001))
        assertEquals("+₪120.50", Fmt.signedShekels(120.5))
        assertEquals("-₪1,930.00", Fmt.signedShekels(-1930.0))
        assertEquals("₪0.00", Fmt.signedShekels(0.0))
    }

    @Test fun bigShekelsInWords() {
        assertEquals("141.7 מיליארד ₪", Fmt.bigShekels(141_700_004_000.0))
        assertEquals("587.3 מיליון ₪", Fmt.bigShekels(587_288_000.0))
        assertEquals("109 מיליון ₪", Fmt.bigShekels(109_034_062.0))
        assertEquals("61 אלף ₪", Fmt.bigShekels(60_800.0))
        assertEquals("950 ₪", Fmt.bigShekels(950.0))
    }

    @Test fun dates() {
        assertEquals("1.10.2026", Fmt.date("2026-10-01"))
        assertEquals("", Fmt.date(null))
        assertEquals("1.10", Fmt.dayMonth("2026-10-01"))
        assertEquals("10/26", Fmt.monthYear("2026-10-01"))
    }

    @Test fun parsesWhatPeopleType() {
        assertEquals(1250.5, Fmt.parse("1,250.5"))
        assertEquals(12.0, Fmt.parse(" 12 "))
        assertNull(Fmt.parse(""))
        assertNull(Fmt.parse("-5"))
        assertNull(Fmt.parse("1.2.3"))
        assertNull(Fmt.parse("abc"))
    }
}
