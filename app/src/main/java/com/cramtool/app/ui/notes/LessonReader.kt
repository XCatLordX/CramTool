package com.cramtool.app.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cramtool.app.data.NoteEntity
import com.cramtool.app.notes.LessonPage
import com.cramtool.app.notes.MdBlock
import com.cramtool.app.ui.cramApp
import com.cramtool.app.ui.theme.CorrectGreen
import kotlinx.coroutines.launch

/**
 * 課程式閱讀：一個小節一頁。可以左右滑動換頁，或按頁面最下方右下角的「下一節」；
 * 往下一節前進時會把目前小節標為已完成，並記住讀到哪一頁。
 */
@Composable
fun LessonReader(
    note: NoteEntity,
    pages: List<LessonPage>,
    scale: Float,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    chrome: ReaderChrome,
) {
    val app = cramApp()
    val repo = app.notes
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = note.lessonPage.coerceIn(0, pages.lastIndex)) { pages.size }
    // 資料庫的完成紀錄改變時（例如「重設學習進度」）同步回來
    // 單一狀態物件（換頁監聽器會一直寫入同一個）；資料庫變動時同步過來
    var completed by remember { mutableStateOf(note.completedSet) }
    LaunchedEffect(note.completedPages) { completed = note.completedSet }
    LaunchedEffect(note.completedPages, note.lessonPage) {
        if (note.completedPages.isEmpty() && note.lessonPage == 0 && pager.currentPage != 0) pager.scrollToPage(0)
    }
    var showToc by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    fun save() {
        val snapshot = completed
        val p = pager.settledPage
        app.appScope.launch { repo.saveLessonState(note.id, p, snapshot) }
    }

    // 換頁（滑動或按鈕）停下來時：往下一頁前進就把上一頁標為完成，並存檔
    LaunchedEffect(pager) {
        var prev = pager.settledPage
        snapshotFlow { pager.settledPage }.collect { p ->
            if (p == prev + 1) completed = completed + pages[prev].title
            prev = p
            save()
        }
    }

    fun go(to: Int, markCurrentDone: Boolean) {
        if (markCurrentDone) { completed = completed + pages[pager.currentPage].title; save() }
        scope.launch { pager.animateScrollToPage(to.coerceIn(0, pages.lastIndex)) }
    }

    val page = pager.currentPage
    val cur = pages[page]
    val doneCount = pages.count { it.title in completed }

    ReaderScaffold(
        title = note.title,
        subtitle = cur.title,
        progress = { (pager.currentPage + 1).toFloat() / pages.size },
        onBack = onBack,
        actions = {
            IconButton(onClick = { showToc = true }) { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, "目錄") }
            actions()
        },
        chrome = chrome,
    ) { tapModifier ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { "$it:${pages[it].title}" },
        ) { i ->
            val p = pages[i]
            SelectionContainer {
                LazyColumn(
                    state = rememberLazyListState(), // 每頁各自從頂端開始
                    modifier = Modifier.fillMaxSize().then(tapModifier),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                ) {
                    item { PageHeader(p, i, pages.size, p.title in completed) }
                    items(pageBlocks(p)) { b -> MdBlockView(b, scale) }
                    item {
                        PageNav(
                            prev = pages.getOrNull(i - 1),
                            next = pages.getOrNull(i + 1),
                            onPrev = { go(i - 1, markCurrentDone = false) },
                            onNext = { go(i + 1, markCurrentDone = true) },
                            onFinish = { go(i, markCurrentDone = true); finished = true },
                        )
                    }
                    item { SwipeHint(hasNext = i < pages.lastIndex, hasPrev = i > 0) }
                }
            }
        }
    }

    if (showToc) {
        LessonToc(pages, page, completed, doneCount, onPick = { scope.launch { pager.scrollToPage(it) } }, onDismiss = { showToc = false })
    }
    if (finished) {
        AlertDialog(
            onDismissRequest = { finished = false },
            title = { Text("🎉 完成課程！") },
            text = { Text("「${note.title}」共 ${pages.size} 節，已完成 ${pages.count { it.title in completed }} 節。") },
            confirmButton = { TextButton(onClick = { finished = false; onBack() }) { Text("回到筆記列表") } },
            dismissButton = { TextButton(onClick = { finished = false; scope.launch { pager.scrollToPage(0) } }) { Text("從第一節複習") } },
        )
    }
}

/**
 * 頁面要顯示的區塊：本節標題放大成頁面大標題；
 * 標題前面被帶進來的章標題／課程標題省略（章名改由上方小標籤顯示）。
 */
private fun pageBlocks(p: LessonPage): List<MdBlock> {
    val idx = p.blocks.indexOfFirst { it is MdBlock.Heading && com.cramtool.app.notes.Markdown.plainText(it.text) == p.title }
    if (idx < 0) return p.blocks
    val h = p.blocks[idx] as MdBlock.Heading
    return listOf(MdBlock.Heading(1, h.text)) + p.blocks.drop(idx + 1)
}

@Composable
private fun PageHeader(p: LessonPage, index: Int, total: Int, done: Boolean) {
    val alreadyShowsChapter = p.chapter == null
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "第 ${index + 1} / $total 節",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        if (!alreadyShowsChapter) {
            Text(
                p.chapter!!, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (done) {
            Icon(Icons.Default.CheckCircle, null, tint = CorrectGreen, modifier = Modifier.size(16.dp))
            Text("已完成", style = MaterialTheme.typography.labelMedium, color = CorrectGreen)
        }
    }
}

@Composable
private fun SwipeHint(hasNext: Boolean, hasPrev: Boolean) {
    val text = when {
        hasNext && hasPrev -> "← 也可以左右滑動換節 →"
        hasNext -> "也可以向左滑動到下一節 →"
        hasPrev -> "← 向右滑動回上一節"
        else -> return
    }
    Text(
        text, modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** 頁面最下方：左「上一節」、右下角「下一節」（含下一節標題預告） */
@Composable
private fun PageNav(prev: LessonPage?, next: LessonPage?, onPrev: () -> Unit, onNext: () -> Unit, onFinish: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp)) {
        androidx.compose.material3.HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (prev != null) {
                OutlinedButton(onClick = onPrev) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                    Text(" 上一節")
                }
            }
            Spacer(Modifier.weight(1f))
            if (next != null) {
                Button(onClick = onNext, modifier = Modifier.widthIn(max = 240.dp)) {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f, fill = false)) {
                        Text("下一節", style = MaterialTheme.typography.labelSmall)
                        Text(next.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.size(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            } else {
                Button(onClick = onFinish) { Text("完成課程 🎉") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonToc(
    pages: List<LessonPage>,
    current: Int,
    completed: Set<String>,
    doneCount: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, current - 3))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("目錄", style = MaterialTheme.typography.titleLarge)
            Text(
                "已完成 $doneCount / ${pages.size} 節",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(state = list, modifier = Modifier.navigationBarsPadding(), contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(pages) { i, p ->
                val done = p.title in completed
                val selected = i == current
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { scope.launch { sheet.hide(); onPick(i); onDismiss() } }
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(start = (20 + maxOf(0, p.depth - 1) * 20).dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (done) Icon(Icons.Default.CheckCircle, "已完成", tint = CorrectGreen, modifier = Modifier.size(20.dp))
                    else Icon(Icons.Default.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                    Text(
                        p.title,
                        modifier = Modifier.padding(start = 12.dp),
                        style = if (p.depth <= 1) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (p.depth <= 1 || selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
