package com.automatelinux.bursa.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.data.LocalApp
import com.automatelinux.bursa.data.model.ChartData
import com.automatelinux.bursa.data.model.Quote
import com.automatelinux.bursa.data.rememberResource
import com.automatelinux.bursa.ui.components.BackButton
import com.automatelinux.bursa.ui.components.LogoTile
import com.automatelinux.bursa.ui.components.Segmented
import com.automatelinux.bursa.ui.components.Skeleton
import com.automatelinux.bursa.ui.components.Num
import com.automatelinux.bursa.ui.components.PriceChart
import com.automatelinux.bursa.ui.theme.Bursa
import com.automatelinux.bursa.ui.theme.NumBody
import com.automatelinux.bursa.ui.theme.NumHero
import com.automatelinux.bursa.ui.theme.NumMedium
import com.automatelinux.bursa.ui.theme.NumSmall
import com.automatelinux.bursa.ui.theme.NumTiny
import com.automatelinux.bursa.ui.theme.of
import com.automatelinux.bursa.util.Fmt
import kotlin.math.abs

/** Back, the paper's face and name, and the star that follows or unfollows it. */
@Composable
fun DetailBar(title: String, kind: String, companyId: String?, followed: Boolean, onToggleFollow: () -> Unit) {
    val app = LocalApp.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton { app.nav.back() }
        LogoTile(kind, companyId, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onToggleFollow, modifier = Modifier.testTag("toggle-follow")) {
            if (followed) Icon(Icons.Filled.Star, "הסרה מהמעקב", tint = Bursa.colors.accent)
            else Icon(Icons.Outlined.StarBorder, "הוספה למעקב")
        }
    }
}

/** The price, big; how far it moved; and as of when. */
@Composable
fun PriceHeader(q: Quote, sub: String?) {
    val c = Bursa.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (!sub.isNullOrBlank()) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Num(Fmt.price(q.last, q.unit), style = NumHero, flashOn = q.last)
            Spacer(Modifier.width(8.dp))
            Text(
                if (q.unit == "points") "נק'" else "אג'",
                Modifier.padding(bottom = 7.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pct = q.changePct
            if (pct != null) {
                val arrow = if (pct > 0) "▲ " else if (pct < 0) "▼ " else ""
                val moved = q.change?.let { Fmt.price(abs(it), q.unit) + "  " } ?: ""
                Num(arrow + moved + "(" + Fmt.pct(pct) + ")", style = NumMedium, color = c.of(pct))
                Spacer(Modifier.width(12.dp))
            }
            val time = q.tradeTime
            Text(
                if (time != null) "עסקה אחרונה ⁦$time⁩" else "סוף יום המסחר ⁦${Fmt.date(q.tradeDate)}⁩",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Range key → the chip's label → how the move over that range is worded. */
private val RANGES = listOf(
    Triple("1d", "יום", "מול סגירה קודמת"),
    Triple("1w", "שבוע", "בשבוע האחרון"),
    Triple("1m", "חודש", "בחודש האחרון"),
    Triple("3m", "3ח'", "ב-3 חודשים"),
    Triple("6m", "6ח'", "בחצי שנה"),
    Triple("1y", "שנה", "בשנה האחרונה"),
    Triple("3y", "3ש'", "ב-3 שנים"),
    Triple("5y", "5ש'", "ב-5 שנים"),
)

/**
 * The chart with its range picker. Above it sits one line that is either the move across
 * the whole range or — while a finger is on the chart — the value under the finger.
 */
@Composable
fun ChartBlock(kind: String, id: String, unit: String) {
    val app = LocalApp.current
    val c = Bursa.colors
    var range by rememberSaveable(kind, id) { mutableStateOf("1d") }
    val res = rememberResource(
        "/api/chart?kind=$kind&id=$id&range=$range",
        ChartData.serializer(),
        live = { range == "1d" && app.marketOpen },
    )
    var scrub by remember(res.path) { mutableStateOf<Int?>(null) }

    val data = res.data
    val points = data?.points.orEmpty()
    val intraday = range == "1d"
    // A day is measured against the previous close; a longer range against its own first point.
    val reference = if (intraday) data?.base else points.firstOrNull()?.v
    val last = points.lastOrNull()?.v
    val movePct = if (reference != null && last != null && reference != 0.0) (last - reference) / reference * 100 else null
    val color = c.of(movePct)
    val phrase = RANGES.first { it.first == range }.third

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(26.dp), verticalAlignment = Alignment.CenterVertically) {
            val at = scrub?.let { points.getOrNull(it) }
            when {
                at != null -> {
                    Num(Fmt.price(at.v, unit), style = NumMedium)
                    Spacer(Modifier.width(10.dp))
                    Num(if (intraday) at.t.substringAfter('T') else Fmt.date(at.t), style = NumBody, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                movePct != null -> {
                    Num((if (movePct > 0) "▲ " else if (movePct < 0) "▼ " else "") + Fmt.fixed(abs(movePct), 2) + "%", style = NumMedium, color = color)
                    Spacer(Modifier.width(10.dp))
                    Text(phrase, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // The chart has no price axis; its lowest and highest points are named here instead.
            if (at == null && points.size >= 2) {
                Spacer(Modifier.weight(1f))
                Num(
                    Fmt.price(points.minOf { it.v }, unit) + " – " + Fmt.price(points.maxOf { it.v }, unit),
                    style = NumSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(210.dp).padding(start = 12.dp, end = 12.dp), contentAlignment = Alignment.Center) {
            val error = res.error
            when {
                points.size >= 2 -> PriceChart(
                    values = points.map { it.v },
                    baseline = if (intraday) data?.base else null,
                    color = color,
                    guide = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxSize().testTag("price-chart"),
                    onScrub = { scrub = it },
                )
                data == null && error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { res.refresh() }) { Text("נסו שוב") }
                }
                data == null -> Skeleton(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 14.dp))
                else -> Text(
                    if (intraday) "עדיין אין עסקאות היום" else "אין נתונים לתקופה הזאת",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (points.size >= 2) {
            // The time axis reads left to right like the line above it.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    fun tick(t: String) = when {
                        intraday -> t.substringAfter('T')
                        range == "1y" || range == "3y" || range == "5y" -> Fmt.monthYear(t)
                        else -> Fmt.dayMonth(t)
                    }
                    Num(tick(points.first().t), style = NumTiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Num(tick(points.last().t), style = NumTiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Segmented(
            RANGES.map { it.second },
            selected = RANGES.indexOfFirst { it.first == range },
            onSelect = { range = RANGES[it].first },
            modifier = Modifier.padding(horizontal = 16.dp),
            tag = "range",
        )
    }
}

/** A detail page's outline while it loads: the price, the chart, the first card. */
@Composable
fun DetailSkeleton() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Skeleton(Modifier.width(150.dp).height(12.dp))
        Spacer(Modifier.height(12.dp))
        Skeleton(Modifier.width(190.dp).height(44.dp))
        Spacer(Modifier.height(10.dp))
        Skeleton(Modifier.width(130.dp).height(16.dp))
        Spacer(Modifier.height(22.dp))
        Skeleton(Modifier.fillMaxWidth().height(210.dp), RoundedCornerShape(18.dp))
        Spacer(Modifier.height(14.dp))
        Skeleton(Modifier.fillMaxWidth().height(40.dp), RoundedCornerShape(14.dp))
        Spacer(Modifier.height(22.dp))
        Skeleton(Modifier.fillMaxWidth().height(150.dp), RoundedCornerShape(18.dp))
    }
}
