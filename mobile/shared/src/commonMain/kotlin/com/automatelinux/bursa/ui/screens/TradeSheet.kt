package com.automatelinux.bursa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.data.LocalApp
import com.automatelinux.bursa.data.model.AccountSummary
import com.automatelinux.bursa.data.model.BUY
import com.automatelinux.bursa.data.model.Order
import com.automatelinux.bursa.data.model.Paper
import com.automatelinux.bursa.data.model.SELL
import com.automatelinux.bursa.data.model.TradeRules
import com.automatelinux.bursa.ui.components.LogoTile
import com.automatelinux.bursa.ui.components.Num
import com.automatelinux.bursa.ui.components.Segmented
import com.automatelinux.bursa.ui.theme.Bursa
import com.automatelinux.bursa.ui.theme.LocalAppFont
import com.automatelinux.bursa.ui.theme.NumBody
import com.automatelinux.bursa.ui.theme.NumLarge
import com.automatelinux.bursa.ui.theme.NumMedium
import com.automatelinux.bursa.util.Fmt
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** The sell side's colour: blue, so it never reads as a price that fell. */
val SellBlue = Color(0xFF6FA8FF)

/** The broker's commission on a deal of [gross] agorot, by the rules the server sent. */
fun feeOf(rules: TradeRules, gross: Double): Double = maxOf(rules.feeMin, ceil(gross * rules.feeRate))

/** The most units [cash] agorot buys at [price], fee included. */
fun maxBuy(rules: TradeRules, cash: Double, price: Double): Long {
    if (price <= 0) return 0
    var q = floor(cash / (price * (1 + rules.feeRate))).toLong()
    while (q > 0) {
        val gross = (q * price).roundToLong().toDouble()
        if (gross + feeOf(rules, gross) <= cash) break
        q--
    }
    return maxOf(0, q)
}

/** A small gold "practice" tag — said wherever money is. */
@Composable
fun PracticeTag(text: String = "תרגול") {
    val c = Bursa.colors
    Text(
        text,
        Modifier.clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = c.accent,
    )
}

