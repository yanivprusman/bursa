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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.data.LocalApp
import com.automatelinux.bursa.data.model.AccountSummary
import com.automatelinux.bursa.data.model.BUY
import com.automatelinux.bursa.data.model.INDEX
import com.automatelinux.bursa.data.model.Order
import com.automatelinux.bursa.data.model.Position
import com.automatelinux.bursa.data.model.Quote
import com.automatelinux.bursa.data.model.SECURITY
import com.automatelinux.bursa.data.model.Trade
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
    val acc = app.account.summary
    var allActivity by remember { mutableStateOf(false) }

    val holdings = acc?.positions ?: emptyList()
    val heldIds = holdings.map { it.paper.id }.toSet()
    val watching = portfolio.items.filter { !(it.kind == SECURITY && it.id in heldIds) }
    // A holding counts toward the total only once its price is known.
    val valued: List<Pair<Position, HoldingValue>> = holdings.mapNotNull { p ->
        val q = app.quotes[p.paper.key] ?: return@mapNotNull null
        p to holdingValue(p.qty, p.avgCost, q.last, q.base)
    }

    val ideas = suggestions(heldIds)

    Refreshable(app.quotesRefreshing, onRefresh = { app.refreshHome(byUser = true) }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp)) {
            (app.account.error ?: app.quotesError)?.let { item { StaleNote(it, onRetry = { app.refreshHome() }) } }

            if (acc != null) {
                item { AccountCard(acc, portfolioTotals(valued.map { it.second }), valued, unpriced = holdings.size - valued.size) }
                if (holdings.isEmpty() && acc.pending.isEmpty()) item { FirstSteps(acc) }
            } else if (app.account.error == null) {
                item { Welcome() }
            }

            if (holdings.isNotEmpty()) {
                item { SectionTitle("התיק שלי") }
                items(holdings, key = { "h" + it.paper.id }) { p ->
                    HoldingRow(p, app.quotes[p.paper.key], missing = app.quotesMissing[p.paper.key], onClick = { app.nav.push(p.paper.tracked().screen()) })
                }
            }

            if (acc != null && acc.pending.isNotEmpty()) {
                item { SectionTitle("פקודות ממתינות", note = "בשער הפתיחה הבא") }
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = PaddingValues(vertical = 6.dp)) {
                        Column { acc.pending.forEach { o -> OrderRow(o, cancellable = true) } }
                    }
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
                        onRemove = { portfolio.unfollow(t.kind, t.id) },
                    )
                }
            }

            if (portfolio.items.size + holdings.size < SUGGEST_BELOW) {
                if (ideas.isNotEmpty()) {
                    item { SectionTitle(if (portfolio.items.isEmpty()) "התחילו מכאן" else "אולי גם אלה", note = "המדדים המובילים והמניות הנסחרות ביותר היום") }
                    item {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = PaddingValues(vertical = 6.dp)) {
                            Column { ideas.forEach { idea -> SuggestionRow(idea) } }
                        }
                    }
                }
            }

            if (acc != null) {
                val lines = activity(acc)
                if (lines.isNotEmpty()) {
                    item { SectionTitle("פעולות אחרונות", action = if (lines.size > 6) (if (allActivity) "פחות" else "הכול") else null, onAction = { allActivity = !allActivity }) }
                    item {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = PaddingValues(vertical = 6.dp)) {
                            Column {
                                (if (allActivity) lines else lines.take(6)).forEach { l ->
                                    if (l.trade != null) TradeRow(l.trade, showName = true) else OrderRow(l.order!!, cancellable = false)
                                }
                            }
                        }
                    }
                    item { StartOver(acc) }
                }
            }
        }
    }
}

private class ActivityLine(val at: String, val trade: Trade? = null, val order: Order? = null)

/** The broker's statement: trades and the orders that did not fill, newest first. */
private fun activity(a: AccountSummary): List<ActivityLine> =
    (a.trades.map { ActivityLine(it.at, trade = it) } + a.orders.filter { it.status != "filled" }.map { ActivityLine(it.closedAt ?: it.placedAt, order = it) })
        .sortedByDescending { it.at }

