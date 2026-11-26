package com.cramtool.app.ui.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.cramtool.app.SessionStore
import com.cramtool.app.notes.Lesson
import com.cramtool.app.notes.Markdown
import com.cramtool.app.notes.NotePrompt
import com.cramtool.app.ui.cramApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 新增（noteId = null）或編輯筆記。
 * 新增時可從檔案、剪貼簿匯入，或接收其他 App 分享進來的文字。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    noteId: Long?,
    initialFolderId: Long?,
    onDone: (savedNoteId: Long?) -> Unit,
) {
    val ctx = LocalContext.current
    val repo = cramApp().notes
    val scope = rememberCoroutineScope()
    val folders by remember(Unit) { repo.observeAllFolders() }.collectAsState(initial = emptyList())

    var title by rememberSaveable { mutableStateOf("") }
    var titleTouched by rememberSaveable { mutableStateOf(false) }
    var content by rememberSaveable { mutableStateOf("") }
    var folderId by rememberSaveable { mutableStateOf(initialFolderId) }
    var loaded by rememberSaveable { mutableStateOf(noteId == null) }
    var original by rememberSaveable { mutableStateOf("" to "") }
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    fun setContent(text: String) {
        content = text
        if (!titleTouched) title = Markdown.guessTitle(text)
    }
    /** 從外部（剪貼簿、檔案、分享）帶入：去掉 LLM 常加的外層 ```markdown 區塊 */
    fun importContent(text: String) = setContent(Lesson.stripOuterFence(text))
    fun clipboardText(): String? = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()

    LaunchedEffect(noteId) {
        if (noteId != null && !loaded) {
            repo.getNote(noteId)?.let {
                title = it.title; content = it.content; folderId = it.folderId
                titleTouched = true; original = it.title to it.content
            }
            loaded = true
        }
    }
    LaunchedEffect(Unit) {
        SessionStore.pendingNote.value?.let { importContent(it); SessionStore.pendingNote.value = null }
    }

    val dirty = (title to content) != original && content.isNotBlank()
    BackHandler(enabled = dirty) { confirmDiscard = true }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            if (text == null) Toast.makeText(ctx, "無法讀取檔案", Toast.LENGTH_SHORT).show() else importContent(text)
        }
    }

    fun save() = scope.launch {
        val t = title.ifBlank { Markdown.guessTitle(content) }
        val id = if (noteId == null) repo.createNote(t, content, folderId)
        else { repo.updateNoteContent(noteId, t, content); repo.moveNote(noteId, folderId); noteId }
        withContext(Dispatchers.Main) { onDone(id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (dirty) confirmDiscard = true else onDone(null) }) { Icon(Icons.Default.Close, "取消") }
                },
                title = { Text(if (noteId == null) "新增筆記" else "編輯筆記") },
                actions = {
                    TextButton(enabled = content.isNotBlank() && loaded, onClick = { save() }) { Text("儲存") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (noteId == null) {
                    AssistChip(
                        onClick = {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("prompt", NotePrompt.lesson))
                            Toast.makeText(ctx, "已複製課程式筆記提示詞，貼給 LLM 並附上講義", Toast.LENGTH_LONG).show()
                        },
                        label = { Text("複製課程式筆記提示詞") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                    )
                    AssistChip(
                        onClick = {
                            val clip = clipboardText()
                            if (clip.isNullOrBlank()) Toast.makeText(ctx, "剪貼簿是空的", Toast.LENGTH_SHORT).show() else importContent(clip)
                        },
                        label = { Text("從剪貼簿貼上") },
                        leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                    )
                    AssistChip(
                        onClick = { importContent(NotePrompt.sample) },
                        label = { Text("載入範例課程") },
                        leadingIcon = { Icon(Icons.Default.AutoStories, null) },
                    )
                }
                AssistChip(
                    onClick = {
                        val clip = clipboardText()
                        if (clip.isNullOrBlank()) Toast.makeText(ctx, "剪貼簿是空的", Toast.LENGTH_SHORT).show()
                        else {
                            setContent(Lesson.appendContinuation(content, clip))
                            Toast.makeText(ctx, "已接在最後", Toast.LENGTH_SHORT).show()
                        }
                    },
                    label = { Text("從剪貼簿附加到最後") },
                    leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) },
                )
                if (noteId == null) {
                    AssistChip(
                        onClick = { picker.launch(arrayOf("text/markdown", "text/plain", "text/*", "*/*")) },
                        label = { Text("選擇 .md / .txt 檔") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                    )
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; titleTouched = true },
                label = { Text("筆記名稱") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AssistChip(
                onClick = { picking = true },
                label = {
                    Text("位置：" + (folderPath(folderId, folders).joinToString(" / ") { it.name }.ifEmpty { "筆記（最上層）" }))
                },
                leadingIcon = { Icon(Icons.Default.Folder, null) },
            )
            OutlinedTextField(
                value = content,
                onValueChange = { setContent(it) },
                label = { Text("Markdown 內容") },
                placeholder = { Text("# 標題\n\n## 章節一\n- 重點…") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 8.dp),
            )
        }
    }

    if (picking) MoveToDialog("存放位置", folders, folderId, onDismiss = { picking = false }) { folderId = it }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放棄未儲存的內容？") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDone(null) }) { Text("放棄") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("繼續編輯") } },
        )
    }
}
