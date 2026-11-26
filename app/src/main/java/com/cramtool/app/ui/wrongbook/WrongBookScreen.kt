package com.cramtool.app.ui.wrongbook

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cramtool.app.QuizRequest
import com.cramtool.app.data.WrongItem
import com.cramtool.app.ui.EmptyState
import com.cramtool.app.ui.cramApp
import com.cramtool.app.ui.quiz.AnswerReveal
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrongBookScreen(onBack: () -> Unit, onStart: (QuizRequest) -> Unit) {
    val repo = cramApp().repository
    val scope = rememberCoroutineScope()
    val items by repo.observeWrongItems().collectAsState(initial = null)
    val threshold by repo.masteryThreshold.collectAsState()
    var bankFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var chapterFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(false) }

    val all = items.orEmpty()
    val banks = all.map { it.question.bankId to it.bankName }.distinct()
    val inBank = all.filter { bankFilter == null || it.question.bankId == bankFilter }
    val chapters = if (bankFilter == null) emptyList() else inBank.map { it.question.chapter }.distinct()
    val shown = inBank.filter { chapterFilter == null || it.question.chapter == chapterFilter }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("錯題本") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = { IconButton(onClick = { settings = true }) { Icon(Icons.Default.Tune, "設定") } },
            )
        },
        bottomBar = {
            if (shown.isNotEmpty()) Surface(tonalElevation = 3.dp) {
                Button(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    onClick = { onStart(QuizRequest("錯題複習", shown.map { it.question.id }.shuffled(), isReview = true)) },
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text("  複習這 ${shown.size} 題")
                }
            }
        },
    ) { pad ->
        if (items == null) return@Scaffold
        if (all.isEmpty()) {
            EmptyState("錯題本是空的 🎉", "刷題時答錯的題目會自動收進這裡；在複習中連續答對 $threshold 次就會自動移出。", Modifier.padding(pad))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(bankFilter == null, { bankFilter = null; chapterFilter = null }, label = { Text("全部 ${all.size}") })
                    banks.forEach { (id, name) ->
                        FilterChip(bankFilter == id, { bankFilter = id; chapterFilter = null },
                            label = { Text("$name ${all.count { it.question.bankId == id }}") })
                    }
                }
                if (chapters.size > 1) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(chapterFilter == null, { chapterFilter = null }, label = { Text("所有章節") })
                        chapters.forEach { c ->
                            FilterChip(chapterFilter == c, { chapterFilter = c }, label = { Text(c) })
                        }
                    }
                }
                Text(
                    "連續答對 $threshold 次會自動移出錯題本，也可以按 ✓ 手動移出。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(shown, key = { it.question.id }) { w ->
                WrongCard(w, threshold) { scope.launch { repo.removeFromWrongBook(w.question.id) } }
            }
        }
    }

    if (settings) {
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text("熟練門檻") },
            text = {
                Column {
                    Text("錯題在之後的練習中連續答對幾次後，自動移出錯題本：")
                    listOf(1, 2, 3, 5).forEach { n ->
                        Row(
                            Modifier.fillMaxWidth().clickable { repo.setMasteryThreshold(n) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(threshold == n, onClick = { repo.setMasteryThreshold(n) })
                            Text("$n 次")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { settings = false }) { Text("完成") } },
        )
    }
}

@Composable
private fun WrongCard(w: WrongItem, threshold: Int, onRemove: () -> Unit) {
    val q = w.question
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().animateContentSize().clickable { open = !open }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${w.bankName} · ${q.chapter} · 錯 ${q.wrongCount} 次 · 進度 ${q.reviewStreak}/$threshold",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) { Icon(Icons.Default.CheckCircleOutline, "移出錯題本") }
            }
            Text(q.stem, style = MaterialTheme.typography.bodyLarge, maxLines = if (open) Int.MAX_VALUE else 3)
            if (open) AnswerReveal(q, Modifier.padding(top = 8.dp))
            else Text("點擊查看答案", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
