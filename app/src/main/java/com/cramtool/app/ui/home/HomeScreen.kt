package com.cramtool.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import com.cramtool.app.ui.DragMoveState
import com.cramtool.app.ui.SelectionState
import com.cramtool.app.ui.SelectionTopBar
import com.cramtool.app.ui.latest
import com.cramtool.app.ui.listSelectionGestures
import com.cramtool.app.ui.mainToast
import com.cramtool.app.ui.notes.ConfirmDeleteDialog
import com.cramtool.app.ui.notes.TextInputDialog
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cramtool.app.data.BankSummary
import com.cramtool.app.ui.EmptyState
import com.cramtool.app.ui.horizontalSafe
import com.cramtool.app.ui.cramApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenBank: (Long) -> Unit,
    onImport: () -> Unit,
    onWrongBook: () -> Unit,
    onSettings: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    val repo = cramApp().repository
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val banks by remember(Unit) { repo.observeBankSummaries() }.collectAsState(initial = null)
    val wrongCount by remember(Unit) { repo.observeWrongCount() }.collectAsState(initial = 0)

    // 多選：長按選取、長按滑動範圍選取
    val selection = remember { SelectionState() }
    val drag = remember { DragMoveState() }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val edgePx = with(LocalDensity.current) { 64.dp.toPx() }
    val keys = banks.orEmpty().map { "b${it.bank.id}" }
    LaunchedEffect(keys) { selection.retain(keys) }
    val keysNow = latest(keys)
    val tapAction = latest<(String) -> Unit> { k ->
        if (selection.active) selection.toggle(k) else onOpenBank(k.drop(1).toLong())
    }
    val selectedIds = selection.keys.map { it.drop(1).toLong() }
    var dialog by remember { mutableStateOf<String?>(null) }
    var selMenu by remember { mutableStateOf(false) }
    BackHandler(enabled = selection.active) { selection.clear() }

    Scaffold(
        topBar = {
            if (selection.active) SelectionTopBar(selection.keys.size, onClose = { selection.clear() }) {
                if (selection.keys.size == 1) IconButton(onClick = { dialog = "rename" }) { Icon(Icons.Default.Edit, "重新命名") }
                IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Default.Delete, "刪除所選") }
                Box {
                    IconButton(onClick = { selMenu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                    DropdownMenu(selMenu, { selMenu = false }) {
                        DropdownMenuItem(text = { Text("全選") }, onClick = { selMenu = false; selection.keys = keys.toSet() })
                        DropdownMenuItem(text = { Text("重置作答紀錄") }, onClick = { selMenu = false; dialog = "reset" })
                    }
                }
            } else TopAppBar(
                navigationIcon = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "設定") } },
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
            if (!selection.active) ExtendedFloatingActionButton(
                modifier = Modifier.horizontalSafe(),
                onClick = onImport,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("匯入題庫") },
            )
        },
        bottomBar = { if (!selection.active) bottomBar() },
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
                state = listState,
                modifier = Modifier.fillMaxSize().padding(pad).listSelectionGestures(
                    list = listState, selection = selection, drag = drag,
                    selectableKeys = { keysNow.value },
                    canDropOn = { false }, onDrop = {},
                    onTap = { k -> tapAction.value(k) },
                    scope = scope, haptics = haptics, edgePx = edgePx,
                ),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "wrong") {
                    if (wrongCount > 0 && !selection.active) {
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
                items(list, key = { "b${it.bank.id}" }) { b ->
                    val k = "b${b.bank.id}"
                    BankCard(
                        b,
                        selecting = selection.active,
                        selected = k in selection.keys,
                        onLongClick = { selection.toggle(k) },
                    ) { if (selection.active) selection.toggle(k) else onOpenBank(b.bank.id) }
                }
            }
        }
    }

    val single = banks.orEmpty().firstOrNull { selectedIds.singleOrNull() == it.bank.id }
    when (dialog) {
        "rename" -> if (single != null) TextInputDialog("重新命名題庫", single.bank.name, "題庫名稱", onDismiss = { dialog = null }) { name ->
            selection.clear(); scope.launch { repo.renameBank(single.bank.id, name) }
        }
        "delete" -> ConfirmDeleteDialog(
            "刪除 ${selectedIds.size} 個題庫？",
            "所選題庫的所有題目與作答紀錄將永久刪除（建議先到「設定 → 匯出備份」）。",
            { dialog = null },
        ) {
            val ids = selectedIds; selection.clear()
            scope.launch { repo.deleteBanks(ids); mainToast(ctx, "已刪除 ${ids.size} 個題庫") }
        }
        "reset" -> ConfirmDeleteDialog(
            "重置 ${selectedIds.size} 個題庫的作答紀錄？",
            "作答次數與錯題本紀錄會清除，題目與收藏保留。",
            { dialog = null },
            confirmLabel = "重置",
        ) {
            val ids = selectedIds; selection.clear()
            scope.launch { repo.resetBanksStats(ids) }
        }
    }
}

@Composable
private fun BankCard(s: BankSummary, selecting: Boolean, selected: Boolean, onLongClick: () -> Unit, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth()
            .semantics {
                onClick(label = "開啟") { onClick(); true }
                onLongClick(label = "選取") { onLongClick(); true }
            },
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else CardDefaults.cardColors(),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    if (selected) Icon(Icons.Default.CheckCircle, "已選取", tint = MaterialTheme.colorScheme.primary)
                    else Icon(Icons.Default.RadioButtonUnchecked, "未選取", tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(12.dp))
                }
                Text(s.bank.name, style = MaterialTheme.typography.titleMedium)
            }
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
