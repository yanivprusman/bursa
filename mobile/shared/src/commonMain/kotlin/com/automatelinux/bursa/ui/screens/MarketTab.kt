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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.data.LocalApp
import com.automatelinux.bursa.data.model.Breadth
import com.automatelinux.bursa.data.model.IndexRow
import com.automatelinux.bursa.data.model.Mover
import com.automatelinux.bursa.data.model.Overview
import com.automatelinux.bursa.nav.Screen
import com.automatelinux.bursa.ui.components.Card
import com.automatelinux.bursa.ui.components.ChangeChip
import com.automatelinux.bursa.ui.components.FailedBlock
import com.automatelinux.bursa.ui.components.LoadingBlock
import com.automatelinux.bursa.ui.components.NameBlock
import com.automatelinux.bursa.ui.components.Num
import com.automatelinux.bursa.ui.components.Refreshable
import com.automatelinux.bursa.ui.components.SectionTitle
import com.automatelinux.bursa.ui.components.Sparkline
import com.automatelinux.bursa.ui.components.StaleNote
import com.automatelinux.bursa.ui.theme.Bursa
import com.automatelinux.bursa.ui.theme.NumBody
import com.automatelinux.bursa.ui.theme.NumMedium
import com.automatelinux.bursa.ui.theme.NumSmall
import com.automatelinux.bursa.util.Fmt

@Composable
fun MarketTab() {
    val app = LocalApp.current
    val res = app.overview
    val market = res.data

    Refreshable(res.refreshing, onRefresh = { app.refreshHome(byUser = true) }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            if (market == null) {
                item {
                    val error = res.error
                    if (error != null) FailedBlock(error, onRetry = { res.refresh() }) else LoadingBlock()
                }
                return@LazyColumn
            }
            res.error?.let { item { StaleNote(it, onRetry = { res.refresh() }) } }

            item {
                SectionTitle("מדדים", action = "כל המדדים", onAction = { app.nav.push(Screen.Indices) })
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(market.indices, key = { it.id }) { row ->
                        IndexCard(row) { app.nav.push(Screen.Index(row.id, row.name)) }
                    }
                }
            }

            market.breadth?.let { b -> item { BreadthCard(b) } }

            item { MoversSection(market) }

            if (market.turnovers.isNotEmpty()) {
                item {
                    SectionTitle("מחזורי המסחר", note = Fmt.date(market.tradeDate))
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column {
                            market.turnovers.forEach { t ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    Text(t.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    // Turnovers arrive in thousands of shekels.
                                    Text(Fmt.bigShekels(t.value * 1000), style = MaterialTheme.typography.bodyMedium)
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

@Composable
private fun IndexCard(row: IndexRow, onClick: () -> Unit) {
    Card(Modifier.width(164.dp).testTag("index-card-${row.id}"), onClick = onClick, padding = PaddingValues(14.dp)) {
        Column {
            Text(row.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Num(Fmt.fixed(row.last, 2), style = NumMedium)
            Spacer(Modifier.height(8.dp))
            ChangeChip(row.changePct)
            Spacer(Modifier.height(12.dp))
            // A month of closes. Gold, not green or red: it is a shape, not today's direction.
            Sparkline(row.spark, Bursa.colors.accent, Modifier.fillMaxWidth().height(34.dp))
            Spacer(Modifier.height(4.dp))
            Text("חודש אחרון", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    SectionTitle("ת\"א-125 היום")
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column {
            // Drawn left-to-right like the numbers beside it: rises, unchanged, falls.
            Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
                if (down > 0) Box(Modifier.weight(down.toFloat()).fillMaxHeight().background(c.down))
                if (same > 0) Box(Modifier.weight(same.toFloat()).fillMaxHeight().background(c.flat))
                if (up > 0) Box(Modifier.weight(up.toFloat()).fillMaxHeight().background(c.up))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("$down ירדו", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.down)
                if (same > 0) {
                    Text("$same ללא שינוי", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                }
                Text("$up עלו", style = MaterialTheme.typography.bodyMedium, color = c.up)
            }
        }
    }
}

@Composable
private fun MoversSection(market: Overview) {
    val app = LocalApp.current
    var which by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("עולות" to market.movers.gainers, "יורדות" to market.movers.losers, "מחזור גבוה" to market.movers.active)

    SectionTitle("בולטות בת\"א-125")
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEachIndexed { i, (label, _) ->
            FilterChip(
                selected = which == i,
                onClick = { which = i },
                label = { Text(label) },
                modifier = Modifier.testTag("movers-$i"),
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = PaddingValues(vertical = 6.dp)) {
        Column {
            tabs[which].second.forEach { m ->
                MoverRow(m) { app.nav.push(Screen.Security(m.id, m.name)) }
            }
        }
    }
}

@Composable
private fun MoverRow(m: Mover, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("mover-${m.id}").padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NameBlock(
            m.name,
            // On the "most traded" list the turnover is the point; it arrives in thousands of shekels.
            m.turnover?.let { "מחזור " + Fmt.bigShekels(it * 1000) },
            Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Num(Fmt.trimmed(m.last), style = NumBody)
        Spacer(Modifier.width(12.dp))
        ChangeChip(m.changePct, style = NumSmall)
    }
}
