package com.automatelinux.bursa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.ui.theme.Bursa
import com.automatelinux.bursa.ui.theme.NumBody
import com.automatelinux.bursa.ui.theme.NumSmall
import com.automatelinux.bursa.ui.theme.of
import com.automatelinux.bursa.ui.theme.softOf
import com.automatelinux.bursa.util.Fmt
import kotlin.math.abs

/** A string that is only a number. The Num* styles force left-to-right and tabular digits. */
@Composable
fun Num(text: String, modifier: Modifier = Modifier, style: TextStyle = NumBody, color: Color = Color.Unspecified) {
    Text(text, modifier, color = color, style = style, maxLines = 1, softWrap = false)
}

/** "▲ 0.34%" on a tinted pill. The arrow carries the direction for anyone who can't tell the colours apart. */
@Composable
fun ChangeChip(pct: Double?, modifier: Modifier = Modifier, style: TextStyle = NumSmall, minWidth: Dp = 74.dp) {
    val c = Bursa.colors
    val text = when {
        pct == null -> "—"
        pct > 0 -> "▲ " + Fmt.fixed(abs(pct), 2) + "%"
        pct < 0 -> "▼ " + Fmt.fixed(abs(pct), 2) + "%"
        else -> "0.00%"
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(c.softOf(pct))
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .widthIn(min = minWidth - 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Num(text, style = style, color = c.of(pct))
    }
}

/** "המסחר פתוח · 14:27" / "המסחר סגור · 1.10.2026". */
@Composable
fun MarketStatus(open: Boolean, tradeDate: String?, tradeTime: String?, modifier: Modifier = Modifier) {
    val c = Bursa.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (open) c.up else c.flat))
        Spacer(Modifier.width(7.dp))
        Text(
            if (open) "המסחר פתוח" else "המסחר סגור",
            style = MaterialTheme.typography.labelLarge,
            color = if (open) c.up else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val asOf = if (open) tradeTime else Fmt.date(tradeDate).takeIf { it.isNotEmpty() }
        if (asOf != null) {
            Text(" · ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Num(asOf, style = NumSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, note: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** The numbers on screen are from last time: say so, and offer to try again. */
@Composable
fun StaleNote(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CloudOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(
            "$message — מוצגים הנתונים האחרונים שנשמרו",
            Modifier.weight(1f).padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) { Text("נסו שוב") }
    }
}

/** Nothing to show and the load failed. */
@Composable
fun FailedBlock(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.CloudOff, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text("נסו שוב") }
    }
}

@Composable
fun LoadingBlock(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Refreshable(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) { content() }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
}

/** A raised card, the app's one container. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(Bursa.colors.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
    ) { content() }
}

/** Name over a small grey line — the leading half of every list row. */
@Composable
fun NameBlock(name: String, sub: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!sub.isNullOrBlank()) {
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One label over one value, for the facts grid on a detail page. */
@Composable
fun Fact(label: String, value: String, modifier: Modifier = Modifier, numeric: Boolean = true, color: Color = Color.Unspecified) {
    Column(modifier.padding(vertical = 9.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        if (numeric) Num(value, style = NumBody, color = color)
        else Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Facts two to a row. Each cell is handed the modifier that gives it half the width. */
@Composable
fun FactGrid(cells: List<@Composable (Modifier) -> Unit>, modifier: Modifier = Modifier) {
    Column(modifier) {
        cells.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { cell -> cell(Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
