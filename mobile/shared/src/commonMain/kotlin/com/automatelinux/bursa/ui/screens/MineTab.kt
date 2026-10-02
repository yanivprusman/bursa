package com.automatelinux.bursa.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.data.LocalApp
import com.automatelinux.bursa.data.model.INDEX
import com.automatelinux.bursa.data.model.Quote
import com.automatelinux.bursa.data.model.SECURITY
import com.automatelinux.bursa.data.model.Tracked
import com.automatelinux.bursa.nav.Screen
import com.automatelinux.bursa.ui.components.Card
import com.automatelinux.bursa.ui.components.ChangeChip
import com.automatelinux.bursa.ui.components.HeroCard
import com.automatelinux.bursa.ui.components.LogoTile
import com.automatelinux.bursa.ui.components.NameBlock
import com.automatelinux.bursa.ui.components.Num
import com.automatelinux.bursa.ui.components.Refreshable
import com.automatelinux.bursa.ui.components.SectionTitle
import com.automatelinux.bursa.ui.components.StaleNote
import com.automatelinux.bursa.ui.theme.Bursa
import com.automatelinux.bursa.ui.theme.NumBody
import com.automatelinux.bursa.ui.theme.NumHero
import com.automatelinux.bursa.ui.theme.NumLarge
import com.automatelinux.bursa.ui.theme.NumMedium
import com.automatelinux.bursa.ui.theme.NumSmall
import com.automatelinux.bursa.ui.theme.of
import com.automatelinux.bursa.util.Fmt
import com.automatelinux.bursa.util.HoldingValue
import com.automatelinux.bursa.util.PortfolioTotals
import com.automatelinux.bursa.util.holdingValue
import com.automatelinux.bursa.util.portfolioTotals

fun Tracked.screen(): Screen = if (kind == INDEX) Screen.Index(id, name) else Screen.Security(id, name)

// The bright pair for the dark hero band, in either theme.
private val HeroUp = Color(0xFF3DD68C)
private val HeroDown = Color(0xFFFF7B7B)

/** One colour per holding in the "what the portfolio is made of" bar; the tail shares the last. */
private val SLICES = listOf(Color(0xFFF2B84B), Color(0xFF6FA8FF), Color(0xFF3DD68C), Color(0xFFE58CCB), Color(0xFF9B8CFF), Color(0xFF7C8AA3))

/** Below this many papers the tab still has room, and uses it to suggest a few more. */
private const val SUGGEST_BELOW = 4

@Composable
fun MineTab() {
    val app = LocalApp.current
    val portfolio = app.portfolio
    var editing by remember { mutableStateOf<Tracked?>(null) }
    var removing by remember { mutableStateOf<Tracked?>(null) }

    val holdings = portfolio.holdings
    val watching = portfolio.watching
    // A holding counts toward the total only once its price is known.
    val valued: List<Pair<Tracked, HoldingValue>> = holdings.mapNotNull { t ->
        val q = app.quotes[t.key] ?: return@mapNotNull null
        t to holdingValue(t.qty ?: 0.0, t.avgCost, q.last, q.base)
    }

    val ideas = suggestions()

    Refreshable(app.quotesRefreshing, onRefresh = { app.refreshHome(byUser = true) }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp)) {
            app.quotesError?.let { item { StaleNote(it, onRetry = { app.refreshQuotes() }) } }

            if (portfolio.items.isEmpty()) item { Welcome() }

            if (holdings.isNotEmpty()) {
                item { TotalsCard(portfolioTotals(valued.map { it.second }), valued, unpriced = holdings.size - valued.size) }
                item { SectionTitle("התיק שלי") }
                items(holdings, key = { "h" + it.key }) { t ->
                    HoldingRow(
                        t,
                        app.quotes[t.key],
                        missing = app.quotesMissing[t.key],
                        onClick = { app.nav.push(t.screen()) },
                        onEdit = { editing = t },
                        // A holding is something the user typed in; it is never dropped on one tap.
                        onRemove = { removing = t },
                    )
                }
            }

            if (watching.isNotEmpty()) {
                item { SectionTitle("במעקב") }
                items(watching, key = { "w" + it.key }) { t ->
                    WatchRow(
                        t,
                        app.quotes[t.key],
                        missing = app.quotesMissing[t.key],
                        onClick = { app.nav.push(t.screen()) },
                        onHold = if (t.kind == INDEX) null else ({ editing = t }),
                        onRemove = { portfolio.unfollow(t.kind, t.id) },
                    )
                }
            }

            if (portfolio.items.isNotEmpty() && holdings.isEmpty()) {
                item {
                    Text(
                        "מחזיקים באחד מהם? לחיצה ארוכה על השורה ← \"הוספת החזקה\", ושווי התיק יופיע כאן למעלה.",
                        Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (portfolio.items.size < SUGGEST_BELOW) {
                if (ideas.isNotEmpty()) {
                    item { SectionTitle(if (portfolio.items.isEmpty()) "התחילו מכאן" else "אולי גם אלה", note = "המדדים המובילים והמניות הנסחרות ביותר היום") }
                    item {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = PaddingValues(vertical = 6.dp)) {
                            Column { ideas.forEach { idea -> SuggestionRow(idea) } }
                        }
                    }
                }
            }
        }
    }

    editing?.let { t -> HoldingSheet(t, app.quotes[t.key]?.last, onDone = { editing = null }) }
    removing?.let { t ->
        ConfirmRemoveHolding(t, onConfirm = { portfolio.unfollow(t.kind, t.id); removing = null }, onDismiss = { removing = null })
    }
}

