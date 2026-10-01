package com.automatelinux.bursa.util

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Number and date wording for the whole app. commonMain has no String.format, and one
 * formatter is the only way "12,160" looks the same on every screen.
 *
 * Everything here returns plain digits and signs. A string that is only a number must be
 * drawn left-to-right even though the app is Hebrew — that is [com.automatelinux.bursa.ui.components.Num]'s job.
 */
object Fmt {
    private fun group(n: Long): String {
        val s = n.toString()
        val out = StringBuilder()
        for ((i, c) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) out.append(',')
            out.append(c)
        }
        return out.toString()
    }

    private fun pow10(n: Int): Long {
        var p = 1L
        repeat(n) { p *= 10 }
        return p
    }

    /** 1234567.891, 2 → "1,234,567.89". A value that rounds to zero never shows a minus. */
    fun fixed(v: Double, decimals: Int): String {
        val scale = pow10(decimals)
        val scaled = (abs(v) * scale).roundToLong()
        val out = StringBuilder()
        if (v < 0 && scaled != 0L) out.append('-')
        out.append(group(scaled / scale))
        if (decimals > 0) out.append('.').append((scaled % scale).toString().padStart(decimals, '0'))
        return out.toString()
    }

    /** Up to [maxDecimals], trailing zeros dropped: 12160.0 → "12,160", 104.5 → "104.5". */
    fun trimmed(v: Double, maxDecimals: Int = 2): String {
        val s = fixed(v, maxDecimals)
        if (maxDecimals == 0 || !s.contains('.')) return s
        return s.trimEnd('0').trimEnd('.')
    }

    /** A quoted price: indices always carry two decimals, securities only when they have them. */
    fun price(v: Double, unit: String): String = if (unit == "points") fixed(v, 2) else trimmed(v, 2)

    private fun sign(v: Double, text: String): String = if (v > 0 && !text.startsWith('-')) "+$text" else text

    /** "+0.34%" / "-1.22%" / "0.00%". */
    fun pct(v: Double): String = sign(v, fixed(v, 2)) + "%"

    /** A signed move in the price's own unit: "+14.27" / "-150". */
    fun move(v: Double, unit: String): String = sign(v, price(v, unit))

    /** "₪1,234.50"; from ₪10,000 up the agorot are noise and are dropped. */
    fun shekels(v: Double): String {
        val body = if (abs(v) >= 10_000) fixed(abs(v), 0) else fixed(abs(v), 2)
        val zero = body.all { it == '0' || it == '.' || it == ',' }
        return (if (v < 0 && !zero) "-" else "") + "₪" + body
    }

    /** "+₪120.50" / "-₪1,930.00". */
    fun signedShekels(v: Double): String {
        val s = shekels(v)
        return if (v > 0 && !s.startsWith('-')) "+$s" else s
    }

    /** A large shekel amount in words: 141_700_004_000.0 → "141.7 מיליארד ₪". */
    fun bigShekels(v: Double): String = when {
        abs(v) >= 1e9 -> trimmed(v / 1e9, 1) + " מיליארד ₪"
        abs(v) >= 1e6 -> trimmed(v / 1e6, 1) + " מיליון ₪"
        abs(v) >= 1e4 -> trimmed(v / 1e3, 0) + " אלף ₪"
        else -> fixed(v, 0) + " ₪"
    }

    fun quantity(v: Double): String = trimmed(v, 4)

    /** "2026-10-01" → "1.10.2026". Anything else is returned as it came. */
    fun date(iso: String?): String {
        val s = iso ?: return ""
        val p = s.take(10).split('-')
        if (p.size != 3) return s
        val d = p[2].toIntOrNull() ?: return s
        val m = p[1].toIntOrNull() ?: return s
        return "$d.$m.${p[0]}"
    }

    /** "2026-10-01" → "1.10" — for chart axes, where the year is already obvious. */
    fun dayMonth(iso: String): String {
        val p = iso.take(10).split('-')
        if (p.size != 3) return iso
        return "${p[2].toIntOrNull() ?: p[2]}.${p[1].toIntOrNull() ?: p[1]}"
    }

    /** "2026-10-01" → "10/26". */
    fun monthYear(iso: String): String {
        val p = iso.take(10).split('-')
        if (p.size != 3) return iso
        return "${p[1].toIntOrNull() ?: p[1]}/${p[0].takeLast(2)}"
    }

    /**
     * Read what a person typed into a number field: "1,250.5" and "1250.5" both work,
     * anything else — including a negative or an empty field — is null.
     */
    fun parse(text: String): Double? {
        val clean = text.trim().replace(",", "")
        if (clean.isEmpty() || !clean.all { it.isDigit() || it == '.' }) return null
        if (clean.count { it == '.' } > 1) return null
        return clean.toDoubleOrNull()
    }
}