/**
 * A market order: buy or sell, how many. The quantity is all the owner types — the price is
 * the exchange's, read by the server when the order arrives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeSheet(paper: Paper, initialSide: String, last: Double, liveNow: Boolean, onDone: () -> Unit) {
    val app = LocalApp.current
    val a = app.account.summary ?: return
    val scope = rememberCoroutineScope()
    val held = a.position(paper.id)?.qty?.toLong() ?: 0L
    val selling = a.pending.filter { it.side == SELL && it.paper.id == paper.id }.sumOf { it.qty.toLong() }
    val canSell = held - selling
    var sideIdx by remember { mutableIntStateOf(if (initialSide == SELL && held > 0) 1 else 0) }
    val side = if (sideIdx == 1) SELL else BUY
    var qtyText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<Order?>(null) }

    val max = if (side == BUY) maxBuy(a.rules, a.available, last) else canSell
    val qty = qtyText.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) && it.length < 10 }?.toLong()
    val bad = qtyText.isNotBlank() && (qty == null || qty <= 0)
    val tooMany = qty != null && qty > max
    val gross = qty?.let { (it * last).roundToLong().toDouble() } ?: 0.0
    val fee = if (qty != null) feeOf(a.rules, gross) else 0.0
    val total = if (side == BUY) gross + fee else gross - fee
    val canSend = qty != null && qty > 0 && !tooMany && !busy
    val verb = if (side == BUY) "קנייה" else "מכירה"

    val fieldStyle = NumMedium.copy(textAlign = TextAlign.End, fontFamily = LocalAppFont.current)

    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().imePadding().padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoTile("security", paper.companyId, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(paper.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    PracticeTag("חשבון תרגול · כסף מדומה, מחירים אמיתיים")
                }
            }
            Spacer(Modifier.height(16.dp))

            val result = done
            if (result != null) {
                Outcome(result, a)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDone, modifier = Modifier.testTag("trade-done")) { Text("סגירה") }
                return@Column
            }

            // Selling is offered only for something held; there is no short selling.
            if (held > 0) {
                Segmented(listOf("קנייה", "מכירה"), sideIdx, { sideIdx = it; error = null }, Modifier.fillMaxWidth(), tag = "trade-side")
                Spacer(Modifier.height(14.dp))
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (liveNow) "מחיר עכשיו" else "שער אחרון", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Num(Fmt.trimmed(last), style = NumLarge)
                Spacer(Modifier.width(6.dp))
                Text("אג'", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("כמות") },
                    isError = bad || tooMany,
                    singleLine = true,
                    textStyle = fieldStyle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).testTag("trade-qty"),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { qtyText = max.toString() }, enabled = max > 0, modifier = Modifier.testTag("trade-max")) {
                    Text(if (side == BUY) "כמה שאפשר" else "הכול")
                }
            }
            Text(
                when {
                    bad -> "מספר שלם גדול מאפס"
                    side == BUY -> "אפשר לקנות עד ⁦${Fmt.fixed(max.toDouble(), 0)}⁩ במזומן הפנוי"
                    else -> "בתיק ⁦${Fmt.fixed(canSell.toDouble(), 0)}⁩ יחידות למכירה"
                },
                Modifier.padding(top = 4.dp, start = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (bad || tooMany) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                SumLine("שווי העסקה", Fmt.shekels(gross / 100))
                SumLine("עמלה (⁦${Fmt.trimmed(a.rules.feeRate * 100)}%⁩, לפחות ⁦${Fmt.shekels(a.rules.feeMin / 100)}⁩)", Fmt.shekels(fee / 100))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SumLine(if (side == BUY) "סה״כ לתשלום" else "סה״כ תקבלו", Fmt.shekels(total / 100), strong = true)
                SumLine("מזומן פנוי אחרי", Fmt.shekels((a.available + if (side == BUY) -total else total) / 100))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (liveNow) "הנייר נסחר עכשיו — הפקודה תבוצע מיד, בשער שבבורסה ברגע השליחה."
                else "הנייר לא נסחר כרגע — הפקודה תמתין ותבוצע בשער הפתיחה של יום המסחר הבא. הסכום הוא הערכה לפי השער האחרון.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = canSend,
                    colors = if (side == SELL) ButtonDefaults.buttonColors(containerColor = SellBlue, contentColor = Color(0xFF04142E)) else ButtonDefaults.buttonColors(),
                    onClick = {
                        val n = qty ?: return@Button
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                done = app.account.order(side, paper.id, n)
                                app.refreshQuotes()
                            } catch (e: Exception) {
                                error = e.message ?: "משהו השתבש"
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.testTag("trade-send"),
                ) { Text(if (busy) "שולח…" else if (qty != null && qty > 0) "$verb של ⁦${Fmt.fixed(qty.toDouble(), 0)}⁩" else verb) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDone, modifier = Modifier.testTag("trade-cancel")) { Text("ביטול") }
            }
        }
    }
}

@Composable
private fun SumLine(label: String, value: String, strong: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.weight(1f),
            style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Num(value, style = if (strong) NumMedium else NumBody)
    }
}

/** What became of the order, in the words of a broker's confirmation. */
@Composable
private fun Outcome(order: Order, a: AccountSummary) {
    val c = Bursa.colors
    val trade = a.trades.firstOrNull { it.orderId == order.id }
    val (bg, title, titleColor) = when {
        order.status == "filled" && trade != null -> Triple(c.upSoft, "בוצע", c.up)
        order.status == "pending" -> Triple(c.flatSoft, "הפקודה נקלטה וממתינה", MaterialTheme.colorScheme.onSurface)
        else -> Triple(c.downSoft, "הפקודה נדחתה", c.down)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(16.dp).testTag("trade-outcome")) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = titleColor)
        Spacer(Modifier.height(6.dp))
        val body = when {
            trade != null && order.status == "filled" -> {
                val verb = if (order.side == BUY) "נקנו" else "נמכרו"
                val paid = if (order.side == BUY) trade.gross + trade.fee else trade.gross - trade.fee
                "$verb ⁦${Fmt.fixed(trade.qty, 0)}⁩ יחידות בשער ⁦${Fmt.trimmed(trade.price)}⁩ אג'" +
                    (if (trade.how == "open") " (שער הפתיחה)" else "") +
                    ". ${if (order.side == BUY) "שולם" else "התקבל"} ⁦${Fmt.shekels(paid / 100)}⁩, כולל עמלה של ⁦${Fmt.shekels(trade.fee / 100)}⁩."
            }
            order.status == "pending" ->
                "היא תבוצע בשער הפתיחה של יום המסחר הראשון מ-⁦${Fmt.date(order.fillFrom)}⁩. אפשר לבטל אותה עד אז בלשונית \"שלי\"."
            else -> order.reason ?: ""
        }
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
