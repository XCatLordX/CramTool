package com.cramtool.app.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cramtool.app.data.FolderSummary
import com.cramtool.app.data.NoteRepository
import com.cramtool.app.data.NoteSummary
import com.cramtool.app.notes.Markdown
import com.cramtool.app.ui.EmptyState
import com.cramtool.app.ui.cramApp
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private enum class SortMode(val label: String) { UPDATED("最近更新"), OPENED("最近閱讀"), NAME("名稱") }

/** 檔案總管式的筆記列表。folderId = null 為最上層 */
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
    var dialog by remember { mutableStateOf<Pair<String, Any?>?>(null) }

    val path = folderPath(folderId, allFolders)
    val title = path.lastOrNull()?.name ?: "筆記"
    val sortedNotes = notes.orEmpty().let { ns ->
        when (sort) {
            SortMode.UPDATED -> ns.sortedByDescending { it.updatedAt }
            SortMode.OPENED -> ns.sortedByDescending { it.lastOpenedAt ?: 0 }
            SortMode.NAME -> ns.sortedBy { it.title.lowercase() }
        }
    }

    Scaffold(
        topBar = {
            if (searching) {
                TopAppBar(
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
            } else {
                TopAppBar(
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
            if (!searching) ExtendedFloatingActionButton(
                onClick = { onNewNote(folderId) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("新增筆記") },
            )
        },
        bottomBar = bottomBar,
    ) { pad ->
        if (searching) {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                if (query.isNotBlank() && results.isEmpty()) {
                    item { Text("找不到「$query」", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(results, key = { it.id }) { n ->
                    NoteRow(n, subtitle = folderPath(n.folderId, allFolders).joinToString(" / ") { it.name }.ifEmpty { "筆記" },
                        onClick = { onOpenNote(n.id) }, onAction = { dialog = it to n })
                }
            }
            return@Scaffold
        }
        if (notes == null) return@Scaffold
        if (subfolders.isEmpty() && sortedNotes.isEmpty()) {
            EmptyState(
                if (folderId == null) "還沒有筆記" else "這個資料夾是空的",
                "點「新增筆記」貼上 LLM 整理的 Markdown 筆記，或用右上角按鈕建立資料夾來分類。",
                Modifier.padding(pad),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            items(subfolders, key = { "f${it.id}" }) { f ->
                FolderRow(f, onClick = { onOpenFolder(f.id) }, onAction = { dialog = it to f })
            }
            if (subfolders.isNotEmpty() && sortedNotes.isNotEmpty()) item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            items(sortedNotes, key = { "n${it.id}" }) { n ->
                NoteRow(n, subtitle = null, onClick = { onOpenNote(n.id) }, onAction = { dialog = it to n })
            }
        }
    }

    val (kind, target) = dialog ?: (null to null)
    val close = { dialog = null }
    when (kind) {
        "newFolder" -> TextInputDialog("新增資料夾", "", "資料夾名稱", confirm = "建立", onDismiss = close) {
            scope.launch { repo.createFolder(it, folderId) }
        }
        "rename" -> when (target) {
            is FolderSummary -> TextInputDialog("重新命名資料夾", target.name, "資料夾名稱", onDismiss = close) {
                scope.launch { repo.renameFolder(target.id, it) }
            }
            is NoteSummary -> TextInputDialog("重新命名筆記", target.title, "筆記名稱", onDismiss = close) {
                scope.launch { repo.renameNote(target.id, it) }
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
    }
}

@Composable
private fun FolderRow(f: FolderSummary, onClick: () -> Unit, onAction: (String) -> Unit) {
    ListRow(
        icon = { Icon(Icons.Default.Folder, null, tint = Color(0xFFFFA000)) },
        title = f.name,
        subtitle = buildList {
            add("${f.noteCount} 篇筆記")
            if (f.folderCount > 0) add("${f.folderCount} 個資料夾")
        }.joinToString(" · "),
        preview = null,
        onClick = onClick,
        onAction = onAction,
    )
}

@Composable
private fun NoteRow(n: NoteSummary, subtitle: String?, onClick: () -> Unit, onAction: (String) -> Unit) {
    val preview = remember(n.preview) {
        n.preview.lineSequence().map { it.trim().trimStart('#', '>', '-', '*', ' ') }
            .filter { it.isNotBlank() && !it.startsWith("|") && !it.startsWith("```") }
            .drop(1).take(2).joinToString(" ").let { Markdown.plainText(it) }
    }
    ListRow(
        icon = { Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary) },
        title = n.title,
        subtitle = listOfNotNull(subtitle, formatTime(n.updatedAt), "約 ${n.length} 字").joinToString(" · "),
        preview = preview.ifBlank { null },
        onClick = onClick,
        onAction = onAction,
    )
}

@Composable
private fun ListRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    preview: String?,
    onClick: () -> Unit,
    onAction: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            preview?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
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
