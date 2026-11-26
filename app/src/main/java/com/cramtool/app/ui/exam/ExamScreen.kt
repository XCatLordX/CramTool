package com.cramtool.app.ui.exam

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cramtool.app.QuizRequest
import com.cramtool.app.data.QuestionEntity
import com.cramtool.app.data.QuestionType
import com.cramtool.app.ui.EmptyState
import com.cramtool.app.ui.bottomBarInsets
import com.cramtool.app.ui.cramViewModel
import com.cramtool.app.ui.quiz.AnswerReveal
import com.cramtool.app.ui.theme.CorrectGreen
import com.cramtool.app.ui.theme.WrongRed

@Composable
fun ExamScreen(onExit: () -> Unit, onPractice: (QuizRequest) -> Unit) {
    val vm = cramViewModel { ExamViewModel(it) }
    if (vm.finished) ExamResultView(vm, onExit, onPractice) else ExamTakingView(vm, onExit)
}

// ===================== 作答中 =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamTakingView(vm: ExamViewModel, onExit: () -> Unit) {
    var confirmExit by remember { mutableStateOf(false) }
    var confirmSubmit by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    BackHandler(enabled = vm.questions.isNotEmpty()) { confirmExit = true }

    val q = vm.current
    val limit = vm.request?.timeLimitSec ?: 0
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { if (vm.questions.isEmpty()) onExit() else confirmExit = true }) { Icon(Icons.Default.Close, "放棄考試") } },
                title = { Text(if (q != null) "模擬考 ${vm.index + 1} / ${vm.questions.size}" else "模擬考", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    if (limit > 0) {
                        val urgent = vm.remainingSec <= 60
                        Row(
                            Modifier.background(if (urgent) WrongRed.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Timer, "剩餘時間", Modifier.size(16.dp), tint = if (urgent) WrongRed else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(" " + formatClock(vm.remainingSec), color = if (urgent) WrongRed else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = { showGrid = true }) { Icon(Icons.Default.Apps, "題號總覽") }
                    TextButton(onClick = { confirmSubmit = true }) { Text("交卷") }
                },
            )
        },
        bottomBar = {
            if (q != null) Surface(tonalElevation = 3.dp) {
                Row(Modifier.bottomBarInsets().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { vm.goTo(vm.index - 1) }, enabled = vm.index > 0, modifier = Modifier.weight(1f)) { Text("上一題") }
                    if (vm.index < vm.questions.lastIndex) Button(onClick = { vm.goTo(vm.index + 1) }, modifier = Modifier.weight(1f)) { Text("下一題") }
                    else Button(onClick = { confirmSubmit = true }, modifier = Modifier.weight(1f)) { Text("交卷") }
                }
            }
        },
    ) { pad ->
        when {
            vm.loading -> {}
            q == null -> EmptyState("沒有題目", "請回上一頁重新選擇範圍。", Modifier.padding(pad))
            else -> Column(Modifier.fillMaxSize().padding(pad)) {
                LinearProgressIndicator(
                    progress = { vm.answeredCount.toFloat() / vm.questions.size },
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${q.chapter} · ${q.type.label}" + if (q.type == QuestionType.MULTIPLE) "（複選）" else "",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        val f = vm.flagged[q.id] == true
                        TextButton(onClick = { vm.toggleFlag(q) }) {
                            Icon(if (f) Icons.Default.Flag else Icons.Outlined.Flag, null, tint = if (f) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (f) " 已標記" else " 標記稍後檢查")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(q.stem, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    ExamAnswerInput(vm, q)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showGrid) QuestionGrid(vm, onPick = { vm.goTo(it); showGrid = false }, onSubmit = { showGrid = false; confirmSubmit = true }, onDismiss = { showGrid = false })
    if (confirmSubmit) {
        val unanswered = vm.questions.size - vm.answeredCount
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text("確定交卷？") },
            text = {
                Text(buildString {
                    append("已作答 ${vm.answeredCount} / ${vm.questions.size} 題。")
                    if (unanswered > 0) append("\n還有 $unanswered 題未作答，交卷後會算答錯。")
                    if (vm.flaggedCount > 0) append("\n有 ${vm.flaggedCount} 題標記為稍後檢查。")
                })
            },
            confirmButton = { TextButton(onClick = { confirmSubmit = false; vm.submit() }) { Text("交卷") } },
            dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text("繼續作答") } },
        )
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("放棄這次模擬考？") },
            text = { Text("作答內容不會保存，也不會留下成績。") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onExit() }) { Text("放棄", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("繼續作答") } },
        )
    }
}