/** What an empty practice account says: what it is, and the one thing to do. */
@Composable
private fun FirstSteps(a: AccountSummary) {
    Text(
        "יש לכם \u2066${Fmt.shekels(a.cash / 100)}\u2069 מדומים לתרגול. פתחו נייר — מניה, קרן סל או אג\"ח — ולחצו \"קנייה\". הפקודה מתבצעת בשער האמיתי של הבורסה.",
        Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
                "חשבון התרגול נטען מהשרת. בינתיים אפשר להוסיף ניירות למעקב מהרשימה שלמטה או מהחיפוש.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onHeroMuted,
            )
        }
    }
}

private class Suggestion(val item: Tracked, val last: Double, val changePct: Double?, val unit: String)

/** The headline indices, then today's most traded shares — minus whatever is already followed. */
@Composable
private fun suggestions(heldIds: Set<String>): List<Suggestion> {
    val app = LocalApp.current
    val market = app.overview.data ?: return emptyList()
    val indices = market.indices.take(3).map {
        Suggestion(Tracked(INDEX, it.id, it.name, type = "מדד"), it.last, it.changePct, "points")
    }
    val shares = market.movers.active.take(5).map {
        Suggestion(Tracked(SECURITY, it.id, it.name, type = "מניות", companyId = it.companyId), it.last, it.changePct, "agorot")
    }
    return (indices + shares).filter { !app.portfolio.isTracked(it.item.kind, it.item.id) && !(it.item.kind == SECURITY && it.item.id in heldIds) }.take(6)
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

/** The number the user opened the app for: what the practice account is worth, and what the market did to it. */
@Composable
private fun AccountCard(a: AccountSummary, t: PortfolioTotals, valued: List<Pair<Position, HoldingValue>>, unpriced: Int) {
    val c = Bursa.colors
    fun tone(v: Double) = if (v > 0) HeroUp else if (v < 0) HeroDown else c.onHeroMuted
    val cash = a.cash / 100
    val worth = cash + t.value
    val start = a.startCash / 100
    val sinceStart = worth - start
    @Composable
    fun line(label: String, content: @Composable () -> Unit) {
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium, color = c.onHeroMuted)
            content()
        }
    }
    HeroCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("account-total")) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("שווי החשבון", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = c.onHeroMuted)
                PracticeTag()
            }
            Spacer(Modifier.height(4.dp))
            Num(Fmt.shekels(worth), style = NumHero, color = c.onHero, flashOn = worth)
            Spacer(Modifier.height(10.dp))
            line("מאז ההתחלה") {
                Num(Fmt.signedShekels(sinceStart), style = NumMedium, color = tone(sinceStart))
                Spacer(Modifier.width(8.dp))
                Num("(" + Fmt.pct(sinceStart / start * 100) + ")", style = NumBody, color = tone(sinceStart))
            }
            if (valued.isNotEmpty()) {
                line("היום") {
                    Num(Fmt.signedShekels(t.dayChange), style = NumMedium, color = tone(t.dayChange))
                    if (t.dayChangePct != null) {
                        Spacer(Modifier.width(8.dp))
                        Num("(" + Fmt.pct(t.dayChangePct) + ")", style = NumBody, color = tone(t.dayChange))
                    }
                }
            }
            line("מזומן פנוי") { Num(Fmt.shekels(a.available / 100), style = NumMedium, color = c.onHero) }
            if (a.available != a.cash) {
                Text(
                    "ועוד \u2066${Fmt.shekels((a.cash - a.available) / 100)}\u2069 שמורים לפקודות ממתינות",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onHeroMuted,
                )
            }

            // What the account is made of — cash is a slice too.
            if (valued.isNotEmpty() && worth > 0) {
                val slices = valued.sortedByDescending { it.second.value }
                val named = slices.take(SLICES.size - 2)
                val rest = slices.drop(SLICES.size - 2)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    slices.forEachIndexed { i, (_, v) ->
                        if (v.value > 0) Box(Modifier.weight(v.value.toFloat()).fillMaxHeight().background(SLICES[minOf(i, SLICES.size - 2)]))
                    }
                    if (cash > 0) Box(Modifier.weight(cash.toFloat()).fillMaxHeight().background(SLICES.last()))
                }
                Spacer(Modifier.height(10.dp))
                @Composable
                fun slice(color: Color, name: String, value: Double) {
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.onHero, maxLines = 1)
                        Num(Fmt.fixed(value / worth * 100, 1) + "%", style = NumSmall, color = c.onHeroMuted)
                    }
                }
                named.forEachIndexed { i, (p, v) -> slice(SLICES[i], p.paper.name, v.value) }
                if (rest.isNotEmpty()) slice(SLICES[SLICES.size - 2], "עוד ${rest.size}", rest.sumOf { it.second.value })
                slice(SLICES.last(), "מזומן", cash)
            }
            if (unpriced > 0) {
                Spacer(Modifier.height(10.dp))
                Text("$unpriced החזקות בלי מחיר עדכני אינן בסכום", style = MaterialTheme.typography.bodySmall, color = c.onHeroMuted)
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
                .combinedClickable(onClick = onClick, onLongClick = if (menu.isEmpty()) null else ({ open = true }))
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
private fun HoldingRow(p: Position, q: Quote?, missing: String?, onClick: () -> Unit) {
    val c = Bursa.colors
    RowShell(p.paper.tracked(), "holding-${p.paper.id}", onClick, emptyList()) {
        val v = q?.let { holdingValue(p.qty, p.avgCost, it.last, it.base) }
        Column(Modifier.weight(1f)) {
            NameBlock(p.paper.name, null)
            Spacer(Modifier.height(2.dp))
            if (q != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Num(Fmt.quantity(p.qty) + " × " + Fmt.trimmed(q.last), style = NumSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Num(v?.gainPct?.let { Fmt.pct(it) } ?: "", style = NumSmall, color = c.of(v?.gain))
                }
            } else {
                Text(missing ?: "ממתין למחיר…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (v != null) {
            Column(horizontalAlignment = Alignment.End) {
                Num(Fmt.shekels(v.value), style = NumMedium, flashOn = v.value)
                if (v.dayChange != null) Num(Fmt.signedShekels(v.dayChange), style = NumSmall, color = c.of(v.dayChange))
            }
        }
    }
}

/** "קנייה" / "מכירה" on a small tinted pill — gold to buy, blue to sell; never the up/down colours. */
@Composable
fun SidePill(side: String) {
    val color = if (side == BUY) Bursa.colors.accent else SellBlue
    Text(
        if (side == BUY) "קנייה" else "מכירה",
        Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = color,
    )
}

/** A waiting (or cancelled / rejected) order; [cancellable] for a waiting one the owner may cancel. */
@Composable
fun OrderRow(o: Order, cancellable: Boolean) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    Row(
        Modifier.fillMaxWidth().clickable { app.nav.push(o.paper.tracked().screen()) }.padding(horizontal = 16.dp, vertical = 8.dp).testTag("order-${o.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoTile(SECURITY, o.paper.companyId, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SidePill(o.side)
                Spacer(Modifier.width(6.dp))
                Text(o.paper.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            }
            Text(
                when (o.status) {
                    "pending" -> "\u2066${Fmt.fixed(o.qty, 0)}\u2069 · בפתיחה, מ-\u2066${Fmt.dayMonth(o.fillFrom)}\u2069"
                    "cancelled" -> "\u2066${Fmt.fixed(o.qty, 0)}\u2069 · בוטלה · \u2066${stamp(o.closedAt ?: o.placedAt)}\u2069"
                    else -> "נדחתה — ${o.reason ?: ""} · \u2066${stamp(o.closedAt ?: o.placedAt)}\u2069"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        if (cancellable) {
            TextButton(
                onClick = { scope.launch { runCatching { app.account.cancel(o.id) } } },
                modifier = Modifier.testTag("cancel-order-${o.id}"),
            ) { Text("ביטול", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun TradeRow(t: Trade, showName: Boolean) {
    val app = LocalApp.current
    val paid = if (t.side == BUY) t.gross + t.fee else t.gross - t.fee
    Row(
        Modifier.fillMaxWidth().clickable { app.nav.push(t.paper.tracked().screen()) }.padding(horizontal = 16.dp, vertical = 8.dp).testTag("trade-${t.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SidePill(t.side)
                if (showName) {
                    Spacer(Modifier.width(6.dp))
                    Text(t.paper.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                }
            }
            Text(
                "\u2066${Fmt.fixed(t.qty, 0)} × ${Fmt.trimmed(t.price)}\u2069" + (if (t.how == "open") " · שער פתיחה" else "") + " · \u2066${stamp(t.at)}\u2069",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Num((if (t.side == BUY) "-" else "+") + Fmt.shekels(paid / 100), style = NumSmall, color = if (t.side == BUY) MaterialTheme.colorScheme.onSurface else Bursa.colors.up)
    }
}

/** "2.10 14:27" in Israel time. */
private fun stamp(iso: String): String = runCatching {
    val d = Instant.parse(iso).toLocalDateTime(TimeZone.of("Asia/Jerusalem"))
    "${d.dayOfMonth}.${d.monthNumber} ${d.hour.toString().padStart(2, '0')}:${d.minute.toString().padStart(2, '0')}"
}.getOrDefault("")

/** Back to the starting cash. The old account is kept on the server, but it is still a big step — so it asks. */
@Composable
private fun StartOver(a: AccountSummary) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)) {
        if (asking) {
            Text("למחוק את כל העסקאות ולהתחיל שוב מ-\u2066${Fmt.shekels(a.startCash / 100)}\u2069?", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = { scope.launch { runCatching { app.account.reset() }; asking = false } }, modifier = Modifier.testTag("start-over-confirm")) {
                    Text("כן, מההתחלה", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { asking = false }, modifier = Modifier.testTag("start-over-cancel")) { Text("לא") }
            }
        } else {
            TextButton(onClick = { asking = true }, modifier = Modifier.testTag("start-over")) { Text("התחלה מחדש של חשבון התרגול", color = Bursa.colors.accent) }
        }
    }
}

@Composable
private fun WatchRow(t: Tracked, q: Quote?, missing: String?, onClick: () -> Unit, onRemove: () -> Unit) {
    val menu = listOf("הסרה מהמעקב" to onRemove)
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

/** The holding in place on the security page: worth, today, since buying, cost. */
@Composable
fun PositionCard(p: Position, q: Quote, modifier: Modifier = Modifier) {
    val c = Bursa.colors
    val v = holdingValue(p.qty, p.avgCost, q.last, q.base)
    @Composable
    fun line(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Num(value, style = NumBody, color = color)
        }
    }
    Card(modifier.fillMaxWidth().testTag("position-card")) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ההחזקה שלי", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                PracticeTag()
            }
            Spacer(Modifier.height(8.dp))
            Num(Fmt.shekels(v.value), style = NumLarge, flashOn = v.value)
            Spacer(Modifier.height(2.dp))
            Num(Fmt.quantity(p.qty) + " × " + Fmt.trimmed(q.last), style = NumSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            if (v.dayChange != null) line("היום", Fmt.signedShekels(v.dayChange), c.of(v.dayChange))
            if (v.gain != null) line("מאז הקנייה", Fmt.signedShekels(v.gain) + (v.gainPct?.let { "  (" + Fmt.pct(it) + ")" } ?: ""), c.of(v.gain))
            line("שער קנייה ממוצע", Fmt.trimmed(p.avgCost) + " אג'")
            line("עלות כולל עמלות", Fmt.shekels(p.cost / 100))
        }
    }
}
