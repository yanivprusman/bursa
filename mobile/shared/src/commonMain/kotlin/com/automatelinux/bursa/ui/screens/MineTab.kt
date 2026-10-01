package com.automatelinux.bursa.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.data.LocalApp
import com.automatelinux.bursa.data.model.INDEX
import com.automatelinux.bursa.data.model.Quote
import com.automatelinux.bursa.data.model.Tracked
import com.automatelinux.bursa.nav.Screen
import com.automatelinux.bursa.ui.components.Card
import com.automatelinux.bursa.ui.components.ChangeChip
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

@Composable
fun MineTab() {
    val app = LocalApp.current
    val portfolio = app.portfolio
    var editing by remember { mutableStateOf<Tracked?>(null) }
    var removing by remember { mutableStateOf<Tracked?>(null) }

    if (portfolio.items.isEmpty()) {
        EmptyMine { app.nav.push(Screen.Search) }
        return
    }

    val holdings = portfolio.holdings
    val watching = portfolio.watching
    // A holding counts toward the total only once its price is known.
    val valued: List<Pair<Tracked, HoldingValue>> = holdings.mapNotNull { t ->
        val q = app.quotes[t.key] ?: return@mapNotNull null
        t to holdingValue(t.qty ?: 0.0, t.avgCost, q.last, q.base)
    }

    Refreshable(app.quotesRefreshing, onRefresh = { app.refreshHome(byUser = true) }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            app.quotesError?.let { item { StaleNote(it, onRetry = { app.refreshQuotes() }) } }

            if (holdings.isNotEmpty()) {
                item { TotalsCard(portfolioTotals(valued.map { it.second }), unpriced = holdings.size - valued.size) }
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

            if (holdings.isEmpty()) {
                item {
                    Text(
                        "מחזיקים באחד מהם? פתחו את הנייר והזינו כמה יחידות — ושווי התיק יופיע כאן.",
                        Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    editing?.let { t -> HoldingSheet(t, app.quotes[t.key]?.last, onDone = { editing = null }) }
    removing?.let { t ->
        ConfirmRemoveHolding(t, onConfirm = { portfolio.unfollow(t.kind, t.id); removing = null }, onDismiss = { removing = null })
    }
}

@Composable
private fun EmptyMine(onSearch: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.StarBorder, null, Modifier.size(52.dp), tint = Bursa.colors.accent)
        Spacer(Modifier.height(18.dp))
        Text("הרשימה שלך ריקה", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "חפשו מניה, קרן סל, אג\"ח או מדד והוסיפו למעקב. הזינו כמה אתם מחזיקים — ותראו כאן את שווי התיק ואת השינוי היומי בשקלים.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Button(onClick = onSearch, modifier = Modifier.testTag("empty-search")) {
            Icon(Icons.Filled.Search, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("חיפוש נייר")
        }
    }
}

/** The number the user opened the app for: what it is all worth, and what today did to it. */
@Composable
private fun TotalsCard(t: PortfolioTotals, unpriced: Int) {
    val c = Bursa.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.large)
            .background(c.hero)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Text("שווי התיק", style = MaterialTheme.typography.labelLarge, color = c.onHeroMuted)
        Spacer(Modifier.height(4.dp))
        Num(Fmt.shekels(t.value), style = NumHero, color = c.onHero)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("היום", style = MaterialTheme.typography.bodyMedium, color = c.onHeroMuted)
            Spacer(Modifier.width(10.dp))
            Num(Fmt.signedShekels(t.dayChange), style = NumMedium, color = heroTone(t.dayChange))
            if (t.dayChangePct != null) {
                Spacer(Modifier.width(8.dp))
                Num("(" + Fmt.pct(t.dayChangePct) + ")", style = NumBody, color = heroTone(t.dayChange))
            }
        }
        if (t.gain != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (t.gainIsPartial) "מאז הקנייה*" else "מאז הקנייה", style = MaterialTheme.typography.bodyMedium, color = c.onHeroMuted)
                Spacer(Modifier.width(10.dp))
                Num(Fmt.signedShekels(t.gain), style = NumMedium, color = heroTone(t.gain))
                if (t.gainPct != null) {
                    Spacer(Modifier.width(8.dp))
                    Num("(" + Fmt.pct(t.gainPct) + ")", style = NumBody, color = heroTone(t.gain))
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

/** The hero band is dark in both themes, so it always takes the bright up/down pair. */
@Composable
private fun heroTone(v: Double) = when {
    v > 0 -> Color(0xFF3DD68C)
    v < 0 -> Color(0xFFFF7B7B)
    else -> Bursa.colors.onHeroMuted
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowShell(tag: String, onClick: () -> Unit, menu: List<Pair<String, () -> Unit>>, content: @Composable RowScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { open = true })
                .testTag(tag)
                .padding(horizontal = 20.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
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
    RowShell("holding-${t.id}", onClick, listOf("עריכת ההחזקה" to onEdit, "הסרה מהרשימה" to onRemove)) {
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
                Num(Fmt.shekels(v.value), style = NumMedium)
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
    RowShell("watch-${t.id}", onClick, menu) {
        NameBlock(t.name, if (q == null) (missing ?: "ממתין למחיר…") else t.type, Modifier.weight(1f))
        if (q != null) {
            Spacer(Modifier.width(12.dp))
            Num(Fmt.price(q.last, q.unit), style = NumBody)
            Spacer(Modifier.width(12.dp))
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
                Text("עריכה", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Num(Fmt.shekels(v.value), style = NumLarge)
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