@Composable
private fun ExamAnswerInput(vm: ExamViewModel, q: QuestionEntity) {
    when (q.type) {
        QuestionType.FILL -> OutlinedTextField(
            vm.texts[q.id].orEmpty(), { vm.setText(q, it) }, label = { Text("你的答案") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        QuestionType.SHORT -> OutlinedTextField(
            vm.texts[q.id].orEmpty(), { vm.setText(q, it) }, label = { Text("你的答案（交卷後對照參考答案自評）") }, minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val order = vm.optionOrder[q.id] ?: q.options.indices.toList()
            val sel = vm.selected[q.id].orEmpty()
            order.forEachIndexed { display, original ->
                val picked = original in sel
                val scheme = MaterialTheme.colorScheme
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth()
                        .border(BorderStroke(if (picked) 2.dp else 1.dp, if (picked) scheme.primary else scheme.outlineVariant), shape)
                        .background(if (picked) scheme.primaryContainer else Color.Transparent, shape)
                        .clickable { vm.toggleOption(q, original) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).background(if (picked) scheme.primary else scheme.surfaceVariant,
                            if (q.type == QuestionType.MULTIPLE) RoundedCornerShape(6.dp) else CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (q.type == QuestionType.TRUE_FALSE) (if (original == 0) "O" else "X") else ('A' + display).toString(),
                            color = if (picked) scheme.onPrimary else scheme.onSurfaceVariant, fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(q.options[original], Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuestionGrid(vm: ExamViewModel, onPick: (Int) -> Unit, onSubmit: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Text("題號總覽", style = MaterialTheme.typography.titleLarge)
            Text(
                "已作答 ${vm.answeredCount} · 未作答 ${vm.questions.size - vm.answeredCount} · 標記 ${vm.flaggedCount}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.questions.forEachIndexed { i, q ->
                    val answered = vm.isAnswered(q)
                    val flag = vm.flagged[q.id] == true
                    val scheme = MaterialTheme.colorScheme
                    Box(
                        Modifier.size(44.dp)
                            .background(if (answered) scheme.primaryContainer else Color.Transparent, CircleShape)
                            .border(
                                if (i == vm.index) 3.dp else 1.dp,
                                when { flag -> Color(0xFFE65100); i == vm.index -> scheme.primary; else -> scheme.outlineVariant },
                                CircleShape,
                            )
                            .clickable { onPick(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", fontWeight = if (i == vm.index) FontWeight.Bold else FontWeight.Normal)
                        if (flag) Icon(Icons.Default.Flag, "已標記", Modifier.size(12.dp).align(Alignment.TopEnd), tint = Color(0xFFE65100))
                    }
                }
            }
            Text(
                "實心＝已作答，空心＝未作答，橘框＝標記稍後檢查",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) { Text("交卷") }
        }
    }
}

// ===================== 成績 =====================

private enum class ReviewFilter(val label: String) { ALL("全部"), WRONG("答錯"), UNANSWERED("未作答"), FLAGGED("已標記") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamResultView(vm: ExamViewModel, onExit: () -> Unit, onPractice: (QuizRequest) -> Unit) {
    BackHandler(onBack = onExit)
    var filter by rememberSaveable { mutableStateOf(ReviewFilter.ALL) }
    val items = vm.items
    val wrongIds = items.filter { vm.correctOf(it) == false }.map { it.q.id }
    val shown = items.withIndex().filter { (_, it) ->
        when (filter) {
            ReviewFilter.ALL -> true
            ReviewFilter.WRONG -> vm.correctOf(it) == false
            ReviewFilter.UNANSWERED -> !it.answered
            ReviewFilter.FLAGGED -> it.flagged
        }
    }
    val byChapter = items.groupBy { it.q.chapter }.map { (ch, list) -> Triple(ch, list.count { vm.correctOf(it) == true }, list.size) }
    val limit = vm.request?.timeLimitSec ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模擬考成績") },
                navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.Default.Close, "關閉") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (vm.autoSubmitted) Text("⏰ 時間到，已自動交卷", style = MaterialTheme.typography.labelLarge, color = WrongRed)
                        Text("${vm.score} 分", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold,
                            color = if (vm.score >= 60) CorrectGreen else WrongRed)
                        Text("答對 ${vm.correctCount} / ${items.size} 題", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "用時 ${formatClock(vm.elapsedSec)}" + (if (limit > 0) " / 限時 ${formatClock(limit)}" else "") +
                                " · 未作答 ${items.count { !it.answered }}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (vm.pendingShort.isNotEmpty()) {
                            Text("還有 ${vm.pendingShort.size} 題簡答題待自評（評完分數會更新）", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (wrongIds.isNotEmpty()) Button(
                        onClick = { onPractice(QuizRequest("模擬考錯題", wrongIds.shuffled(), isReview = true)) },
                        modifier = Modifier.weight(1f),
                    ) { Text("練習 ${wrongIds.size} 題錯題") }
                    OutlinedButton(onClick = onExit, modifier = Modifier.weight(1f)) { Text("完成") }
                }
            }
            if (byChapter.size > 1) {
                item { Text("各章節表現", style = MaterialTheme.typography.titleMedium) }
                items(byChapter, key = { "ch_" + it.first }) { (ch, ok, total) ->
                    Column {
                        Row {
                            Text(ch, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$ok / $total", style = MaterialTheme.typography.bodyMedium)
                        }
                        LinearProgressIndicator(progress = { ok.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            }
            if (vm.pendingShort.isNotEmpty() || items.any { it.correct == null }) {
                item { Text("簡答題自評", style = MaterialTheme.typography.titleMedium) }
                items(items.filter { it.correct == null }, key = { "short_" + it.q.id }) { it ->
                    ShortGradeCard(it.q, vm.texts[it.q.id].orEmpty(), vm.selfGrades[it.q.id]) { ok -> vm.gradeShort(it.q, ok) }
                }
            }
            item {
                Text("逐題檢討", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewFilter.entries.forEach { f ->
                        val n = items.count {
                            when (f) {
                                ReviewFilter.ALL -> true
                                ReviewFilter.WRONG -> vm.correctOf(it) == false
                                ReviewFilter.UNANSWERED -> !it.answered
                                ReviewFilter.FLAGGED -> it.flagged
                            }
                        }
                        FilterChip(filter == f, onClick = { filter = f }, label = { Text("${f.label} $n") })
                    }
                }
            }
            items(shown, key = { "r_" + it.value.q.id }) { (i, item) -> ReviewCard(i + 1, item, vm) }
        }
    }
}

@Composable
private fun ShortGradeCard(q: QuestionEntity, yours: String, grade: Boolean?, onGrade: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(q.stem, style = MaterialTheme.typography.bodyLarge)
            Text("你的答案：" + yours.ifBlank { "（未作答）" }, style = MaterialTheme.typography.bodyMedium)
            Text("參考答案：" + q.answers.joinToString("\n"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onGrade(false) }, modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = if (grade == false) WrongRed.copy(alpha = .15f) else Color.Transparent),
                ) { Text("我答錯了") }
                OutlinedButton(
                    onClick = { onGrade(true) }, modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = if (grade == true) CorrectGreen.copy(alpha = .15f) else Color.Transparent),
                ) { Text("我答對了") }
            }
        }
    }
}

@Composable
private fun ReviewCard(number: Int, item: ExamItem, vm: ExamViewModel) {
    val q = item.q
    val c = vm.correctOf(item)
    val mark = when { !item.answered -> "未作答"; c == true -> "✓ 答對"; c == false -> "✗ 答錯"; else -> "待自評" }
    val color = when (c) { true -> CorrectGreen; false -> WrongRed; null -> MaterialTheme.colorScheme.primary }
    Card(
        Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, color.copy(alpha = .4f)),
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("第 $number 題 · ${q.chapter}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (item.flagged) Icon(Icons.Default.Flag, "已標記", Modifier.size(16.dp), tint = Color(0xFFE65100))
                Spacer(Modifier.width(6.dp))
                Text(mark, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
            Text(q.stem, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
            Text("你的答案：" + userAnswer(q, vm), style = MaterialTheme.typography.bodyMedium, color = color)
            AnswerReveal(q, Modifier.padding(top = 6.dp))
        }
    }
}

private fun userAnswer(q: QuestionEntity, vm: ExamViewModel): String = when (q.type) {
    QuestionType.FILL, QuestionType.SHORT -> vm.texts[q.id].orEmpty().ifBlank { "（未作答）" }
    QuestionType.TRUE_FALSE -> vm.selected[q.id]?.firstOrNull()?.let { q.options.getOrNull(it) } ?: "（未作答）"
    else -> vm.selected[q.id].orEmpty().sorted().joinToString("、") { "(${'A' + it}) ${q.options.getOrElse(it) { "" }}" }.ifBlank { "（未作答）" }
}
