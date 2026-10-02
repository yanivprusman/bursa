package com.automatelinux.bursa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.automatelinux.bursa.data.LocalApp
import com.automatelinux.bursa.data.model.IndicesResponse
import com.automatelinux.bursa.data.rememberResource
import com.automatelinux.bursa.nav.Screen
import com.automatelinux.bursa.ui.components.BackButton
import com.automatelinux.bursa.ui.components.ChangeChip
import com.automatelinux.bursa.ui.components.FailedBlock
import com.automatelinux.bursa.ui.components.SkeletonRows
import com.automatelinux.bursa.ui.components.NameBlock
import com.automatelinux.bursa.ui.components.Num
import com.automatelinux.bursa.ui.components.Refreshable
import com.automatelinux.bursa.ui.components.SectionTitle
import com.automatelinux.bursa.ui.components.StaleNote
import com.automatelinux.bursa.ui.theme.NumBody
import com.automatelinux.bursa.util.Fmt

/** Every index the exchange publishes, grouped the way the exchange groups them. */
@Composable
fun IndicesScreen() {
    val app = LocalApp.current
    val res = rememberResource("/api/indices", IndicesResponse.serializer(), live = { app.marketOpen })
    val groups = res.data?.indices.orEmpty().groupBy { it.category }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton { app.nav.back() }
                Text("כל המדדים", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { pad ->
        Refreshable(res.refreshing, onRefresh = { res.refresh(byUser = true) }, modifier = Modifier.padding(pad)) {
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (res.data == null) {
                    item {
                        val error = res.error
                        if (error != null) FailedBlock(error, onRetry = { res.refresh() }) else SkeletonRows(9)
                    }
                    return@LazyColumn
                }
                res.error?.let { item { StaleNote(it, onRetry = { res.refresh() }) } }
                groups.forEach { (category, rows) ->
                    item(key = "title-$category") { SectionTitle(category) }
                    items(rows, key = { it.id }) { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { app.nav.push(Screen.Index(row.id, row.name)) }
                                .testTag("index-${row.id}")
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NameBlock(row.name, null, Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            Num(Fmt.fixed(row.last, 2), style = NumBody.copy(fontWeight = FontWeight.SemiBold), flashOn = row.last)
                            Spacer(Modifier.width(12.dp))
                            ChangeChip(row.changePct)
                        }
                    }
                }
            }
        }
    }
}
