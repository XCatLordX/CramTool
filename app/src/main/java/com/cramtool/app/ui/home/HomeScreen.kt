package com.cramtool.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cramtool.app.data.BankSummary
import com.cramtool.app.ui.EmptyState
import com.cramtool.app.ui.cramApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenBank: (Long) -> Unit,
    onImport: () -> Unit,
    onWrongBook: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val repo = cramApp().repository
    val banks by repo.observeBankSummaries().collectAsState(initial = null)
    val wrongCount by repo.observeWrongCount().collectAsState(initial = 0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的題庫") },
                actions = {
                    IconButton(onClick = onWrongBook) {
                        BadgedBox(badge = { if (wrongCount > 0) Badge { Text("$wrongCount") } }) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = "錯題本")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImport,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("匯入題庫") },
            )
        },
        bottomBar = bottomBar,
    ) { pad ->
        val list = banks
        when {
            list == null -> {}
            list.isEmpty() -> EmptyState(
                "還沒有題庫",
                "點右下角「匯入題庫」，把 LLM 產生的 JSON 題目貼上或選擇檔案即可開始刷題。",
                Modifier.padding(pad),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    if (wrongCount > 0) {
                        Card(Modifier.fillMaxWidth().clickable(onClick = onWrongBook)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    "錯題本有 $wrongCount 題待複習",
                                    Modifier.weight(1f).padding(start = 12.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Icon(Icons.Default.ChevronRight, null)
                            }
                        }
                    }
                }
                items(list, key = { it.bank.id }) { BankCard(it) { onOpenBank(it.bank.id) } }
            }
        }
    }
}

@Composable
private fun BankCard(s: BankSummary, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(s.bank.name, style = MaterialTheme.typography.titleMedium)
            if (s.bank.description.isNotBlank()) {
                Text(
                    s.bank.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (s.total == 0) 0f else s.answered.toFloat() / s.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "共 ${s.total} 題 · 已作答 ${s.answered} · 錯題 ${s.wrong}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
