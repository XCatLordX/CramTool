package com.cramtool.app.ui.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.UploadFile
import com.cramtool.app.SessionStore
import com.cramtool.app.data.SOURCE_DOCX
import com.cramtool.app.data.SOURCE_PDF
import com.cramtool.app.data.SOURCE_PPTX
import com.cramtool.app.docs.DocumentImporter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cramtool.app.data.FolderSummary
import com.cramtool.app.data.NoteRepository
import com.cramtool.app.data.NoteSummary
import com.cramtool.app.notes.Markdown
import com.cramtool.app.ui.DragMoveState
import com.cramtool.app.ui.EmptyState
import com.cramtool.app.ui.SelectionState
import com.cramtool.app.ui.SelectionTopBar
import com.cramtool.app.ui.cramApp
import com.cramtool.app.ui.horizontalSafe
import com.cramtool.app.ui.latest
import com.cramtool.app.ui.listSelectionGestures
import com.cramtool.app.ui.mainToast
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SortMode(val label: String) { UPDATED("最近更新"), OPENED("最近閱讀"), NAME("名稱") }

private fun folderKey(id: Long) = "f$id"
private fun noteKey(id: Long) = "n$id"

/**
 * 檔案總管式的筆記列表。folderId = null 為最上層。
 * 長按進入多選、長按滑動範圍選取、拖曳已選項目到資料夾即可移動。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    folderId: Long?,
    onBack: (() -> Unit)?,
    onOpenFolder: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewNote: (Long?) -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val repo = cramApp().notes
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val allFolders by remember(Unit) { repo.observeAllFolders() }.collectAsState(initial = emptyList())
    val subfolders by remember(folderId) { repo.observeSubfolders(folderId) }.collectAsState(initial = emptyList())
    val notes by remember(folderId) { repo.observeNotesIn(folderId) }.collectAsState(initial = null)

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val results by remember(query) {
        if (query.isBlank()) flowOf(emptyList()) else repo.searchNotes(query.trim())
    }.collectAsState(initial = emptyList())

    var sort by rememberSaveable { mutableStateOf(SortMode.UPDATED) }
    var sortMenu by remember { mutableStateOf(false) }
    var selMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Pair<String, Any?>?>(null) }

    var addMenu by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<String?>(null) }
    val app = cramApp()
    fun importDoc(uri: android.net.Uri) {
        importing = DocumentImporter.displayName(ctx, uri) ?: "文件"
        scope.launch {
            val r = runCatching { DocumentImporter.import(ctx, uri, folderId, repo) }
            importing = null
            r.onSuccess { d ->
                mainToast(ctx, "已匯入「${d.title}」" + when (d.type) {
                    SOURCE_PDF -> "（${d.pages} 頁）"
                    SOURCE_PPTX -> "（${d.pages} 張投影片）"
                    else -> ""
                })
                onOpenNote(d.noteId)
            }.onFailure { mainToast(ctx, it.message ?: "匯入失敗", long = true) }
        }
    }
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importDoc) }
    // 從其他 App 分享／開啟方式帶進來的文件（只在最上層筆記頁處理）
    val pendingDoc by SessionStore.pendingDocument.collectAsState()
    LaunchedEffect(pendingDoc) {
        if (folderId == null) pendingDoc?.let { SessionStore.pendingDocument.value = null; importDoc(it) }
    }

    val selection = remember { SelectionState() }
    val drag = remember { DragMoveState() }
    val list = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val edgePx = with(LocalDensity.current) { 64.dp.toPx() }

    val path = folderPath(folderId, allFolders)
    val title = path.lastOrNull()?.name ?: "筆記"
    val sortedNotes = notes.orEmpty().let { ns ->
        when (sort) {
            SortMode.UPDATED -> ns.sortedByDescending { it.updatedAt }
            SortMode.OPENED -> ns.sortedByDescending { it.lastOpenedAt ?: 0 }
            SortMode.NAME -> ns.sortedBy { it.title.lowercase() }
        }
    }
    val orderedKeys = subfolders.map { folderKey(it.id) } + sortedNotes.map { noteKey(it.id) }
    LaunchedEffect(orderedKeys) { selection.retain(orderedKeys) }

    val selFolders = selection.keys.filter { it.startsWith("f") }.map { it.drop(1).toLong() }
    val selNotes = selection.keys.filter { it.startsWith("n") }.map { it.drop(1).toLong() }

    fun moveSelected(target: Long?) {
        val nCount = selNotes.size; val fCount = selFolders.size
        val notesToMove = selNotes; val foldersToMove = selFolders
        selection.clear()
        scope.launch {
            val skipped = repo.moveItems(notesToMove, foldersToMove, target)
            val dest = target?.let { t -> allFolders.firstOrNull { it.id == t }?.name } ?: "筆記（最上層）"
            val moved = nCount + fCount - skipped
            mainToast(ctx, "已移動 $moved 項到「$dest」" + if (skipped > 0) "（$skipped 個資料夾無法移進自己裡面）" else "")
        }
    }

    BackHandler(enabled = selection.active) { selection.clear() }

    val keysNow = latest(orderedKeys)
    val folderKeys = latest(subfolders.map { folderKey(it.id) }.toSet())
    val dropAction = latest<(String) -> Unit> { target -> moveSelected(target.drop(1).toLong()) }
    val tapAction = latest<(String) -> Unit> { k ->
        if (selection.active) selection.toggle(k)
        else if (k.startsWith("f")) onOpenFolder(k.drop(1).toLong()) else onOpenNote(k.drop(1).toLong())
    }

    Scaffold(
        topBar = {
            when {
                selection.active -> SelectionTopBar(selection.keys.size, onClose = { selection.clear() }) {
                    IconButton(onClick = { dialog = "moveSel" to null }) { Icon(Icons.AutoMirrored.Filled.DriveFileMove, "移動到") }
                    IconButton(onClick = { dialog = "groupSel" to null }) { Icon(Icons.Default.CreateNewFolder, "裝進新資料夾") }
                    IconButton(onClick = { dialog = "deleteSel" to null }) { Icon(Icons.Default.Delete, "刪除所選") }
                    Box {
                        IconButton(onClick = { selMenu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                        DropdownMenu(selMenu, { selMenu = false }) {
                            DropdownMenuItem(text = { Text("全選") }, onClick = { selMenu = false; selection.keys = orderedKeys.toSet() })
                            if (selection.keys.size == 1) {
                                DropdownMenuItem(text = { Text("重新命名") }, onClick = {
                                    selMenu = false
                                    val k = selection.keys.single()
                                    val target: Any? = if (k.startsWith("f")) subfolders.firstOrNull { folderKey(it.id) == k }
                                    else sortedNotes.firstOrNull { noteKey(it.id) == k }
                                    dialog = "rename" to target
                                })
                            }
                        }
                    }
                }
                searching -> TopAppBar(
                    navigationIcon = { IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "關閉搜尋") } },
                    title = {
                        TextField(
                            query, { query = it }, singleLine = true,
                            placeholder = { Text("搜尋所有筆記的標題與內容") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    actions = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "清除") } },
                )
                else -> TopAppBar(
                    navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } } },
                    title = {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (path.size > 1) {
                                Text(
                                    "筆記 / " + path.dropLast(1).joinToString(" / ") { it.name },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "搜尋") }
                        IconButton(onClick = { dialog = "newFolder" to null }) { Icon(Icons.Default.CreateNewFolder, "新增資料夾") }
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "排序") }
                            DropdownMenu(sortMenu, { sortMenu = false }) {
                                SortMode.entries.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text((if (m == sort) "✓ " else "    ") + m.label) },
                                        onClick = { sort = m; sortMenu = false },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!searching && !selection.active) Box {
                ExtendedFloatingActionButton(
                    modifier = Modifier.horizontalSafe(),
                    onClick = { addMenu = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("新增") },
                )
                DropdownMenu(addMenu, { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("新增筆記（貼上 Markdown）") },
                        leadingIcon = { Icon(Icons.Default.Description, null) },
                        onClick = { addMenu = false; onNewNote(folderId) },
                    )
                    DropdownMenuItem(
                        text = { Text("匯入 PDF／Word／PPT") },
                        leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                        onClick = { addMenu = false; docPicker.launch(DocumentImporter.PICKER_MIMES) },
                    )
                }
            }
        },
        bottomBar = { if (!selection.active) bottomBar() },
    ) { pad ->
        if (searching) {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                if (query.isNotBlank() && results.isEmpty()) {
                    item { Text("找不到「$query」", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(results, key = { it.id }) { n ->
                    NoteRow(
                        n, subtitle = folderPath(n.folderId, allFolders).joinToString(" / ") { it.name }.ifEmpty { "筆記" },
                        state = RowState(), onClick = { onOpenNote(n.id) }, onLongClick = null, onAction = { dialog = it to n },
                    )
                }
            }
            return@Scaffold
        }
        if (notes == null) return@Scaffold
        if (subfolders.isEmpty() && sortedNotes.isEmpty()) {
            EmptyState(
                if (folderId == null) "還沒有筆記" else "這個資料夾是空的",
                "點「新增筆記」貼上 LLM 整理的 Markdown 筆記，或用右上角按鈕建立資料夾來分類。\n小技巧：長按項目可以多選、拖曳到資料夾。",
                Modifier.padding(pad),
            )
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().listSelectionGestures(
                    list = list, selection = selection, drag = drag,
                    selectableKeys = { keysNow.value },
                    canDropOn = { it in folderKeys.value },
                    onDrop = { dropAction.value(it) },
                    onTap = { tapAction.value(it) },
                    scope = scope, haptics = haptics, edgePx = edgePx,
                ),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(subfolders, key = { folderKey(it.id) }) { f ->
                    val k = folderKey(f.id)
                    FolderRow(
                        f,
                        state = RowState(selection.active, k in selection.keys, drag.active && drag.hoverTarget == k, drag.active && k in selection.keys),
                        onClick = { if (selection.active) selection.toggle(k) else onOpenFolder(f.id) },
                        onLongClick = { selection.toggle(k) },
                        onAction = { dialog = it to f },
                    )
                }
                if (subfolders.isNotEmpty() && sortedNotes.isNotEmpty()) item(key = "divider") { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                items(sortedNotes, key = { noteKey(it.id) }) { n ->
                    val k = noteKey(n.id)
                    NoteRow(
                        n, subtitle = null,
                        state = RowState(selection.active, k in selection.keys, false, drag.active && k in selection.keys),
                        onClick = { if (selection.active) selection.toggle(k) else onOpenNote(n.id) },
                        onLongClick = { selection.toggle(k) },
                        onAction = { dialog = it to n },
                    )
                }
            }
            // 拖曳中：跟著手指的標籤
            if (drag.active) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 6.dp,
                    modifier = Modifier.offset { IntOffset(drag.position.x.roundToInt() - 40, drag.position.y.roundToInt() - 120) },
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                        Text(
                            " 移動 ${selection.keys.size} 項" + (drag.hoverTarget?.let { t ->
                                subfolders.firstOrNull { folderKey(it.id) == t }?.let { " → ${it.name}" }
                            } ?: ""),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }

    importing?.let { name ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text("匯入中…") },
            text = {
                Column {
                    Text("「$name」", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            },
            confirmButton = {},
        )
    }

    val (kind, target) = dialog ?: (null to null)
    val close = { dialog = null }
    val selectedExclude = selFolders.flatMap { NoteRepository.descendantsOf(it, allFolders) + it }.toSet()
    when (kind) {
        "newFolder" -> TextInputDialog("新增資料夾", "", "資料夾名稱", confirm = "建立", onDismiss = close) {
            scope.launch { repo.createFolder(it, folderId) }
        }
        "rename" -> when (target) {
            is FolderSummary -> TextInputDialog("重新命名資料夾", target.name, "資料夾名稱", onDismiss = close) {
                selection.clear(); scope.launch { repo.renameFolder(target.id, it) }
            }
            is NoteSummary -> TextInputDialog("重新命名筆記", target.title, "筆記名稱", onDismiss = close) {
                selection.clear(); scope.launch { repo.renameNote(target.id, it) }
            }
        }
        "move" -> when (target) {
            is FolderSummary -> MoveToDialog(
                "把「${target.name}」移動到…", allFolders, target.parentId,
                exclude = NoteRepository.descendantsOf(target.id, allFolders) + target.id,
                onDismiss = close,
            ) { scope.launch { repo.moveFolder(target.id, it) } }
            is NoteSummary -> MoveToDialog("把「${target.title}」移動到…", allFolders, target.folderId, onDismiss = close) {
                scope.launch { repo.moveNote(target.id, it) }
            }
        }
        "delete" -> when (target) {
            is FolderSummary -> ConfirmDeleteDialog(
                "刪除資料夾？",
                "只會刪除「${target.name}」這個資料夾，裡面的 ${target.noteCount} 篇筆記和 ${target.folderCount} 個子資料夾會移到上一層，不會被刪掉。",
                close,
            ) { scope.launch { repo.deleteFolder(target.id) } }
            is NoteSummary -> ConfirmDeleteDialog("刪除筆記？", "「${target.title}」將永久刪除。", close) {
                scope.launch { repo.deleteNote(target.id) }
            }
        }
        // ---- 多選操作 ----
        "moveSel" -> MoveToDialog("把 ${selection.keys.size} 項移動到…", allFolders, folderId, exclude = selectedExclude, onDismiss = close) {
            moveSelected(it)
        }
        "groupSel" -> TextInputDialog("裝進新資料夾", "", "新資料夾名稱", confirm = "建立並移入", onDismiss = close) { name ->
            val ns = selNotes; val fs = selFolders
            selection.clear()
            scope.launch {
                repo.groupIntoNewFolder(name, folderId, ns, fs)
                mainToast(ctx, "已建立「$name」並放入 ${ns.size + fs.size} 項")
            }
        }
        "deleteSel" -> ConfirmDeleteDialog(
            "刪除所選的 ${selection.keys.size} 項？",
            buildString {
                if (selNotes.isNotEmpty()) append("${selNotes.size} 篇筆記將永久刪除。")
                if (selFolders.isNotEmpty()) append("\n${selFolders.size} 個資料夾會被刪除，但裡面沒被選到的筆記會移到上一層，不會遺失。")
            }.trim(),
            close,
        ) {
            val ns = selNotes; val fs = selFolders
            selection.clear()
            scope.launch { repo.deleteItems(ns, fs) }
        }
    }
}

@Composable
private fun NoteIcon(sourceType: Int) = when (sourceType) {
    SOURCE_PDF -> Icon(Icons.Default.PictureAsPdf, "PDF", tint = Color(0xFFD32F2F))
    SOURCE_DOCX -> Icon(Icons.Default.Article, "Word", tint = Color(0xFF1565C0))
    SOURCE_PPTX -> Icon(Icons.Default.Slideshow, "PowerPoint", tint = Color(0xFFE65100))
    else -> Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
}

/** 列的顯示狀態 */
private data class RowState(
    val selecting: Boolean = false,
    val selected: Boolean = false,
    /** 拖曳中、手指正停在這個資料夾上 */
    val dropTarget: Boolean = false,
    /** 正被拖曳的項目（淡化） */
    val dragging: Boolean = false,
)

