package com.automatelinux.bursa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.data.LocalApp
import com.automatelinux.bursa.data.model.Breadth
import com.automatelinux.bursa.data.model.ChartData
import com.automatelinux.bursa.data.model.INDEX
import com.automatelinux.bursa.data.model.IndexRow
import com.automatelinux.bursa.data.model.Mover
import com.automatelinux.bursa.data.model.Overview
import com.automatelinux.bursa.data.model.SECURITY
import com.automatelinux.bursa.data.rememberResource
import com.automatelinux.bursa.nav.Screen
import com.automatelinux.bursa.ui.components.Card
import com.automatelinux.bursa.ui.components.ChangeChip
import com.automatelinux.bursa.ui.components.FailedBlock
import com.automatelinux.bursa.ui.components.HeroCard
import com.automatelinux.bursa.ui.components.NameBlock
import com.automatelinux.bursa.ui.components.Num
import com.automatelinux.bursa.ui.components.PaperRow
import com.automatelinux.bursa.ui.components.PriceChart
import com.automatelinux.bursa.ui.components.Refreshable
import com.automatelinux.bursa.ui.components.SectionTitle
import com.automatelinux.bursa.ui.components.Segmented
import com.automatelinux.bursa.ui.components.Skeleton
import com.automatelinux.bursa.ui.components.SkeletonRows
import com.automatelinux.bursa.ui.components.Sparkline
import com.automatelinux.bursa.ui.components.StaleNote
import com.automatelinux.bursa.ui.theme.Bursa
import com.automatelinux.bursa.ui.theme.NumBody
import com.automatelinux.bursa.ui.theme.NumHero
import com.automatelinux.bursa.ui.theme.NumMedium
import com.automatelinux.bursa.ui.theme.NumSmall
import com.automatelinux.bursa.ui.theme.NumTiny
import com.automatelinux.bursa.util.Fmt
import kotlin.math.abs

// The bright pair for anything drawn on the dark hero band, in either theme.
private val HeroUp = Color(0xFF3DD68C)
private val HeroDown = Color(0xFFFF7B7B)

