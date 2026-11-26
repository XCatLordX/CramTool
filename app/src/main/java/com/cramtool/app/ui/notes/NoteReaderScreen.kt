package com.cramtool.app.ui.notes

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cramtool.app.ui.horizontalSafe
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cramtool.app.data.NoteEntity
import com.cramtool.app.notes.Lesson
import com.cramtool.app.notes.Markdown
import com.cramtool.app.notes.MdBlock
import com.cramtool.app.notes.OutlineEntry
import com.cramtool.app.ui.cramApp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

const val VIEW_AUTO = 0
const val VIEW_SCROLL = 1
const val VIEW_PAGED = 2

@Composable
fun NoteReaderScreen(noteId: Long, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val repo = cramApp().notes
    val note by remember(noteId) { repo.observeNote(noteId) }.collectAsState(initial = null)
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(note) { if (note != null) loaded = true else if (loaded) onBack() } // 被刪除時返回
    val n = note ?: return
    key(n.id) { ReaderHost(n, onBack, onEdit) }
}

/** 決定用分頁或捲動模式，並提供兩者共用的選單與對話框 */
@Composable
private fun ReaderHost(note: NoteEntity, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val repo = cramApp().notes
    val scope = rememberCoroutineScope()
    val scale by repo.fontScale.collectAsState()
    val folders by remember { repo.observeAllFolders() }.collectAsState(initial = emptyList())

    val blocks = remember(note.content) { Markdown.parse(note.content) }
    val pages = remember(blocks) { Lesson.paginate(blocks) }
    val paged = when (note.viewMode) {
        VIEW_SCROLL -> false
        VIEW_PAGED -> true
        else -> pages.size >= 2
    }
    LaunchedEffect(pages.size) { if (note.pageCount != pages.size) repo.setPageCount(note.id, pages.size) }

    var menu by remember { mutableStateOf(false) }
    var showFont by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }

    // 全螢幕：隱藏標題列與系統狀態列／導覽列（從邊緣滑動可暫時叫出系統列）
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    DisposableEffect(fullscreen) {
        val window = (ctx as? Activity)?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (fullscreen) ctrl?.hide(WindowInsetsCompat.Type.systemBars()) else ctrl?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { ctrl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    val toggleFullscreen = {
        if (!fullscreen) Toast.makeText(ctx, "點一下內文即可叫出／收起工具列", Toast.LENGTH_SHORT).show()
        fullscreen = !fullscreen
    }

    val actions: @Composable RowScope.() -> Unit = {
        IconButton(onClick = toggleFullscreen) { Icon(Icons.Default.Fullscreen, "全螢幕") }
        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多") }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(
                text = { Text("字體大小") },
                leadingIcon = { Icon(Icons.Default.TextFields, null) },
                onClick = { menu = false; showFont = true },
            )
            DropdownMenuItem(
                text = { Text(if (paged) "切換為捲動閱讀" else "切換為分頁閱讀（一節一頁）") },
                onClick = { menu = false; scope.launch { repo.setViewMode(note.id, if (paged) VIEW_SCROLL else VIEW_PAGED) } },
            )
            if (paged) DropdownMenuItem(text = { Text("重設學習進度") }, onClick = { menu = false; dialog = "reset" })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("編輯") }, onClick = { menu = false; onEdit(note.id) })
            DropdownMenuItem(text = { Text("重新命名") }, onClick = { menu = false; dialog = "rename" })
            DropdownMenuItem(text = { Text("移動到…") }, onClick = { menu = false; dialog = "move" })
            DropdownMenuItem(text = { Text("刪除") }, onClick = { menu = false; dialog = "delete" })
        }
    }

    val chrome = ReaderChrome(fullscreen = fullscreen, onTapContent = { fullscreen = !fullscreen })
    if (paged) LessonReader(note, pages, scale, onBack, actions, chrome)
    else ScrollReader(note, blocks, scale, onBack, actions, chrome)

    if (showFont) {
        AlertDialog(
            onDismissRequest = { showFont = false },
            title = { Text("字體大小") },
            text = {
                Column {
                    Text("預覽文字 Aa 字體 ${(scale * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge.let {
                        it.copy(fontSize = it.fontSize * scale)
                    })
                    Slider(value = scale, onValueChange = { repo.setFontScale(it) }, valueRange = 0.8f..1.6f, steps = 7)
                }
            },
            confirmButton = { TextButton(onClick = { showFont = false }) { Text("完成") } },
        )
    }
    when (dialog) {
        "rename" -> TextInputDialog("重新命名", note.title, "筆記名稱", onDismiss = { dialog = null }) {
            scope.launch { repo.renameNote(note.id, it) }
        }
        "move" -> MoveToDialog("移動到…", folders, note.folderId, onDismiss = { dialog = null }) {
            scope.launch { repo.moveNote(note.id, it) }
        }
        "delete" -> ConfirmDeleteDialog("刪除筆記？", "「${note.title}」將永久刪除。", { dialog = null }) {
            scope.launch { repo.deleteNote(note.id) }
        }
        "reset" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("重設學習進度？") },
            text = { Text("所有小節的完成標記會清除，並回到第一節。") },
            confirmButton = {
                TextButton(onClick = { dialog = null; scope.launch { repo.saveLessonState(note.id, 0, emptySet()) } }) { Text("重設") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } },
        )
    }
}

