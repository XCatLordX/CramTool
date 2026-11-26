package com.cramtool.app.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cramtool.app.data.FolderEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String,
    confirm: String = "確定",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()); onDismiss() }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 資料夾樹狀結構（依層級縮排），用於「移動到…」 */
data class FolderNode(val folder: FolderEntity, val depth: Int)

fun flattenTree(all: List<FolderEntity>, exclude: Set<Long> = emptySet()): List<FolderNode> {
    val children = all.filter { it.id !in exclude }.groupBy { it.parentId }
    val out = mutableListOf<FolderNode>()
    fun walk(parent: Long?, depth: Int) {
        children[parent].orEmpty().sortedBy { it.name.lowercase() }.forEach {
            out += FolderNode(it, depth)
            walk(it.id, depth + 1)
        }
    }
    walk(null, 0)
    return out
}

/** 資料夾路徑，例如「資料結構 / 期中」 */
fun folderPath(id: Long?, all: List<FolderEntity>): List<FolderEntity> {
    val byId = all.associateBy { it.id }
    val path = mutableListOf<FolderEntity>()
    var cur = id?.let { byId[it] }
    while (cur != null && path.size < 32) { path.add(0, cur); cur = cur.parentId?.let { byId[it] } }
    return path
}

@Composable
fun MoveToDialog(
    title: String,
    folders: List<FolderEntity>,
    current: Long?,
    exclude: Set<Long> = emptySet(),
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
) {
    val nodes = flattenTree(folders, exclude)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item { PickRow(null, "筆記（最上層）", 0, current == null) { onPick(null); onDismiss() } }
                items(nodes, key = { it.folder.id }) { n ->
                    PickRow(n.folder.id, n.folder.name, n.depth + 1, current == n.folder.id) { onPick(n.folder.id); onDismiss() }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PickRow(id: Long?, name: String, depth: Int, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !isCurrent, onClick = onClick)
            .padding(start = (depth * 16).dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (id == null) Icons.Default.Home else Icons.Default.Folder, null,
            tint = if (id == null) MaterialTheme.colorScheme.primary else Color(0xFFFFA000),
        )
        Text(
            name + if (isCurrent) "（目前位置）" else "",
            Modifier.padding(start = 12.dp),
            color = if (isCurrent) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun ConfirmDeleteDialog(title: String, body: String, onDismiss: () -> Unit, confirmLabel: String = "刪除", onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private val dateFmt = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
fun formatTime(ms: Long): String = dateFmt.format(Date(ms))
