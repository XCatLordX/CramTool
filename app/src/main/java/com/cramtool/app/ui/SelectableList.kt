package com.cramtool.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 多選狀態。key 為字串（例如 "n12" 筆記、"f3" 資料夾、"b5" 題庫） */
@Stable
class SelectionState {
    var keys by mutableStateOf(emptySet<String>())
    val active: Boolean get() = keys.isNotEmpty()
    fun toggle(k: String) { keys = if (k in keys) keys - k else keys + k }
    fun clear() { keys = emptySet() }
    /** 選取後已被刪除／不存在的項目要移除 */
    fun retain(existing: Collection<String>) { if (!existing.containsAll(keys)) keys = keys intersect existing.toSet() }
}

/** 拖曳搬移中的狀態（顯示跟著手指的標籤、目標資料夾高亮） */
@Stable
class DragMoveState {
    var active by mutableStateOf(false)
    var position by mutableStateOf(Offset.Zero)
    var hoverTarget by mutableStateOf<String?>(null)
}

/**
 * 清單的點擊與長按手勢（統一在清單層處理，列本身不放 clickable，避免攔截長按）：
 *  - 點一下：[onTap]（一般模式開啟、選取模式切換勾選）
 *  - 長按：選取該項（進入選取模式）
 *  - 長按後滑動：選取滑過的整段（靠近上下邊緣會自動捲動）
 *  - 選取模式中長按「已選取」的項目並拖曳：拖到 [canDropOn] 允許的目標上放開，呼叫 [onDrop]
 * 捲動清單、按列內的按鈕（例如 ⋮）不會被當成點擊。
 */
fun Modifier.listSelectionGestures(
    list: LazyListState,
    selection: SelectionState,
    drag: DragMoveState,
    selectableKeys: () -> List<String>,
    canDropOn: (target: String) -> Boolean,
    onDrop: (target: String) -> Unit,
    onTap: (key: String) -> Unit,
    scope: CoroutineScope,
    haptics: HapticFeedback,
    edgePx: Float,
): Modifier = pointerInput(list, selection, drag) {
    var mode = 0 // 0 = 無、1 = 範圍選取、2 = 拖曳搬移
    var anchor = -1
    var base = emptySet<String>()
    var last = Offset.Zero
    var scrollJob: Job? = null

    fun keyAt(p: Offset): String? = list.layoutInfo.visibleItemsInfo
        .firstOrNull { p.y.toInt() in it.offset until it.offset + it.size }?.key as? String

    fun update(p: Offset) {
        last = p
        val keys = selectableKeys()
        when (mode) {
            1 -> {
                val k = keyAt(p) ?: return
                val idx = keys.indexOf(k)
                if (idx < 0 || anchor < 0) return
                val range = keys.subList(minOf(anchor, idx), maxOf(anchor, idx) + 1)
                val next = base + range
                if (next != selection.keys) {
                    selection.keys = next
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            2 -> {
                drag.position = p
                val t = keyAt(p)?.takeIf { it !in selection.keys && canDropOn(it) }
                if (t != drag.hoverTarget) {
                    drag.hoverTarget = t
                    if (t != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }

    fun start(p: Offset): Boolean {
        val k = keyAt(p) ?: return false
        val keys = selectableKeys()
        if (k !in keys) return false
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (selection.active && k in selection.keys && keys.any(canDropOn)) {
            mode = 2; drag.active = true; drag.position = p
        } else {
            mode = 1; anchor = keys.indexOf(k); base = selection.keys
            selection.keys = base + k
        }
        last = p
        scrollJob = scope.launch {
            while (isActive) {
                val h = size.height
                val v = when {
                    last.y < edgePx -> -(edgePx - last.y) / 4f
                    last.y > h - edgePx -> (last.y - (h - edgePx)) / 4f
                    else -> 0f
                }
                if (v != 0f) { list.scrollBy(v); update(last) }
                delay(16)
            }
        }
        return true
    }

    fun stop() {
        scrollJob?.cancel(); scrollJob = null
        mode = 0; anchor = -1
        drag.active = false; drag.hoverTarget = null
    }

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress == null) {
            // 未達長按：若是乾淨的放開（沒被捲動或按鈕消耗）就是點擊
            val up = currentEvent.changes.firstOrNull { it.id == down.id }
            if (up != null && up.changedToUp() && !up.isConsumed) {
                keyAt(down.position)?.takeIf { it in selectableKeys() }?.let(onTap)
            }
            return@awaitEachGesture
        }
        if (!start(longPress.position)) return@awaitEachGesture
        longPress.consume()
        try {
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (ch.changedToUpIgnoreConsumed()) {
                    val target = drag.hoverTarget
                    val wasMove = mode == 2
                    stop()
                    if (wasMove && target != null) onDrop(target)
                    break
                }
                ch.consume()
                update(ch.position)
            }
        } finally {
            if (mode != 0) stop()
        }
    }
}

/** 選取模式的頂部列：✕ 關閉、已選取數量、操作按鈕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(count: Int, onClose: () -> Unit, actions: @Composable RowScope.() -> Unit) {
    TopAppBar(
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "取消選取") } },
        title = { Text("已選取 $count 項") },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
}

/** 讓 lambda 參數在 pointerInput 中永遠取最新值 */
@Composable
fun <T> latest(value: T) = rememberUpdatedState(value)