@Composable
private fun FolderRow(f: FolderSummary, state: RowState, onClick: () -> Unit, onLongClick: (() -> Unit)?, onAction: (String) -> Unit) {
    ListRow(
        icon = { Icon(Icons.Default.Folder, null, tint = Color(0xFFFFA000)) },
        title = f.name,
        subtitle = buildList {
            add("${f.noteCount} 篇筆記")
            if (f.folderCount > 0) add("${f.folderCount} 個資料夾")
        }.joinToString(" · "),
        preview = null,
        state = state,
        onClick = onClick,
        onLongClick = onLongClick,
        onAction = onAction,
    )
}

@Composable
private fun NoteRow(n: NoteSummary, subtitle: String?, state: RowState, onClick: () -> Unit, onLongClick: (() -> Unit)?, onAction: (String) -> Unit) {
    val preview = remember(n.preview) {
        n.preview.lineSequence().map { it.trim().trimStart('#', '>', '-', '*', ' ') }
            .filter { it.isNotBlank() && !it.startsWith("|") && !it.startsWith("```") }
            .drop(1).take(2).joinToString(" ").let { Markdown.plainText(it) }
    }
    ListRow(
        icon = { NoteIcon(n.sourceType) },
        title = n.title,
        subtitle = listOfNotNull(
            subtitle,
            when (n.sourceType) { SOURCE_PDF -> "PDF"; SOURCE_DOCX -> "Word"; SOURCE_PPTX -> "PowerPoint"; else -> null },
            if (n.sourceType == SOURCE_PDF) "${n.pageCount} 頁"
            else if (n.pageCount >= 2) "${if (n.sourceType == SOURCE_PPTX) "已看" else "學習進度"} ${n.completedCount}/${n.pageCount} ${if (n.sourceType == SOURCE_PPTX) "張" else "節"}" else null,
            formatTime(n.updatedAt),
            if (n.sourceType == SOURCE_PDF) null else "約 ${n.length} 字",
        ).joinToString(" · "),
        preview = if (n.sourceType == SOURCE_PDF) null else preview.ifBlank { null },
        state = state,
        onClick = onClick,
        onLongClick = onLongClick,
        onAction = onAction,
    )
}

