package com.automatelinux.bursa.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.data.LIVE_REFRESH_MS
import com.automatelinux.bursa.data.LocalApp
import com.automatelinux.bursa.nav.Screen
import com.automatelinux.bursa.nav.Tab
import com.automatelinux.bursa.ui.components.MarketStatus
import com.automatelinux.bursa.ui.components.TickerTape
import com.automatelinux.bursa.ui.theme.Bursa
import kotlinx.coroutines.delay

@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val nav = app.nav

    // Both tabs read from the same two sources, so they are kept fresh here rather than
    // in each tab: on open, on return to the foreground, and on a timer while trading.
    LaunchedEffect(app.active) {
        if (!app.active) return@LaunchedEffect
        app.refreshHome()
        while (true) {
            delay(LIVE_REFRESH_MS)
            if (app.marketOpen) app.refreshHome()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { HomeHeader() },
        bottomBar = { BottomBar(nav.tab) { nav.tab = it } },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (nav.tab) {
                Tab.Mine -> MineTab()
                Tab.Market -> MarketTab()
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    val app = LocalApp.current
    val market = app.overview.data
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("בורסה", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (market != null) MarketStatus(market.open, market.tradeDate, market.tradeTime)
        }
        Spacer(Modifier.height(12.dp))
        // Search is how anything gets onto the list, so it is a full-width field, not an icon.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { app.nav.push(Screen.Search) }
                .testTag("open-search")
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(
                "חיפוש מניה, קרן סל, אג\"ח או מדד",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (market != null) TickerTape(market.tape, onClick = { app.nav.push(Screen.Indices) })
    }
}

/** Two destinations, a hairline above them, and a short bar over the one you are on. */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val accent = Bursa.colors.accent
    Column(Modifier.fillMaxWidth().background(Bursa.colors.card)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Tab.entries.forEach { t ->
                val selected = t == current
                val tone by animateColorAsState(if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, tween(180))
                val icon = when (t) {
                    Tab.Mine -> if (selected) Icons.Filled.Star else Icons.Outlined.StarBorder
                    // Not an auto-mirrored icon: flipped for Hebrew, a rising chart would read as a falling one.
                    Tab.Market -> Icons.Filled.CandlestickChart
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(t) }
                        .testTag(if (t == Tab.Mine) "tab-mine" else "tab-market")
                        .then(if (selected) Modifier.semantics { contentDescription = "active-tab:${t.label}" } else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(if (selected) accent else androidx.compose.ui.graphics.Color.Transparent),
                    )
                    Spacer(Modifier.height(9.dp))
                    Icon(icon, null, Modifier.size(24.dp), tint = tone)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        t.label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                        color = tone,
                    )
                    Spacer(Modifier.height(9.dp))
                }
            }
        }
    }
}