@Composable
fun MarketTab() {
    val app = LocalApp.current
    val res = app.overview
    val market = res.data

    Refreshable(res.refreshing, onRefresh = { app.refreshHome(byUser = true) }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp)) {
            if (market == null) {
                item {
                    val error = res.error
                    if (error != null) FailedBlock(error, onRetry = { res.refresh() }) else MarketSkeleton()
                }
                return@LazyColumn
            }
            res.error?.let { item { StaleNote(it, onRetry = { res.refresh() }) } }

            val lead = market.indices.firstOrNull()
            if (lead != null) {
                item { LeadIndex(lead) { app.nav.push(Screen.Index(lead.id, lead.name)) } }
            }

            val rest = market.indices.drop(1)
            if (rest.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rest.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { row ->
                                    IndexCard(row, Modifier.weight(1f)) { app.nav.push(Screen.Index(row.id, row.name)) }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            market.breadth?.let { b -> item { BreadthCard(b) } }

            item { MoversSection(market) }

            if (market.turnovers.isNotEmpty()) {
                item {
                    SectionTitle("מחזורי המסחר", note = Fmt.date(market.tradeDate))
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        market.turnovers.forEach { t ->
                            Card(Modifier.weight(1f), padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                                Column {
                                    Text(
                                        t.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    // Turnovers arrive in thousands of shekels.
                                    Text(
                                        Fmt.bigShekels(t.value * 1000),
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "הנתונים מאתר הבורסה לניירות ערך בתל אביב. מחירי ניירות הערך באגורות, המדדים בנקודות.",
                    Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The market in one card: the flagship index, large, over the line it drew today. Before the
 * first trade of the day there is no such line, so the past month's closes stand in — and the
 * caption says which of the two it is.
 */
@Composable
private fun LeadIndex(row: IndexRow, onClick: () -> Unit) {
    val app = LocalApp.current
    val c = Bursa.colors
    val today = rememberResource(
        "/api/chart?kind=$INDEX&id=${row.id}&range=1d",
        ChartData.serializer(),
        live = { app.marketOpen },
    )
    val pct = row.changePct
    val tone = when {
        pct == null || pct == 0.0 -> c.onHeroMuted
        pct > 0 -> HeroUp
        else -> HeroDown
    }
    val points = today.data?.points.orEmpty()
    val intraday = points.size >= 2

    HeroCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("lead-index"), onClick = onClick) {
        Column(Modifier.padding(top = 18.dp, bottom = 14.dp)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.onHero)
                    // The exchange's own name for ת"א-35.
                    if (row.id == "142") Text("מדד הדגל", style = MaterialTheme.typography.labelMedium, color = c.onHeroMuted)
                }
                Spacer(Modifier.height(6.dp))
                Num(Fmt.fixed(row.last, 2), style = NumHero, color = c.onHero, flashOn = row.last)
                Spacer(Modifier.height(2.dp))
                if (pct != null) {
                    Num(
                        (if (pct > 0) "▲ " else if (pct < 0) "▼ " else "") + Fmt.fixed(abs(pct), 2) + "%",
                        style = NumMedium,
                        color = tone,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                when {
                    intraday -> PriceChart(
                        values = points.map { it.v },
                        baseline = today.data?.base,
                        color = tone,
                        guide = c.onHeroMuted.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxSize(),
                        interactive = false,
                    )
                    today.data == null && today.error == null -> Skeleton(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp))
                    row.spark.size >= 2 -> PriceChart(
                        values = row.spark,
                        baseline = null,
                        color = c.accent,
                        guide = c.onHeroMuted,
                        modifier = Modifier.fillMaxSize(),
                        interactive = false,
                    )
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (intraday) {
                        Num(points.first().t.substringAfter('T'), style = NumTiny, color = c.onHeroMuted)
                        Spacer(Modifier.weight(1f))
                        Text("⁧היום, מול הסגירה הקודמת⁩", style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
                        Spacer(Modifier.weight(1f))
                        Num(points.last().t.substringAfter('T'), style = NumTiny, color = c.onHeroMuted)
                    } else if (row.spark.size >= 2 && (today.data != null || today.error != null)) {
                        Spacer(Modifier.weight(1f))
                        Text("⁧עוד אין מסחר היום — החודש האחרון⁩", style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun IndexCard(row: IndexRow, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.testTag("index-card-${row.id}"), onClick = onClick, padding = PaddingValues(14.dp)) {
        Column {
            Text(row.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Num(Fmt.fixed(row.last, 2), style = NumMedium, flashOn = row.last)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChangeChip(row.changePct)
                Spacer(Modifier.width(10.dp))
                // A month of closes. Gold, not green or red: it is a shape, not today's direction.
                Sparkline(row.spark, Bursa.colors.accent, Modifier.weight(1f).height(28.dp))
            }
        }
    }
}

/** How many of the 125 rose and fell — the mood of the day in one bar. */
@Composable
private fun BreadthCard(b: Breadth) {
    val c = Bursa.colors
    val up = b.gainers ?: 0
    val down = b.decliners ?: 0
    val same = b.unchanged ?: 0
    if (up + down + same == 0) return
    SectionTitle("ת\"א-125 היום", note = "כמה מהמניות עלו וכמה ירדו")
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Num(down.toString(), style = NumMedium.copy(fontSize = NumMedium.fontSize * 1.3f), color = c.down)
                    Spacer(Modifier.width(6.dp))
                    Text("ירדו", Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.bodyMedium, color = c.down)
                }
                if (same > 0) {
                    Text(
                        "$same ללא שינוי",
                        Modifier.padding(bottom = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
                    Text("עלו", Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.bodyMedium, color = c.up)
                    Spacer(Modifier.width(6.dp))
                    Num(up.toString(), style = NumMedium.copy(fontSize = NumMedium.fontSize * 1.3f), color = c.up)
                }
            }
            Spacer(Modifier.height(10.dp))
            // Same order as the numbers above it: falls on the right, rises on the left.
            Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (down > 0) Box(Modifier.weight(down.toFloat()).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.down))
                if (same > 0) Box(Modifier.weight(same.toFloat()).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.flat))
                if (up > 0) Box(Modifier.weight(up.toFloat()).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.up))
            }
        }
    }
}

@Composable
private fun MoversSection(market: Overview) {
    val app = LocalApp.current
    var which by rememberSaveable { mutableIntStateOf(0) }
    val lists = listOf(market.movers.gainers, market.movers.losers, market.movers.active)

    SectionTitle("בולטות בת\"א-125")
    Segmented(listOf("עולות", "יורדות", "מחזור גבוה"), which, { which = it }, Modifier.padding(horizontal = 16.dp), tag = "movers")
    Spacer(Modifier.height(10.dp))
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = PaddingValues(vertical = 6.dp)) {
        Column {
            lists[which].forEach { m ->
                MoverRow(m) { app.nav.push(Screen.Security(m.id, m.name)) }
            }
        }
    }
}

@Composable
private fun MoverRow(m: Mover, onClick: () -> Unit) {
    PaperRow(SECURITY, m.companyId, Modifier.clickable(onClick = onClick).testTag("mover-${m.id}")) {
        NameBlock(
            m.name,
            // On the "most traded" list the turnover is the point; it arrives in thousands of shekels.
            m.turnover?.let { "מחזור " + Fmt.bigShekels(it * 1000) },
            Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Num(Fmt.trimmed(m.last), style = NumBody.copy(fontWeight = FontWeight.SemiBold), flashOn = m.last)
        Spacer(Modifier.width(10.dp))
        ChangeChip(m.changePct, style = NumSmall)
    }
}

/** The market tab's own outline while the first load is on its way. */
@Composable
private fun MarketSkeleton() {
    Column {
        Skeleton(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(236.dp), RoundedCornerShape(24.dp))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Skeleton(Modifier.weight(1f).height(104.dp), RoundedCornerShape(18.dp))
            Skeleton(Modifier.weight(1f).height(104.dp), RoundedCornerShape(18.dp))
        }
        Spacer(Modifier.height(18.dp))
        SkeletonRows(5)
    }
}