/** What a first-time user sees above the suggestions: what this tab will become. */
@Composable
private fun Welcome() {
    val c = Bursa.colors
    HeroCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
            Text("הרשימה שלך", style = MaterialTheme.typography.titleLarge, color = c.onHero)
            Spacer(Modifier.height(6.dp))
            Text(
                "הוסיפו ניירות למעקב בלחיצה על +. הזינו כמה אתם מחזיקים — וכאן יופיעו שווי התיק והשינוי היומי בשקלים.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onHeroMuted,
            )
        }
    }
}

private class Suggestion(val item: Tracked, val last: Double, val changePct: Double?, val unit: String)

/** The headline indices, then today's most traded shares — minus whatever is already followed. */
@Composable
private fun suggestions(): List<Suggestion> {
    val app = LocalApp.current
    val market = app.overview.data ?: return emptyList()
    val indices = market.indices.take(3).map {
        Suggestion(Tracked(INDEX, it.id, it.name, type = "מדד"), it.last, it.changePct, "points")
    }
    val shares = market.movers.active.take(5).map {
        Suggestion(Tracked(SECURITY, it.id, it.name, type = "מניות", companyId = it.companyId), it.last, it.changePct, "agorot")
    }
    return (indices + shares).filter { !app.portfolio.isTracked(it.item.kind, it.item.id) }.take(6)
}

@Composable
private fun SuggestionRow(s: Suggestion) {
    val app = LocalApp.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoTile(s.item.kind, s.item.companyId)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NameBlock(s.item.name, null)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Num(Fmt.price(s.last, s.unit), style = NumSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Num(s.changePct?.let { Fmt.pct(it) } ?: "", style = NumSmall, color = Bursa.colors.of(s.changePct))
            }
        }
        FollowButton(followed = false, tag = "suggest-${s.item.key}") {
            app.portfolio.follow(s.item)
            app.refreshQuotes()
        }
    }
}

/** The round + that follows a paper, and the ✓ it becomes. */
@Composable
fun FollowButton(followed: Boolean, tag: String, onClick: () -> Unit) {
    val c = Bursa.colors
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (followed) c.accent else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (followed) Icons.Filled.Check else Icons.Filled.Add,
            contentDescription = if (followed) "הסרה מהמעקב" else "הוספה למעקב",
            modifier = Modifier.size(20.dp),
            tint = if (followed) Color(0xFF2B1D00) else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The number the user opened the app for: what it is all worth, and what today did to it. */
@Composable
private fun TotalsCard(t: PortfolioTotals, valued: List<Pair<Tracked, HoldingValue>>, unpriced: Int) {
    val c = Bursa.colors
    fun tone(v: Double) = if (v > 0) HeroUp else if (v < 0) HeroDown else c.onHeroMuted
    HeroCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("portfolio-total")) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text("שווי התיק", style = MaterialTheme.typography.labelLarge, color = c.onHeroMuted)
            Spacer(Modifier.height(4.dp))
            Num(Fmt.shekels(t.value), style = NumHero, color = c.onHero, flashOn = t.value)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("היום", Modifier.width(86.dp), style = MaterialTheme.typography.bodyMedium, color = c.onHeroMuted)
                Num(Fmt.signedShekels(t.dayChange), style = NumMedium, color = tone(t.dayChange))
                if (t.dayChangePct != null) {
                    Spacer(Modifier.width(8.dp))
                    Num("(" + Fmt.pct(t.dayChangePct) + ")", style = NumBody, color = tone(t.dayChange))
                }
            }
            if (t.gain != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (t.gainIsPartial) "מאז הקנייה*" else "מאז הקנייה",
                        Modifier.width(86.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onHeroMuted,
                    )
                    Num(Fmt.signedShekels(t.gain), style = NumMedium, color = tone(t.gain))
                    if (t.gainPct != null) {
                        Spacer(Modifier.width(8.dp))
                        Num("(" + Fmt.pct(t.gainPct) + ")", style = NumBody, color = tone(t.gain))
                    }
                }
            }

            // What the portfolio is made of — worth showing once there is more than one thing in it.
            if (valued.size > 1 && t.value > 0) {
                val slices = valued.sortedByDescending { it.second.value }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    slices.forEachIndexed { i, (_, v) ->
                        if (v.value > 0) {
                            Box(Modifier.weight(v.value.toFloat()).fillMaxHeight().background(SLICES[minOf(i, SLICES.lastIndex)]))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                slices.take(SLICES.size - 1).forEachIndexed { i, (item, v) ->
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(SLICES[i]))
                        Spacer(Modifier.width(8.dp))
                        Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.onHero, maxLines = 1)
                        Num(Fmt.fixed(v.value / t.value * 100, 1) + "%", style = NumSmall, color = c.onHeroMuted)
                    }
                }
                if (slices.size > SLICES.size - 1) {
                    val rest = slices.drop(SLICES.size - 1)
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(SLICES.last()))
                        Spacer(Modifier.width(8.dp))
                        Text("עוד ${rest.size}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.onHero)
                        Num(Fmt.fixed(rest.sumOf { it.second.value } / t.value * 100, 1) + "%", style = NumSmall, color = c.onHeroMuted)
                    }
                }
            }

            val notes = buildList {
                if (t.gainIsPartial) add("* רק החזקות שהוזן להן מחיר קנייה")
                if (unpriced > 0) add("$unpriced החזקות בלי מחיר עדכני אינן בסכום")
            }
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.onHeroMuted)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowShell(t: Tracked, tag: String, onClick: () -> Unit, menu: List<Pair<String, () -> Unit>>, content: @Composable RowScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { open = true })
                .testTag(tag)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogoTile(t.kind, t.companyId ?: LocalApp.current.quotes[t.key]?.companyId)
            Spacer(Modifier.width(12.dp))
            content()
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menu.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
            }
        }
    }
}

