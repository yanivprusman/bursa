package com.automatelinux.bursa.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.data.LocalApp
import com.automatelinux.bursa.data.model.Tracked
import com.automatelinux.bursa.ui.theme.LocalAppFont
import com.automatelinux.bursa.ui.theme.NumMedium
import com.automatelinux.bursa.util.Fmt

/** Enter or change how much of a paper is held. Saving also follows it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingSheet(t: Tracked, lastPrice: Double?, onDone: () -> Unit) {
    val app = LocalApp.current
    val existing = app.portfolio.find(t.kind, t.id)
    fun editable(v: Double?, decimals: Int) = v?.let { Fmt.trimmed(it, decimals).replace(",", "") } ?: ""
    var qtyText by remember { mutableStateOf(editable(existing?.qty, 4)) }
    var costText by remember { mutableStateOf(editable(existing?.avgCost, 2)) }

    val qty = Fmt.parse(qtyText)
    val cost = Fmt.parse(costText)
    val qtyBad = qtyText.isNotBlank() && (qty == null || qty <= 0)
    val costBad = costText.isNotBlank() && (cost == null || cost <= 0)
    val canSave = qty != null && qty > 0 && !costBad

    // Typed digits read left-to-right but sit by the Hebrew label, on the right.
    val fieldStyle = NumMedium.copy(textAlign = TextAlign.End, fontFamily = LocalAppFont.current)

    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().imePadding().padding(bottom = 20.dp)) {
            Text(t.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "ההחזקה נשמרת בשרת שלך ומוצגת גם במחשב.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = qtyText,
                onValueChange = { qtyText = it },
                label = { Text("כמות") },
                supportingText = { Text(if (qtyBad) "מספר גדול מאפס" else "יחידות; באג\"ח — ערך נקוב בשקלים") },
                isError = qtyBad,
                singleLine = true,
                textStyle = fieldStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("holding-qty"),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = costText,
                onValueChange = { costText = it },
                label = { Text("מחיר קנייה ממוצע, באגורות") },
                supportingText = {
                    Text(
                        when {
                            costBad -> "מספר גדול מאפס, או להשאיר ריק"
                            lastPrice != null -> "לא חובה. המחיר עכשיו: ⁦${Fmt.trimmed(lastPrice)}⁩ אג'"
                            else -> "לא חובה — בלעדיו יוצג שווי בלי רווח והפסד"
                        },
                    )
                },
                isError = costBad,
                singleLine = true,
                textStyle = fieldStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("holding-cost"),
            )
            if (qty != null && qty > 0 && lastPrice != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "שווי לפי המחיר עכשיו: ⁦${Fmt.shekels(qty * lastPrice / 100)}⁩",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = canSave,
                    onClick = {
                        app.portfolio.setHolding(t, qty ?: return@Button, cost)
                        app.refreshQuotes()
                        onDone()
                    },
                    modifier = Modifier.testTag("holding-save"),
                ) { Text("שמירה") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDone, modifier = Modifier.testTag("holding-cancel")) { Text("ביטול") }
                Spacer(Modifier.weight(1f))
                if (existing?.held == true) {
                    TextButton(
                        onClick = {
                            app.portfolio.clearHolding(t.kind, t.id)
                            onDone()
                        },
                        modifier = Modifier.testTag("holding-clear"),
                    ) { Text("מחיקת ההחזקה", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

/** Removing a held paper throws away numbers the user typed, so it asks first. */
@Composable
fun ConfirmRemoveHolding(t: Tracked, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("להסיר את ${t.name}?") },
        text = { Text("הכמות ומחיר הקנייה שהזנת יימחקו מהטלפון.") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-remove")) {
                Text("הסרה", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel-remove")) { Text("ביטול") } },
    )
}
