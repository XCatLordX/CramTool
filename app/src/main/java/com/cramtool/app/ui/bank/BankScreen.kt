package com.cramtool.app.ui.bank

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.cramtool.app.data.QuestionEntity
import com.cramtool.app.ui.cramApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Source(val label: String, val match: (QuestionEntity) -> Boolean) {
    ALL("全部", { true }),
    UNANSWERED("未作答", { it.attempts == 0 }),
    WRONG_BOOK("錯題本", { it.inWrongBook }),
    EVER_WRONG("曾答錯", { it.wrongCount > 0 }),
    STARRED("已收藏", { it.starred }),
}

private val COUNT_OPTIONS = listOf(10, 20, 50, 0) // 0 = 全部

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BankScreen(bankId: Long, onBack: () -> Unit, onStart: (QuizRequest) -> Unit, onBrowse: () -> Unit) {
    val repo = cramApp().repository
    val scope = rememberCoroutineScope()
    val bank by repo.observeBank(bankId).collectAsState(initial = null)
    val chapters by repo.observeChapters(bankId).collectAsState(initial = emptyList())
    val questions by repo.observeQuestions(bankId).collectAsState(initial = emptyList())

    // 勾選的章節；null = 尚未初始化（預設全選）
    var selected by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(chapters) {
        if (selected == null && chapters.isNotEmpty()) selected = chapters.map { it.chapter }
    }
    val sel = selected.orEmpty().toSet()
    var source by rememberSaveable { mutableStateOf(Source.ALL) }
    var count by rememberSaveable { mutableStateOf(20) }
    var shuffle by rememberSaveable { mutableStateOf(true) }

    val pool = questions.filter { it.chapter in sel && source.match(it) }
    val take = if (count == 0) pool.size else minOf(count, pool.size)

    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bank?.name ?: "", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = onBrowse) { Icon(Icons.AutoMirrored.Filled.List, "瀏覽題目") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("重新命名") }, onClick = { menu = false; dialog = "rename" })
                        DropdownMenuItem(text = { Text("重置作答紀錄") }, onClick = { menu = false; dialog = "reset" })
                        DropdownMenuItem(text = { Text("刪除題庫") }, onClick = { menu = false; dialog = "delete" })
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    enabled = take > 0,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    onClick = {
                        val picked = (if (shuffle) pool.shuffled() else pool).take(take).map { it.id }
                        onStart(QuizRequest(bank?.name ?: "刷題", picked))
                    },
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text(if (take > 0) "  開始刷題（$take 題）" else "  沒有符合條件的題目")
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                Section("題目來源")
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Source.entries.forEach { s ->
                        val n = questions.count { it.chapter in sel && s.match(it) }
                        FilterChip(source == s, onClick = { source = s }, label = { Text("${s.label} $n") })
                    }
                }
                Section("題數")
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    COUNT_OPTIONS.forEach { c ->
                        FilterChip(count == c, onClick = { count = c }, label = { Text(if (c == 0) "全部" else "$c") })
                    }
                }
                Section("順序")
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(shuffle, onClick = { shuffle = true }, label = { Text("隨機") })
                    FilterChip(!shuffle, onClick = { shuffle = false }, label = { Text("依題號") })
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("章節範圍", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selected = chapters.map { it.chapter } }) { Text("全選") }
                    TextButton(onClick = { selected = emptyList() }) { Text("全不選") }
                }
            }
            items(chapters, key = { it.chapter }) { c ->
                val checked = c.chapter in sel
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { selected = if (checked) selected.orEmpty() - c.chapter else selected.orEmpty() + c.chapter }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.chapter, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${c.total} 題 · 已作答 ${c.answered} · 錯題 ${c.wrong}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(start = 56.dp))
            }
        }
    }

    when (dialog) {
        "rename" -> {
            var name by remember { mutableStateOf(bank?.name.orEmpty()) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("重新命名") },
                text = { OutlinedTextField(name, { name = it }, singleLine = true) },
                confirmButton = {
                    TextButton(enabled = name.isNotBlank(), onClick = {
                        scope.launch { repo.renameBank(bankId, name.trim()) }; dialog = null
                    }) { Text("確定") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } },
            )
        }
        "reset" -> ConfirmDialog("重置作答紀錄？", "此題庫所有題目的作答次數與錯題本紀錄會被清除（收藏會保留）。", { dialog = null }) {
            scope.launch { repo.resetBankStats(bankId) }
        }
        "delete" -> ConfirmDialog("刪除題庫？", "「${bank?.name}」及其所有題目與紀錄將永久刪除。", { dialog = null }) {
            scope.launch { repo.deleteBank(bankId); withContext(Dispatchers.Main) { onBack() } }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("確定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
