package com.automatelinux.bursa.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.data.LocalApp
import com.automatelinux.bursa.data.model.INDEX
import com.automatelinux.bursa.data.model.TapeItem
import com.automatelinux.bursa.ui.theme.Bursa
import com.automatelinux.bursa.ui.theme.LocalAppFont
import com.automatelinux.bursa.ui.theme.NumBody
import com.automatelinux.bursa.ui.theme.NumSmall
import com.automatelinux.bursa.ui.theme.of
import com.automatelinux.bursa.ui.theme.softOf
import com.automatelinux.bursa.util.Fmt
import kotlin.math.abs

/**
 * A string that is only a number. The Num* styles force left-to-right and even digit widths.
 *
 * Pass the underlying value as [flashOn] for a live price: when it changes between two
 * refreshes the number flashes green or red for a moment, the way a quote board does.
 */
@Composable
fun Num(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = NumBody,
    color: Color = Color.Unspecified,
    flashOn: Double? = null,
) {
    val c = Bursa.colors
    val alpha = remember { Animatable(0f) }
    var previous by remember { mutableStateOf(flashOn) }
    var rose by remember { mutableStateOf(true) }
    LaunchedEffect(flashOn) {
        val before = previous
        previous = flashOn
        if (flashOn != null && before != null && flashOn != before) {
            rose = flashOn > before
            alpha.snapTo(0.42f)
            alpha.animateTo(0f, tween(1100))
        }
    }
    Text(
        text,
        modifier.drawBehind {
            if (alpha.value > 0f) {
                val pad = 4.dp.toPx()
                drawRoundRect(
                    (if (rose) c.up else c.down).copy(alpha = alpha.value),
                    topLeft = Offset(-pad, 0f),
                    size = Size(size.width + pad * 2, size.height),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
        },
        color = color,
        style = style.copy(fontFamily = LocalAppFont.current),
        maxLines = 1,
        softWrap = false,
    )
}

/** "▲ 0.34%" on a tinted pill. The arrow carries the direction for anyone who can't tell the colours apart. */
@Composable
fun ChangeChip(pct: Double?, modifier: Modifier = Modifier, style: TextStyle = NumSmall, minWidth: Dp = 76.dp) {
    val c = Bursa.colors
    val text = when {
        pct == null -> "—"
        pct > 0 -> "▲ " + Fmt.fixed(abs(pct), 2) + "%"
        pct < 0 -> "▼ " + Fmt.fixed(abs(pct), 2) + "%"
        else -> "0.00%"
    }
    Box(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(c.softOf(pct))
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .widthIn(min = minWidth - 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Num(text, style = style.copy(fontWeight = FontWeight.SemiBold), color = c.of(pct))
    }
}

/** "המסחר פתוח · 14:27" / "המסחר סגור · 1.10.2026", as a pill. The dot breathes while trading. */
@Composable
fun MarketStatus(open: Boolean, tradeDate: String?, tradeTime: String?, modifier: Modifier = Modifier) {
    val c = Bursa.colors
    val tone = if (open) c.up else MaterialTheme.colorScheme.onSurfaceVariant
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
    )
    Row(
        modifier
            .clip(CircleShape)
            .background(if (open) c.upSoft else MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(tone.copy(alpha = if (open) pulse else 1f)))
        Spacer(Modifier.width(7.dp))
        Text(if (open) "המסחר פתוח" else "המסחר סגור", style = MaterialTheme.typography.labelMedium, color = tone)
        val asOf = if (open) tradeTime else Fmt.date(tradeDate).takeIf { it.isNotEmpty() }
        if (asOf != null) {
            Spacer(Modifier.width(7.dp))
            Num(asOf, style = NumSmall, color = tone)
        }
    }
}

/**
 * The ticker strip: the indices gliding past, as on the wall of an exchange. It reads left to
 * right like any ticker. Tapping it opens the full list.
 */
@Composable
fun TickerTape(items: List<TapeItem>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val c = Bursa.colors
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onClick)
                .testTag("ticker-tape")
                .padding(vertical = 9.dp),
        ) {
            Row(
                Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = 34.dp, initialDelayMillis = 600, repeatDelayMillis = 0),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    // Names are Hebrew; keep each one whole inside the left-to-right strip.
                    Text(
                        "⁧${item.name}⁩",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                    Num(Fmt.fixed(item.last, 2), style = NumSmall.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.width(7.dp))
                    val pct = item.changePct
                    Num(
                        when {
                            pct == null -> ""
                            pct > 0 -> "▲" + Fmt.fixed(pct, 2) + "%"
                            pct < 0 -> "▼" + Fmt.fixed(abs(pct), 2) + "%"
                            else -> "0.00%"
                        },
                        style = NumSmall,
                        color = c.of(pct),
                    )
                    Spacer(Modifier.width(26.dp))
                }
            }
        }
    }
}

