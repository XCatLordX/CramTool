package com.cramtool.app.ui.importer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.cramtool.app.SessionStore
import com.cramtool.app.data.BankEntity
import com.cramtool.app.importer.ParseException
import com.cramtool.app.importer.ParseResult
import com.cramtool.app.importer.PromptTemplate
import com.cramtool.app.importer.QuestionParser
import com.cramtool.app.ui.cramApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(onBack: () -> Unit, onImported: (Long) -> Unit) {
    val ctx = LocalContext.current
    val repo = cramApp().repository
    val scope = rememberCoroutineScope()

    var raw by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ParseResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var bankName by remember { mutableStateOf("") }
    var targetBank by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    val banks by produceState(emptyList<BankEntity>()) { value = repo.getBanks() }

    fun runParse(text: String) {
        raw = text
        try {
            val r = QuestionParser.parse(text)
            result = r
            error = null
            if (bankName.isBlank()) bankName = r.bankName ?: "新題庫"
        } catch (e: ParseException) {
            result = null
            error = e.message
        }
    }

    val pending by SessionStore.pendingImport.collectAsState()
    LaunchedEffect(pending) {
        pending?.let {
            runParse(it)
            SessionStore.pendingImport.value = null
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            if (text == null) error = "無法讀取檔案" else runParse(text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("匯入題庫") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("第 1 步：讓 LLM 出題", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "複製提示詞，貼到 ChatGPT / Claude / Gemini，並附上講義內容。完成後把它輸出的 JSON 複製回來，或存成 .json 檔。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    OutlinedButton(onClick = {
                        copy(ctx, PromptTemplate.text)
                        Toast.makeText(ctx, "已複製提示詞", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, null)
                        Text("  複製出題提示詞")
                    }
                }
            }

            Text("第 2 步：匯入 JSON", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Icon(Icons.Default.FolderOpen, null)
                    Text("  選擇檔案")
                }
                OutlinedButton(onClick = {
                    val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                    if (clip.isNullOrBlank()) Toast.makeText(ctx, "剪貼簿是空的", Toast.LENGTH_SHORT).show()
                    else runParse(clip)
                }) {
                    Icon(Icons.Default.ContentPaste, null)
                    Text("  從剪貼簿貼上")
                }
            }
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it; result = null; error = null },
                label = { Text("或直接在這裡貼上 JSON") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp),
            )
            if (result == null && raw.isNotBlank()) {
                Button(onClick = { runParse(raw) }) { Text("解析") }
            }

            error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            result?.let { r ->
                Preview(r)
                Text("第 3 步：匯入到", style = MaterialTheme.typography.titleSmall)
                TargetOption("建立新題庫", targetBank == null) { targetBank = null }
                if (targetBank == null) {
                    OutlinedTextField(
                        value = bankName, onValueChange = { bankName = it },
                        label = { Text("題庫名稱") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                banks.forEach { b ->
                    TargetOption("加入「${b.name}」", targetBank == b.id) { targetBank = b.id }
                }
                Button(
                    enabled = !busy && (targetBank != null || bankName.isNotBlank()),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        scope.launch {
                            val id = repo.importQuestions(bankName.trim(), r.description, r.questions, targetBank)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(ctx, "已匯入 ${r.questions.size} 題", Toast.LENGTH_SHORT).show()
                                onImported(id)
                            }
                        }
                    },
                ) { Text("匯入 ${r.questions.size} 題") }
            }
        }
    }
}

@Composable
private fun Preview(r: ParseResult) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("解析成功：${r.questions.size} 題", style = MaterialTheme.typography.titleSmall)
            val byType = r.questions.groupingBy { it.type.label }.eachCount()
            Text("題型：" + byType.entries.joinToString("、") { "${it.key} ${it.value}" }, style = MaterialTheme.typography.bodySmall)
            val byChapter = r.questions.groupingBy { it.chapter }.eachCount()
            Text("章節：", style = MaterialTheme.typography.bodySmall)
            byChapter.forEach { (c, n) -> Text("  • $c（$n 題）", style = MaterialTheme.typography.bodySmall) }
            if (r.warnings.isNotEmpty()) {
                Text(
                    "略過 ${r.warnings.size} 題：\n" + r.warnings.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TargetOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = onClick)
        Text(label)
    }
}

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("prompt", text))
}
