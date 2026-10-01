package com.automatelinux.bursa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.data.LocalApp
import com.automatelinux.bursa.data.model.Component
import com.automatelinux.bursa.data.model.INDEX
import com.automatelinux.bursa.data.model.IndexDetail
import com.automatelinux.bursa.data.model.Tracked
import com.automatelinux.bursa.data.rememberResource
import com.automatelinux.bursa.nav.Screen
import com.automatelinux.bursa.ui.components.Card
import com.automatelinux.bursa.ui.components.ChangeChip
import com.automatelinux.bursa.ui.components.Fact
import com.automatelinux.bursa.ui.components.FactGrid
import com.automatelinux.bursa.ui.components.FailedBlock
import com.automatelinux.bursa.ui.components.LoadingBlock
import com.automatelinux.bursa.ui.components.NameBlock
import com.automatelinux.bursa.ui.components.Num
import com.automatelinux.bursa.ui.components.Refreshable
import com.automatelinux.bursa.ui.components.SectionTitle
import com.automatelinux.bursa.ui.components.StaleNote
import com.automatelinux.bursa.ui.theme.Bursa
import com.automatelinux.bursa.ui.theme.NumBody
import com.automatelinux.bursa.ui.theme.of
import com.automatelinux.bursa.util.Fmt

@Composable
fun IndexScreen(id: String, name: String) {
    val app = LocalApp.current
    val portfolio = app.portfolio
    val res = rememberResource("/api/index/$id", IndexDetail.serializer(), live = { app.marketOpen })
    val d = res.data
    val self = Tracked(INDEX, id, name, type = "מדד")
    val followed = portfolio.isTracked(INDEX, id)
    var order by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(id) { portfolio.opened(self) }
    LaunchedEffect(d) { d?.let { app.learn(it.quote()) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            DetailBar(name, followed) {
                if (followed) portfolio.unfollow(INDEX, id) else { portfolio.follow(self); app.refreshQuotes() }
            }
        },
    ) { pad ->
        Refreshable(res.refreshing, onRefresh = { res.refresh(byUser = true) }, modifier = Modifier.padding(pad)) {
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (d == null) {
                    item {
                        val error = res.error
                        if (error != null) FailedBlock(error, onRetry = { res.refresh() }) else LoadingBlock()
                    }
                    return@LazyColumn
                }
                res.error?.let { item { StaleNote(it, onRetry = { res.refresh() }) } }

                item {
                    PriceHeader(d.quote(), d.category)
                    Spacer(Modifier.height(14.dp))
                    ChartBlock(INDEX, id, d.unit)
                }

                item { SectionTitle("נתוני המדד", note = Fmt.date(d.tradeDate)) }
                item { Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { IndexFacts(d) } }

                val about = d.about
                if (about != null) {
                    item {
                        Text(
                            about,
                            Modifier.padding(horizontal = 20.dp).padding(top = 14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (d.components.isNotEmpty()) {
                    val sorted = when (order) {
                        1 -> d.components.sortedByDescending { it.changePct ?: Double.NEGATIVE_INFINITY }
                        2 -> d.components.sortedBy { it.changePct ?: Double.POSITIVE_INFINITY }
                        else -> d.components
                    }
                    item {
                        SectionTitle("${d.components.size} ניירות במדד")
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("לפי משקל", "העולות", "היורדות").forEachIndexed { i, label ->
                                FilterChip(selected = order == i, onClick = { order = i }, label = { Text(label) }, modifier = Modifier.testTag("order-$i"))
                            }
                        }
                    }
                    items(sorted, key = { it.id }) { c ->
                        ComponentRow(c) { app.nav.push(Screen.Security(c.id, c.name)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentRow(c: Component, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("component-${c.id}").padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NameBlock(c.name, c.weight?.let { "⁦${Fmt.fixed(it, 2)}%⁩ מהמדד" }, Modifier.weight(1f))
        if (c.last != null) {
            Spacer(Modifier.width(12.dp))
            Num(Fmt.trimmed(c.last), style = NumBody)
        }
        Spacer(Modifier.width(12.dp))
        ChangeChip(c.changePct)
    }
}

@Composable
private fun IndexFacts(d: IndexDetail) {
    val colors = Bursa.colors
    val cells = buildList<@Composable (Modifier) -> Unit> {
        fun points(label: String, v: Double?) {
            if (v != null) add { m -> Fact(label, Fmt.fixed(v, 2), m) }
        }
        fun pct(label: String, v: Double?) {
            if (v != null) add { m -> Fact(label, Fmt.pct(v), m, color = colors.of(v)) }
        }
        points("פתיחה", d.open)
        points("בסיס", d.base)
        points("גבוה יומי", d.high)
        points("נמוך יומי", d.low)
        pct("מתחילת החודש", d.monthYield)
        pct("מתחילת השנה", d.yearYield)
        if (d.gainers != null) add { m -> Fact("עלו היום", d.gainers.toString(), m, color = colors.up) }
        if (d.decliners != null) add { m -> Fact("ירדו היום", d.decliners.toString(), m, color = colors.down) }
        // Index market cap arrives in millions of shekels.
        if (d.marketCap != null && d.marketCap > 0) add { m -> Fact("שווי שוק", Fmt.bigShekels(d.marketCap * 1e6), m, numeric = false) }
    }
    FactGrid(cells)
}