@Composable
private fun ListRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    preview: String?,
    state: RowState,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onAction: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val bg = when {
        state.dropTarget -> scheme.primaryContainer
        state.selected -> scheme.secondaryContainer
        else -> Color.Transparent
    }
    Row(
        Modifier.fillMaxWidth()
            .background(bg)
            .then(if (state.dropTarget) Modifier.border(2.dp, scheme.primary, RoundedCornerShape(4.dp)) else Modifier)
            .then(
                if (onLongClick == null) Modifier.clickable(onClick = onClick) // 搜尋結果：一般點擊
                else Modifier.semantics {                                      // 清單手勢處理點擊；這裡提供無障礙動作
                    onClick(label = "開啟") { onClick(); true }
                    onLongClick(label = "選取") { onLongClick(); true }
                }
            )
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = if (state.selecting) 16.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (state.selecting) {
                if (state.selected) Icon(Icons.Default.CheckCircle, "已選取", tint = scheme.primary)
                else Icon(Icons.Default.RadioButtonUnchecked, "未選取", tint = scheme.outline)
            } else icon()
        }
        Column(Modifier.weight(1f)) {
            val alpha = if (state.dragging) 0.4f else 1f
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = scheme.onSurface.copy(alpha = alpha))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant.copy(alpha = alpha), maxLines = 1)
            preview?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!state.selecting) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多選項") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("重新命名") }, onClick = { menu = false; onAction("rename") })
                    DropdownMenuItem(text = { Text("移動到…") }, onClick = { menu = false; onAction("move") })
                    DropdownMenuItem(text = { Text("刪除") }, onClick = { menu = false; onAction("delete") })
                }
            }
        }
    }
}