@Composable
private fun HoldingRow(t: Tracked, q: Quote?, missing: String?, onClick: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    val c = Bursa.colors
    val qty = t.qty ?: 0.0
    RowShell(t, "holding-${t.id}", onClick, listOf("עריכת ההחזקה" to onEdit, "הסרה מהרשימה" to onRemove)) {
        Column(Modifier.weight(1f)) {
            NameBlock(t.name, null)
            Spacer(Modifier.height(2.dp))
            if (q != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Num(Fmt.quantity(qty) + " × " + Fmt.trimmed(q.last), style = NumSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Num(q.changePct?.let { Fmt.pct(it) } ?: "", style = NumSmall, color = c.of(q.changePct))
                }
            } else {
                Text(missing ?: "ממתין למחיר…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (q != null) {
            val v = holdingValue(qty, t.avgCost, q.last, q.base)
            Column(horizontalAlignment = Alignment.End) {
                Num(Fmt.shekels(v.value), style = NumMedium, flashOn = v.value)
                if (v.dayChange != null) Num(Fmt.signedShekels(v.dayChange), style = NumSmall, color = c.of(v.dayChange))
            }
        }
    }
}

@Composable
private fun WatchRow(t: Tracked, q: Quote?, missing: String?, onClick: () -> Unit, onHold: (() -> Unit)?, onRemove: () -> Unit) {
    val menu = buildList {
        if (onHold != null) add("הוספת החזקה" to onHold)
        add("הסרה מהמעקב" to onRemove)
    }
    RowShell(t, "watch-${t.id}", onClick, menu) {
        NameBlock(t.name, if (q == null) (missing ?: "ממתין למחיר…") else t.type, Modifier.weight(1f))
        if (q != null) {
            Spacer(Modifier.width(10.dp))
            Num(Fmt.price(q.last, q.unit), style = NumBody.copy(fontWeight = FontWeight.SemiBold), flashOn = q.last)
            Spacer(Modifier.width(10.dp))
            ChangeChip(q.changePct)
        }
    }
}

/** The card other screens reuse to show a holding in place (the security page). */
@Composable
fun HoldingCard(t: Tracked, q: Quote, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val c = Bursa.colors
    val v = holdingValue(t.qty ?: 0.0, t.avgCost, q.last, q.base)
    Card(modifier.fillMaxWidth().testTag("holding-card"), onClick = onEdit) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ההחזקה שלי", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text("עריכה", style = MaterialTheme.typography.labelLarge, color = c.accent)
            }
            Spacer(Modifier.height(8.dp))
            Num(Fmt.shekels(v.value), style = NumLarge, flashOn = v.value)
            Spacer(Modifier.height(2.dp))
            Num(Fmt.quantity(t.qty ?: 0.0) + " × " + Fmt.trimmed(q.last), style = NumSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            if (v.dayChange != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("היום", Modifier.width(92.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Num(Fmt.signedShekels(v.dayChange), style = NumBody, color = c.of(v.dayChange))
                }
            }
            if (v.gain != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("מאז הקנייה", Modifier.width(92.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Num(
                        Fmt.signedShekels(v.gain) + (v.gainPct?.let { "  (" + Fmt.pct(it) + ")" } ?: ""),
                        style = NumBody,
                        color = c.of(v.gain),
                    )
                }
            }
        }
    }
}