// ======================= 捲動模式 =======================

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun ScrollReader(
    note: NoteEntity,
    blocks: List<MdBlock>,
    scale: Float,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    chrome: ReaderChrome,
) {
    val app = cramApp()
    val repo = app.notes
    val outline = remember(blocks) { Markdown.outline(blocks) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = note.readPosition.coerceIn(0, maxOf(0, blocks.size - 1)))

    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex }.debounce(800).collect { repo.saveReadPosition(note.id, it) }
    }
    DisposableEffect(Unit) {
        onDispose { app.appScope.launch { repo.saveReadPosition(note.id, list.firstVisibleItemIndex) } }
    }
    val current by remember(outline) { derivedStateOf { outline.lastOrNull { it.blockIndex <= list.firstVisibleItemIndex } } }
    val progress by remember(blocks) {
        derivedStateOf {
            val info = list.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) 0f else (((info.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1).toFloat() / total).coerceIn(0f, 1f)
        }
    }
    var showOutline by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = readerInsets(),
        topBar = {
            ReaderTopBar(note.title, current?.title?.takeIf { it != note.title }, { progress }, onBack, actions, chrome.fullscreen)
        },
        floatingActionButton = {
            if (outline.isNotEmpty() && !chrome.fullscreen) {
                ExtendedFloatingActionButton(
                    modifier = Modifier.horizontalSafe(),
                    onClick = { showOutline = true },
                    icon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null) },
                    text = { Text("大綱") },
                )
            }
        },
    ) { pad ->
        // 底部留白一個畫面高，讓最後幾個標題也能捲到頂端（大綱跳轉後標題會在最上方）
        BoxWithConstraints(Modifier.fillMaxSize().padding(pad).tapToToggle(chrome)) {
            val tail = maxHeight - 48.dp
            SelectionContainer {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = tail),
                ) {
                    itemsIndexed(blocks) { _, b -> MdBlockView(b, scale) }
                }
            }
        }
    }
    if (showOutline) OutlineSheet(outline, current, list, onDismiss = { showOutline = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    title: String,
    subtitle: String?,
    progress: () -> Float,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    fullscreen: Boolean = false,
) {
    if (fullscreen) {
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(2.dp).horizontalSafe())
        return
    }
    Column {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    subtitle?.let {
                        Text(
                            it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            actions = actions,
        )
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().horizontalSafe())
    }
}

/** 閱讀器的全螢幕狀態，以及「點內文切換工具列」 */
data class ReaderChrome(val fullscreen: Boolean, val onTapContent: () -> Unit)

/** 點一下內文（非按鈕處）就切換全螢幕；長按選字不受影響 */
fun Modifier.tapToToggle(chrome: ReaderChrome): Modifier =
    pointerInput(chrome.onTapContent) { detectTapGestures(onTap = { chrome.onTapContent() }) }

/** 閱讀器內容避開系統列與鏡頭挖孔（全螢幕時系統列高度為 0） */
@Composable
fun readerInsets(): WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutlineSheet(outline: List<OutlineEntry>, current: OutlineEntry?, list: LazyListState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val minLevel = outline.minOfOrNull { it.level } ?: 1
    val sheetList = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, outline.indexOf(current) - 3))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("大綱", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                scope.launch { list.scrollToItem(0); sheet.hide(); onDismiss() }
            }) {
                Icon(Icons.Default.VerticalAlignTop, null)
                Text(" 回到開頭")
            }
        }
        LazyColumn(state = sheetList, modifier = Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(outline) { _, e ->
                val selected = e == current
                Text(
                    e.title,
                    style = when (e.level - minLevel) {
                        0 -> MaterialTheme.typography.titleMedium
                        1 -> MaterialTheme.typography.bodyLarge
                        else -> MaterialTheme.typography.bodyMedium
                    },
                    fontWeight = if (selected || e.level == minLevel) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            scope.launch {
                                list.scrollToItem(e.blockIndex)
                                sheet.hide()
                                onDismiss()
                            }
                        }
                        .padding(start = (20 + (e.level - minLevel) * 18).dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                )
            }
        }
    }
}
