package com.cramtool.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cramtool.app.audio.Cue
import com.cramtool.app.data.BackupCodec
import com.cramtool.app.data.BackupFile
import com.cramtool.app.data.BackupSummary
import com.cramtool.app.data.SoundStyle
import com.cramtool.app.data.ThemeMode
import com.cramtool.app.importer.PromptTemplate
import com.cramtool.app.notes.NotePrompt
import com.cramtool.app.ui.cramApp
import com.cramtool.app.ui.mainToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = cramApp()
    val s = app.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val theme by s.themeMode.collectAsState()
    val dynamic by s.dynamicColor.collectAsState()
    val soundOn by s.soundEnabled.collectAsState()
    val style by s.soundStyle.collectAsState()
    val volume by s.soundVolume.collectAsState()
    val haptics by s.hapticsEnabled.collectAsState()
    val customOk by s.customCorrect.collectAsState()
    val customBad by s.customWrong.collectAsState()
    val mastery by app.repository.masteryThreshold.collectAsState()
    val fontScale by app.notes.fontScale.collectAsState()

    var dialog by remember { mutableStateOf<String?>(null) }
    var pickingFor by remember { mutableStateOf<Cue?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupFile?>(null) }
    var busy by remember { mutableStateOf(false) }

    // ---- 備份：存成 JSON 檔（使用者自選位置：下載、雲端硬碟…）
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = runCatching {
                val file = app.backup.export(appVersion(ctx).first)
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(BackupCodec.encode(file).toByteArray()) }
                }
                BackupSummary.of(file)
            }
            busy = false
            result.onSuccess { mainToast(ctx, "已備份：$it") }
                .onFailure { mainToast(ctx, "備份失敗：${it.message}", long = true) }
        }
    }
    // ---- 還原：讀檔 → 預覽 → 選合併或取代
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                }
                withContext(Dispatchers.Default) { BackupCodec.decode(text) }
            }
            result.onSuccess { pendingRestore = it }
                .onFailure { mainToast(ctx, it.message ?: "無法讀取備份檔", long = true) }
        }
    }
    fun doRestore(file: BackupFile, replace: Boolean) {
        pendingRestore = null
        busy = true
        scope.launch {
            val r = runCatching { app.backup.restore(file, replace) }
            busy = false
            r.onSuccess { mainToast(ctx, (if (replace) "已還原：" else "已合併：") + it) }
                .onFailure { mainToast(ctx, "還原失敗，資料未變更：${it.message}", long = true) }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cue = pickingFor ?: return@rememberLauncherForActivityResult
        pickingFor = null
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { app.sounds.importCustom(cue, uri) }
            result.onSuccess { path ->
                if (cue == Cue.CORRECT) s.setCustomCorrect(path) else s.setCustomWrong(path)
                app.sounds.previewFile(path)
                mainToast(ctx, "已套用自訂音效")
            }.onFailure {
                mainToast(ctx, "無法使用這個檔案：${it.message ?: "格式不支援"}", long = true)
            }
        }
    }
    app.sounds.onLoadFailed = { path ->
        // 檔案無法解碼（格式不支援）→ 恢復內建音效
        if (path == s.customCorrect.value) s.setCustomCorrect(null)
        if (path == s.customWrong.value) s.setCustomWrong(null)
        scope.launch { mainToast(ctx, "音效檔無法播放，已恢復內建音效", long = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            // ---------------- 外觀 ----------------
            item { Section("外觀") }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("顯示模式", style = MaterialTheme.typography.bodyLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        ThemeMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = theme == m,
                                onClick = { s.setThemeMode(m) },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            ) { Text(m.label) }
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchRow("依桌布配色", "使用 Android 12 以上的 Material You 動態色彩", dynamic) { s.setDynamicColor(it) }
                }
            }

            // ---------------- 音效與震動 ----------------
            item { Section("音效與震動") }
            item { SwitchRow("答題音效", "答對、答錯與完成練習時播放提示音", soundOn) { s.setSoundEnabled(it) } }
            item { SwitchRow("答題震動", "答對輕震一下、答錯震兩下", haptics) { s.setHapticsEnabled(it) } }
            if (soundOn) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = volume, onValueChange = { s.setSoundVolume(it) },
                            onValueChangeFinished = { app.sounds.preview(Cue.CORRECT) },
                            modifier = Modifier.padding(start = 12.dp).weight(1f),
                        )
                        Text("${(volume * 100).toInt()}%", Modifier.padding(start = 8.dp))
                    }
                }
                item { Text("內建音效風格", Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.labelLarge) }
                SoundStyle.entries.forEach { st ->
                    item(key = "style_${st.name}") {
                        Row(
                            Modifier.fillMaxWidth().selectable(style == st) { s.setSoundStyle(st); app.sounds.preview(Cue.CORRECT, st, useCustom = false) }
                                .padding(start = 4.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(style == st, onClick = null, modifier = Modifier.padding(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(st.label, style = MaterialTheme.typography.bodyLarge)
                                Text(st.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { app.sounds.preview(Cue.CORRECT, st, useCustom = false) }) { Text("答對") }
                            TextButton(onClick = { app.sounds.preview(Cue.WRONG, st, useCustom = false) }) { Text("答錯") }
                        }
                    }
                }
                item {
                    CustomSoundRow("答對音效", customOk, style,
                        onPreview = { app.sounds.preview(Cue.CORRECT) },
                        onPick = { pickingFor = Cue.CORRECT; picker.launch(arrayOf("audio/*")) },
                        onReset = { app.sounds.removeCustom(customOk); s.setCustomCorrect(null) })
                }
                item {
                    CustomSoundRow("答錯音效", customBad, style,
                        onPreview = { app.sounds.preview(Cue.WRONG) },
                        onPick = { pickingFor = Cue.WRONG; picker.launch(arrayOf("audio/*")) },
                        onReset = { app.sounds.removeCustom(customBad); s.setCustomWrong(null) })
                }
                item {
                    Text(
                        "自訂音效：可選 mp3、ogg、wav、m4a，建議 3 秒以內、1 MB 以下。",
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------------- 刷題與閱讀 ----------------
            item { Section("刷題與閱讀") }
            item {
                ListItem(
                    headlineContent = { Text("錯題熟練門檻") },
                    supportingContent = { Text("錯題連續答對 $mastery 次後自動移出錯題本") },
                    modifier = Modifier.clickable { dialog = "mastery" },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("閱讀字體大小") },
                    supportingContent = { Text("${(fontScale * 100).toInt()}%") },
                    modifier = Modifier.clickable { dialog = "font" },
                )
            }

            // ---------------- 資料 ----------------
            item { Section("資料") }
            item {
                ListItem(
                    headlineContent = { Text("匯出備份") },
                    supportingContent = { Text("題庫、作答紀錄、筆記與設定存成一個檔案，可存到雲端硬碟或下載資料夾") },
                    leadingContent = { Icon(Icons.Default.Backup, null) },
                    modifier = Modifier.clickable(enabled = !busy) {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
                        exporter.launch("CramTool-備份-$stamp.json")
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("從備份還原") },
                    supportingContent = { Text("選擇先前匯出的備份檔，可合併或取代現有資料") },
                    leadingContent = { Icon(Icons.Default.Restore, null) },
                    modifier = Modifier.clickable(enabled = !busy) { importer.launch(arrayOf("application/json", "text/plain", "*/*")) },
                )
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            item {
                ListItem(
                    headlineContent = { Text("清除所有作答紀錄") },
                    supportingContent = { Text("所有題庫的作答次數、錯題本會清空；題目、收藏與筆記保留") },
                    modifier = Modifier.clickable { dialog = "resetStats" },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("還原預設設定") },
                    supportingContent = { Text("外觀、音效與震動回到預設值") },
                    modifier = Modifier.clickable { dialog = "resetSettings" },
                )
            }

            // ---------------- 關於 ----------------
            item { Section("關於") }
            item {
                val (name, code) = remember { appVersion(ctx) }
                ListItem(
                    headlineContent = { Text("版本") },
                    supportingContent = { Text("CramTool $name（版本代碼 $code）") },
                    modifier = Modifier.clickable { copy(ctx, "CramTool $name ($code) · Android ${Build.VERSION.RELEASE} · ${Build.MODEL}"); toast(ctx, "已複製版本資訊") },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("複製出題提示詞（題庫 JSON）") },
                    supportingContent = { Text("貼給 LLM 產生可匯入的期中考題目") },
                    modifier = Modifier.clickable { copy(ctx, PromptTemplate.text); toast(ctx, "已複製出題提示詞") },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("複製課程式筆記提示詞") },
                    supportingContent = { Text("貼給 LLM 產生分章節、一節一頁的學習筆記") },
                    modifier = Modifier.clickable { copy(ctx, NotePrompt.lesson); toast(ctx, "已複製課程式筆記提示詞") },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("資料與隱私") },
                    supportingContent = { Text("題庫、筆記與作答紀錄只儲存在這支手機上，App 不會上傳任何資料。移除 App 會一併刪除資料。") },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("開放原始碼授權") },
                    modifier = Modifier.clickable { dialog = "licenses" },
                )
            }
        }
    }

    pendingRestore?.let { file ->
        var confirmReplace by remember(file) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(if (confirmReplace) "確定要取代全部資料？" else "從備份還原") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!confirmReplace) {
                        Text("備份時間：" + SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(file.exportedAt)) +
                            if (file.appVersion.isNotBlank()) "（v${file.appVersion}）" else "")
                        Text("內容：" + BackupSummary.of(file))
                        Text("・合併：加入現有資料，原有資料不變（重複還原會出現重複的題庫）。", style = MaterialTheme.typography.bodySmall)
                        Text("・取代：先清空目前所有題庫、筆記與作答紀錄，再完整還原，並套用備份中的設定。", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("目前 App 內的所有題庫、作答紀錄、資料夾與筆記會被刪除，換成備份內容。此動作無法復原。")
                    }
                }
            },
            confirmButton = {
                if (!confirmReplace) {
                    TextButton(onClick = { doRestore(file, replace = false) }) { Text("合併") }
                } else {
                    TextButton(onClick = { doRestore(file, replace = true) }) { Text("確定取代", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = {
                if (!confirmReplace) {
                    TextButton(onClick = { confirmReplace = true }) { Text("取代…", color = MaterialTheme.colorScheme.error) }
                } else {
                    TextButton(onClick = { pendingRestore = null }) { Text("取消") }
                }
            },
        )
    }

    when (dialog) {
        "mastery" -> ChoiceDialog("錯題熟練門檻", listOf(1, 2, 3, 5), mastery, { "$it 次" }, { dialog = null }) {
            app.repository.setMasteryThreshold(it)
        }
        "font" -> ChoiceDialog("閱讀字體大小", listOf(0.8f, 0.9f, 1f, 1.15f, 1.3f, 1.5f), fontScale, { "${(it * 100).toInt()}%" }, { dialog = null }) {
            app.notes.setFontScale(it)
        }
        "resetStats" -> ConfirmDialog("清除所有作答紀錄？", "此動作無法復原。", { dialog = null }) {
            scope.launch { app.repository.resetAllStats(); mainToast(ctx, "已清除作答紀錄") }
        }
        "resetSettings" -> ConfirmDialog("還原預設設定？", "自訂音效檔也會被移除。", { dialog = null }) {
            app.sounds.removeCustom(customOk); app.sounds.removeCustom(customBad)
            s.resetAll()
        }
        "licenses" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("開放原始碼授權") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Jetpack Compose、Material 3" to "Apache License 2.0 · Google",
                        "AndroidX Room、Navigation、Lifecycle" to "Apache License 2.0 · Google",
                        "Kotlin、kotlinx.coroutines、kotlinx.serialization" to "Apache License 2.0 · JetBrains",
                    ).forEach { (lib, lic) ->
                        Column {
                            Text(lib, style = MaterialTheme.typography.bodyLarge)
                            Text(lic, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("App 內建音效為程式即時合成的原創音效。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("關閉") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text(
            title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(desc) },
        trailingContent = { Switch(checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun CustomSoundRow(
    title: String,
    customPath: String?,
    style: SoundStyle,
    onPreview: () -> Unit,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(if (customPath != null) "自訂檔案" else "內建（${style.label}）") },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreview) { Icon(Icons.Default.PlayArrow, "試聽$title") }
                if (customPath != null) TextButton(onClick = onReset) { Text("還原") }
                else OutlinedButton(onClick = onPick) { Text("自訂") }
            }
        },
    )
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<T>, current: T, label: (T) -> String, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().selectable(o == current) { onPick(o); onDismiss() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(o == current, onClick = null, modifier = Modifier.padding(12.dp))
                        Text(label(o))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("確定", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun appVersion(ctx: Context): Pair<String, Long> = runCatching {
    val info = if (Build.VERSION.SDK_INT >= 33) ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
    else @Suppress("DEPRECATION") ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    (info.versionName ?: "?") to (if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong())
}.getOrDefault("?" to 0L)

private fun copy(ctx: Context, text: String) =
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("CramTool", text))

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
