package com.cramtool.app.ui.notes

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pageview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.cramtool.app.data.NoteEntity
import com.cramtool.app.ui.EmptyState
import com.cramtool.app.ui.cramApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

/** PdfRenderer 包裝：同一時間只能開一頁，所以用 Mutex 排隊；頁面圖片用 LRU 快取 */
class PdfDoc(file: File) : Closeable {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val mutex = Mutex()
    val pageCount = renderer.pageCount
    /** 每頁的長寬比（高／寬） */
    val ratios: List<Float> = (0 until pageCount).map { i ->
        renderer.openPage(i).use { it.height.toFloat() / it.width.coerceAtLeast(1) }
    }
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(page: Int, width: Int): Bitmap? = cache.get("$page@$width")

    suspend fun render(page: Int, width: Int): Bitmap = cache.get("$page@$width") ?: mutex.withLock {
        withContext(Dispatchers.IO) {
            cache.get("$page@$width") ?: renderer.openPage(page).use { p ->
                val h = (width * p.height.toFloat() / p.width).roundToInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888).also { bmp ->
                    bmp.eraseColor(AColor.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put("$page@$width", bmp)
                }
            }
        }
    }

    override fun close() { runCatching { renderer.close() }; runCatching { pfd.close() } }
}

/**
 * PDF 閱讀：原樣顯示每一頁。雙指縮放（1–4 倍）、雙擊切換 1／2 倍、跳頁、全螢幕，記住讀到哪一頁。
 */
@OptIn(FlowPreview::class)
@Composable
fun PdfReader(note: NoteEntity, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit, chrome: ReaderChrome) {
    val app = cramApp()
    val repo = app.notes
    val file = note.sourcePath?.let(::File)
    val doc = remember(note.sourcePath) { file?.takeIf { it.exists() }?.let { runCatching { PdfDoc(it) }.getOrNull() } }
    DisposableEffect(doc) { onDispose { doc?.close() } }

    if (doc == null || doc.pageCount == 0) {
        ReaderScaffold(note.title, null, { 0f }, onBack, actions, chrome) {
            EmptyState(
                "無法開啟這份 PDF",
                when {
                    file?.exists() == false -> "原始檔案不在這支手機上（備份不包含 PDF 檔案）。請重新匯入這份 PDF。"
                    doc != null -> "這份 PDF 沒有任何頁面。"
                    else -> "檔案可能已損壞。"
                },
            )
        }
        return
    }

    val list = rememberLazyListState(initialFirstVisibleItemIndex = note.readPosition.coerceIn(0, doc.pageCount - 1))
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex }.debounce(800).collect { repo.saveReadPosition(note.id, it) }
    }
    DisposableEffect(Unit) { onDispose { app.appScope.launch { repo.saveReadPosition(note.id, list.firstVisibleItemIndex) } } }

    val page by remember { derivedStateOf { list.firstVisibleItemIndex } }
    var scale by remember { mutableFloatStateOf(1f) }
    var jump by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hScroll = rememberScrollState()
    val onTap by rememberUpdatedState(chrome.onTapContent)
    val fullscreen by rememberUpdatedState(chrome.fullscreen)

    ReaderScaffold(
        title = note.title,
        subtitle = "第 ${page + 1} / ${doc.pageCount} 頁" + if (scale > 1.01f) " · ${(scale * 100).roundToInt()}%" else "",
        progress = { (page + 1).toFloat() / doc.pageCount },
        onBack = onBack,
        actions = {
            IconButton(onClick = { jump = true }) { Icon(Icons.Default.Pageview, "跳到指定頁") }
            actions()
        },
        chrome = chrome,
    ) { _ ->
        BoxWithConstraints(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
                // 雙指縮放：在 Initial 階段攔截，避免和捲動衝突；單指事件照常交給捲動
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            if (ev.changes.count { it.pressed } >= 2) {
                                scale = (scale * ev.calculateZoom()).coerceIn(1f, 4f)
                                ev.changes.forEach { it.consume() }
                            }
                        } while (ev.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (fullscreen) onTap() },
                        onDoubleTap = { scale = if (scale > 1.4f) 1f else 2f },
                    )
                },
        ) {
            val pageWidth = maxWidth * scale
            val density = LocalDensity.current
            // 實際渲染寬度（像素），分級避免縮放時一直重畫
            val renderPx = with(density) { (pageWidth.toPx() / 200).roundToInt().coerceAtLeast(1) * 200 }.coerceAtMost(2800)
            Box(Modifier.fillMaxSize().horizontalScroll(hScroll, enabled = scale > 1.01f)) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.width(pageWidth).fillMaxHeight(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(doc.pageCount, key = { it }) { i ->
                        PdfPage(doc, i, renderPx, Modifier.padding(vertical = 4.dp).width(pageWidth).height(pageWidth * doc.ratios[i]))
                    }
                }
            }
            // 頁碼小標籤（全螢幕時也看得到）
            Text(
                "${page + 1} / ${doc.pageCount}",
                style = MaterialTheme.typography.labelMedium, color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                    .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }

    if (jump) {
        var target by remember { mutableFloatStateOf(page + 1f) }
        AlertDialog(
            onDismissRequest = { jump = false },
            title = { Text("跳到第 ${target.roundToInt()} 頁") },
            text = {
                Column {
                    Slider(target, { target = it }, valueRange = 1f..doc.pageCount.coerceAtLeast(2).toFloat(),
                        steps = (doc.pageCount - 2).coerceIn(0, 200))
                    Text("共 ${doc.pageCount} 頁", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    jump = false
                    scope.launch { list.scrollToItem((target.roundToInt() - 1).coerceIn(0, doc.pageCount - 1)) }
                }) { Text("前往") }
            },
            dismissButton = { TextButton(onClick = { jump = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int, widthPx: Int, modifier: Modifier) {
    val bmp by produceState(doc.cached(index, widthPx), index, widthPx) {
        value = runCatching { doc.render(index, widthPx) }.getOrNull() ?: value
    }
    Box(modifier.background(Color.White), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), "第 ${index + 1} 頁", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            ?: Text("${index + 1}", color = Color.LightGray)
    }
}