/**
 * A row of mutually exclusive choices: one track, the chosen one raised. Each option takes
 * an equal share of the width, so nothing scrolls and nothing is hidden.
 */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, tag: String = "seg") {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val fill by animateColorAsState(if (on) Bursa.colors.card else Color.Transparent, tween(160))
            val ink by animateColorAsState(
                if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                tween(160),
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(fill)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                    ) { onSelect(i) }
                    .testTag("$tag-$i")
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                    color = ink,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The paper's face in a list: its issuer's logo on a white tile, or — for an index, and for a
 * company the exchange has no logo for — a candlestick mark in the brand colour.
 */
@Composable
fun LogoTile(kind: String, companyId: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val app = LocalApp.current
    val logo = if (kind != INDEX && companyId != null) app.logo(companyId) else null
    val shape = RoundedCornerShape(size * 0.28f)
    if (logo != null) {
        Image(
            logo,
            contentDescription = null,
            modifier = modifier.size(size).clip(shape).background(Color.White).padding(size * 0.08f),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CandlestickChart, null, Modifier.size(size * 0.5f), tint = Bursa.colors.accent)
        }
    }
}

/** A grey block standing in for content that is still loading; it breathes so it reads as alive. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(10.dp)) {
    val alpha by rememberInfiniteTransition().animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Reverse),
    )
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)))
}

/** The shape of a list that is on its way: a tile, two lines, a chip. */
@Composable
fun SkeletonRows(count: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        repeat(count) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Skeleton(Modifier.size(40.dp), RoundedCornerShape(11.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Skeleton(Modifier.width(120.dp).height(13.dp))
                    Spacer(Modifier.height(7.dp))
                    Skeleton(Modifier.width(70.dp).height(10.dp))
                }
                Skeleton(Modifier.width(76.dp).height(28.dp))
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, note: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (action != null) {
            TextButton(onClick = onAction, modifier = Modifier.testTag("section-action")) {
                Text(action, color = Bursa.colors.accent)
            }
        }
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
        TextButton(onClick = onRetry, modifier = Modifier.testTag("retry-stale")) { Text("נסו שוב", color = Bursa.colors.accent) }
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
        TextButton(onClick = onRetry, modifier = Modifier.testTag("retry-failed")) { Text("נסו שוב", color = Bursa.colors.accent) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Refreshable(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) { content() }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
    }
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

/** The dark, lit-from-above band behind the two headline numbers of the app. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val c = Bursa.colors
    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(Brush.verticalGradient(listOf(c.hero, c.heroDeep)))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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

/** One list row's frame: the logo, then whatever the row says. */
@Composable
fun PaperRow(
    kind: String,
    companyId: String?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        LogoTile(kind, companyId)
        Spacer(Modifier.width(12.dp))
        content()
    }
}

/** One label over one value, for the facts grid on a detail page. */
@Composable
fun Fact(label: String, value: String, modifier: Modifier = Modifier, numeric: Boolean = true, color: Color = Color.Unspecified) {
    Column(modifier.padding(vertical = 9.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        if (numeric) Num(value, style = NumBody.copy(fontWeight = FontWeight.SemiBold), color = color)
        else Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/**
 * Where today's price sits between the day's low and high: a track, a filled part up to the
 * price, and a knob on it. Drawn left to right — low on the left — like a number line.
 */
@Composable
fun DayRange(low: Double, high: Double, last: Double, unit: String, tone: Color, modifier: Modifier = Modifier) {
    val span = high - low
    val at = if (span > 0) ((last - low) / span).toFloat().coerceIn(0f, 1f) else 0.5f
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val knobRing = Bursa.colors.card
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text("טווח יומי", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().height(14.dp).drawBehind {
                val y = size.height / 2
                val h = 5.dp.toPx()
                drawRoundRect(track, Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
                drawRoundRect(tone.copy(alpha = 0.55f), Offset(0f, y - h / 2), Size(size.width * at, h), CornerRadius(h / 2))
                drawCircle(knobRing, 7.dp.toPx(), Offset(size.width * at, y))
                drawCircle(tone, 5.dp.toPx(), Offset(size.width * at, y))
            },
        )
        Spacer(Modifier.height(6.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth()) {
                Num(Fmt.price(low, unit), style = NumSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Num(Fmt.price(high, unit), style = NumSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The list could not be brought in line with the server, or a change did not stick. It sits
 * over whatever screen is showing — a change can be made from several of them — until it is
 * retried or dismissed.
 */
@Composable
fun SyncNotice(message: String, onRetry: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            .testTag("sync-notice"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            Modifier.weight(1f).padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
        TextButton(onClick = onRetry, modifier = Modifier.testTag("sync-retry")) {
            Text("נסו שוב", color = MaterialTheme.colorScheme.inversePrimary)
        }
        TextButton(onClick = onDismiss, modifier = Modifier.testTag("sync-dismiss")) {
            Text("סגירה", color = MaterialTheme.colorScheme.inverseOnSurface)
        }
    }
}
