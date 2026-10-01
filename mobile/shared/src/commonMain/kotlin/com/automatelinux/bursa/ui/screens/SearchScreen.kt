package com.automatelinux.bursa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.data.LocalApp
import com.automatelinux.bursa.data.encodeQuery
import com.automatelinux.bursa.data.model.Hit
import com.automatelinux.bursa.data.model.SearchResponse
import com.automatelinux.bursa.data.model.Tracked
import com.automatelinux.bursa.ui.components.BackButton
import com.automatelinux.bursa.ui.components.FailedBlock
import com.automatelinux.bursa.ui.components.NameBlock
import com.automatelinux.bursa.ui.components.SectionTitle
import com.automatelinux.bursa.ui.theme.Bursa
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private fun Hit.tracked() = Tracked(kind, id, name, symbol, type)

@Composable
fun SearchScreen() {
    val app = LocalApp.current
    val portfolio = app.portfolio
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Hit>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    LaunchedEffect(query, attempt) {
        val q = query.trim()
        if (q.isEmpty()) {
            hits = null
            error = null
            searching = false
            return@LaunchedEffect
        }
        searching = true
        // Wait for a pause in the typing before asking the server.
        delay(250)
        try {
            hits = app.api.get("/api/search?q=" + encodeQuery(q), SearchResponse.serializer(), remember = false).hits
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "משהו השתבש"
        }
        searching = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton { app.nav.back() }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("שם, סימול או מספר נייר") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.testTag("clear-search")) {
                            Icon(Icons.Filled.Close, "ניקוי")
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.weight(1f).focusRequester(focus).testTag("search-field"),
            )
        }
        // Always laid out, so results do not jump when it appears.
        LinearProgressIndicator(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            color = if (searching) MaterialTheme.colorScheme.primary else Color.Transparent,
            trackColor = Color.Transparent,
        )

        val found = hits
        val failed = error
        when {
            query.isBlank() -> {
                if (portfolio.recent.isEmpty()) {
                    Hint("אפשר לחפש בעברית או באנגלית: שם החברה, סימול, מספר נייר או ISIN.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        item { SectionTitle("נפתחו לאחרונה") }
                        items(portfolio.recent, key = { "r" + it.key }) { t -> ResultRow(t) }
                    }
                }
            }
            failed != null && found == null -> FailedBlock(failed, onRetry = { attempt++ })
            found == null -> {}
            found.isEmpty() -> Hint("לא נמצא נייר שמתאים ל\"${query.trim()}\".")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(found, key = { it.kind + it.id }) { h -> ResultRow(h.tracked()) }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Tap the row to open the paper; tap the star to follow it without leaving the results. */
@Composable
private fun ResultRow(t: Tracked) {
    val app = LocalApp.current
    val portfolio = app.portfolio
    val existing = portfolio.find(t.kind, t.id)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { app.nav.push(t.screen()) }
            .testTag("result-${t.key}")
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NameBlock(
            t.name,
            listOfNotNull(t.type, t.symbol?.takeIf { it != t.name }, "⁦${t.id}⁩").joinToString(" · "),
            Modifier.weight(1f),
        )
        when {
            // Un-following a held paper would delete the holding; that is done on its own page.
            existing?.held == true -> Text(
                "בתיק",
                Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Bursa.colors.accent,
            )
            existing != null -> IconButton(onClick = { portfolio.unfollow(t.kind, t.id) }, modifier = Modifier.testTag("unfollow-${t.key}")) {
                Icon(Icons.Filled.Star, "הסרה מהמעקב", tint = Bursa.colors.accent)
            }
            else -> IconButton(onClick = { portfolio.follow(t); app.refreshQuotes() }, modifier = Modifier.testTag("follow-${t.key}")) {
                Icon(Icons.Outlined.StarBorder, "הוספה למעקב")
            }
        }
    }
}
