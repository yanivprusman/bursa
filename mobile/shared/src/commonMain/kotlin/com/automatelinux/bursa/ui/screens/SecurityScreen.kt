package com.automatelinux.bursa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.data.LocalApp
import com.automatelinux.bursa.data.model.SECURITY
import com.automatelinux.bursa.data.model.SecurityDetail
import com.automatelinux.bursa.data.model.Tracked
import com.automatelinux.bursa.data.rememberResource
import com.automatelinux.bursa.ui.components.Card
import com.automatelinux.bursa.ui.components.Fact
import com.automatelinux.bursa.ui.components.FactGrid
import com.automatelinux.bursa.ui.components.FailedBlock
import com.automatelinux.bursa.ui.components.DayRange
import com.automatelinux.bursa.ui.components.Refreshable
import com.automatelinux.bursa.ui.components.SectionTitle
import com.automatelinux.bursa.ui.components.StaleNote
import com.automatelinux.bursa.ui.theme.Bursa
import com.automatelinux.bursa.ui.theme.of
import com.automatelinux.bursa.util.Fmt

@Composable
fun SecurityScreen(id: String, name: String) {
    val app = LocalApp.current
    val portfolio = app.portfolio
    val res = rememberResource("/api/security/$id", SecurityDetail.serializer(), live = { app.marketOpen })
    val d = res.data
    val tracked = portfolio.find(SECURITY, id)
    // What gets stored when the user follows or holds this paper. The name it was opened
    // under wins: search calls an ETF by its full name, the quote by a clipped one.
    val self = Tracked(SECURITY, id, name, d?.symbol, d?.type, companyId = d?.companyId)

    var editing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }

    LaunchedEffect(id) { portfolio.opened(Tracked(SECURITY, id, name)) }
    LaunchedEffect(d) { d?.let { app.learn(it.quote()) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            DetailBar(name, SECURITY, d?.companyId ?: tracked?.companyId, followed = tracked != null) {
                when {
                    tracked == null -> { portfolio.follow(self); app.refreshQuotes() }
                    tracked.held -> removing = true
                    else -> portfolio.unfollow(SECURITY, id)
                }
            }
        },
    ) { pad ->
        Refreshable(res.refreshing, onRefresh = { res.refresh(byUser = true) }, modifier = Modifier.padding(pad)) {
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (d == null) {
                    item {
                        val error = res.error
                        if (error != null) FailedBlock(error, onRetry = { res.refresh() }) else DetailSkeleton()
                    }
                    return@LazyColumn
                }
                res.error?.let { item { StaleNote(it, onRetry = { res.refresh() }) } }

                item {
                    PriceHeader(d.quote(), listOfNotNull(d.longName, d.subType ?: d.type).joinToString(" · "))
                    Spacer(Modifier.height(14.dp))
                    ChartBlock(SECURITY, id, d.unit)
                }

                item {
                    Box(Modifier.padding(horizontal = 16.dp).padding(top = 18.dp)) {
                        if (tracked?.held == true) {
                            HoldingCard(tracked, d.quote(), onEdit = { editing = true })
                        } else {
                            OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth().testTag("add-holding")) {
                                Text("יש לי מהנייר הזה — הוספת החזקה")
                            }
                        }
                    }
                }

                item { SectionTitle("נתוני מסחר", note = Fmt.date(d.tradeDate)) }
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column {
                            val low = d.low
                            val high = d.high
                            if (low != null && high != null && high > low) {
                                DayRange(low, high, d.last, d.unit, Bursa.colors.of(d.changePct))
                                Spacer(Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(4.dp))
                            }
                            TradingFacts(d)
                        }
                    }
                }

                val about = d.about
                if (about != null) {
                    item { SectionTitle("על החברה") }
                    item {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Column {
                                if (d.sector != null) {
                                    Text(d.sector, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(6.dp))
                                }
                                Text(about, style = MaterialTheme.typography.bodyMedium)
                                val site = d.site
                                if (site != null) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "\u2066$site\u2069",
                                        Modifier.clickable { app.platform.openUrl(if (site.startsWith("http")) site else "https://$site") }.testTag("company-site"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Bursa.colors.accent,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editing) HoldingSheet(tracked ?: self, d?.last, onDone = { editing = false })
    if (removing && tracked != null) {
        ConfirmRemoveHolding(tracked, onConfirm = { portfolio.unfollow(SECURITY, id); removing = false }, onDismiss = { removing = false })
    }
}

@Composable
private fun TradingFacts(d: SecurityDetail) {
    val c = Bursa.colors
    val cells = buildList<@Composable (Modifier) -> Unit> {
        fun price(label: String, v: Double?) {
            if (v != null) add { m -> Fact(label, Fmt.price(v, d.unit), m) }
        }
        fun pct(label: String, v: Double?) {
            if (v != null) add { m -> Fact(label, Fmt.pct(v), m, color = c.of(v)) }
        }
        fun words(label: String, v: String?) {
            if (!v.isNullOrBlank()) add { m -> Fact(label, v, m, numeric = false) }
        }
        price("שער פתיחה", d.open)
        price("שער בסיס", d.base)
        words("מחזור", d.turnover?.let { Fmt.bigShekels(it) })
        if (d.deals != null) add { m -> Fact("עסקאות", Fmt.fixed(d.deals, 0), m) }
        // Market cap arrives in thousands of shekels.
        words("שווי שוק", d.marketCap?.takeIf { it > 0 }?.let { Fmt.bigShekels(it * 1000) })
        pct("מתחילת החודש", d.monthYield)
        pct("מתחילת השנה", d.yearYield)
        pct("תשואה ברוטו לפדיון", d.grossYield)
        if (d.annualInterest != null) add { m -> Fact("ריבית שנתית", Fmt.trimmed(d.annualInterest, 3) + "%", m) }
        if (d.redemptionDate != null) add { m -> Fact("מועד פדיון", Fmt.date(d.redemptionDate), m) }
        // "לא צמוד" on a share says nothing; linkage matters for a bond.
        if (d.redemptionDate != null || d.grossYield != null) words("הצמדה", d.linkage)
        add { m -> Fact("מספר נייר", d.id, m) }
        if (d.isin != null) add { m -> Fact("ISIN", d.isin, m) }
    }
    FactGrid(cells)
}
