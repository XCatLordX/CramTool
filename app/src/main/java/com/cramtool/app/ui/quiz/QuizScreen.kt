package com.cramtool.app.ui.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cramtool.app.QuizRequest
import com.cramtool.app.data.QuestionEntity
import com.cramtool.app.data.QuestionType
import com.cramtool.app.ui.EmptyState
import com.cramtool.app.ui.cramViewModel
import com.cramtool.app.ui.theme.CorrectGreen
import com.cramtool.app.ui.theme.WrongRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(onExit: () -> Unit, onRestart: (QuizRequest) -> Unit) {
    val vm = cramViewModel { QuizViewModel(it) }
    var confirmExit by remember { mutableStateOf(false) }

    BackHandler(enabled = !vm.finished && vm.questions.isNotEmpty()) { confirmExit = true }

    if (vm.finished) {
        ResultView(vm, onExit, onRestart)
        return
    }

    val q = vm.current
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { if (vm.questions.isEmpty()) onExit() else confirmExit = true }) { Icon(Icons.Default.Close, "結束") } },
                title = {
                    Text(if (q != null) "${vm.index + 1} / ${vm.questions.size}" else "", style = MaterialTheme.typography.titleMedium)
                },
                actions = {
                    if (q != null) {
                        IconButton(onClick = vm::toggleWrongBook) {
                            if (vm.inWrongBook[q.id] == true) Icon(Icons.Default.Bookmark, "移出錯題本", tint = WrongRed)
                            else Icon(Icons.Default.BookmarkBorder, "加入錯題本")
                        }
                        IconButton(onClick = vm::toggleStar) {
                            if (vm.starred[q.id] == true) Icon(Icons.Default.Star, "取消收藏", tint = Color(0xFFFFB300))
                            else Icon(Icons.Default.StarBorder, "收藏")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (q != null) {
                Surface(tonalElevation = 3.dp) {
                    BottomActions(vm, q, Modifier.padding(16.dp).imePadding())
                }
            }
        },
    ) { pad ->
        when {
            vm.loading -> {}
            q == null -> EmptyState("沒有題目", "請回上一頁重新選擇範圍。", Modifier.padding(pad))
            else -> {
                val scroll = rememberScrollState()
                LaunchedEffect(vm.index) { scroll.scrollTo(0) }
                Column(Modifier.fillMaxSize().padding(pad)) {
                    LinearProgressIndicator(
                        progress = { (vm.index + if (vm.revealed) 1 else 0).toFloat() / vm.questions.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(Modifier.verticalScroll(scroll).padding(16.dp)) {
                        Text(
                            "${q.chapter} · ${q.type.label}" + if (q.type == QuestionType.MULTIPLE) "（選出所有正確答案）" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(q.stem, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        QuestionBody(vm, q)
                        if (vm.revealed) {
                            Spacer(Modifier.height(16.dp))
                            Feedback(vm, q)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("結束本次練習？") },
            text = { Text("已作答的題目會保留紀錄。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    if (vm.records.isEmpty() && !vm.revealed) onExit() else vm.finishEarly()
                }) { Text("結束") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("繼續作答") } },
        )
    }
}

@Composable
private fun QuestionBody(vm: QuizViewModel, q: QuestionEntity) {
    when (q.type) {
        QuestionType.SINGLE, QuestionType.MULTIPLE, QuestionType.TRUE_FALSE -> {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                q.options.forEachIndexed { i, opt ->
                    OptionRow(
                        letter = if (q.type == QuestionType.TRUE_FALSE) null else ('A' + i).toString(),
                        text = opt,
                        selected = i in vm.selected,
                        revealed = vm.revealed,
                        isAnswer = i in q.correctIndices,
                        multi = q.type == QuestionType.MULTIPLE,
                        onClick = { vm.toggleOption(i) },
                    )
                }
            }
        }
        QuestionType.FILL -> OutlinedTextField(
            value = vm.input,
            onValueChange = vm::updateInput,
            label = { Text("你的答案") },
            singleLine = true,
            readOnly = vm.revealed,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (vm.canSubmit()) vm.submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        QuestionType.SHORT -> OutlinedTextField(
            value = vm.input,
            onValueChange = vm::updateInput,
            label = { Text("在這裡寫下你的答案（可留空，看答案後自評）") },
            readOnly = vm.revealed,
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OptionRow(
    letter: String?, text: String, selected: Boolean, revealed: Boolean,
    isAnswer: Boolean, multi: Boolean, onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (border, bg) = when {
        revealed && isAnswer -> CorrectGreen to CorrectGreen.copy(alpha = 0.12f)
        revealed && selected -> WrongRed to WrongRed.copy(alpha = 0.12f)
        selected -> scheme.primary to scheme.primaryContainer
        else -> scheme.outlineVariant to Color.Transparent
    }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth()
            .border(BorderStroke(if (selected || (revealed && isAnswer)) 2.dp else 1.dp, border), shape)
            .background(bg, shape)
            .clickable(enabled = !revealed, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp)
                .background(if (selected || (revealed && isAnswer)) border else scheme.surfaceVariant, if (multi) RoundedCornerShape(6.dp) else CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letter ?: if (text.startsWith("正確")) "O" else "X",
                color = if (selected || (revealed && isAnswer)) Color.White else scheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(text, Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (revealed && isAnswer) Text("✓", color = CorrectGreen, fontWeight = FontWeight.Bold)
        else if (revealed && selected) Text("✗", color = WrongRed, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Feedback(vm: QuizViewModel, q: QuestionEntity) {
    val c = vm.correct
    val color = when (c) { true -> CorrectGreen; false -> WrongRed; null -> MaterialTheme.colorScheme.primary }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                when (c) { true -> "答對了！"; false -> "答錯了"; null -> "參考答案" },
                color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
            if (q.type == QuestionType.SHORT || q.type == QuestionType.FILL || c == false) {
                AnswerText(q, Modifier.padding(top = 6.dp))
            }
            if (q.explanation.isNotBlank()) {
                Text("解析", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                Text(q.explanation, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun BottomActions(vm: QuizViewModel, q: QuestionEntity, modifier: Modifier) {
    val last = vm.index >= vm.questions.lastIndex
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!vm.revealed) {
            Button(onClick = vm::submit, enabled = vm.canSubmit(), modifier = Modifier.fillMaxWidth()) {
                Text(if (q.type == QuestionType.SHORT) "看答案" else "確認答案")
            }
            return@Column
        }
        // 簡答自評 / 填空改判
        if (q.type == QuestionType.SHORT || q.type == QuestionType.FILL) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.judge(false) }, modifier = Modifier.weight(1f),
                    colors = if (vm.correct == false) ButtonDefaults.outlinedButtonColors(containerColor = WrongRed.copy(alpha = .15f)) else ButtonDefaults.outlinedButtonColors(),
                ) { Text("我答錯了") }
                OutlinedButton(
                    onClick = { vm.judge(true) }, modifier = Modifier.weight(1f),
                    colors = if (vm.correct == true) ButtonDefaults.outlinedButtonColors(containerColor = CorrectGreen.copy(alpha = .15f)) else ButtonDefaults.outlinedButtonColors(),
                ) { Text("我答對了") }
            }
        }
        Button(onClick = vm::next, enabled = vm.correct != null, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.correct == null) "請先自評對錯" else if (last) "完成" else "下一題")
        }
    }
}

/** 顯示正確答案文字（瀏覽／錯題本共用） */
@Composable
fun AnswerText(q: QuestionEntity, modifier: Modifier = Modifier) {
    val text = when (q.type) {
        QuestionType.SINGLE, QuestionType.MULTIPLE ->
            q.correctIndices.sorted().joinToString("、") { "(${'A' + it}) ${q.options.getOrElse(it) { "" }}" }
        QuestionType.TRUE_FALSE -> q.correctIndices.firstOrNull()?.let { q.options.getOrNull(it) }.orEmpty()
        QuestionType.FILL -> q.answers.joinToString(" ／ ")
        QuestionType.SHORT -> q.answers.joinToString("\n")
    }
    Column(modifier) {
        Text("正確答案", style = MaterialTheme.typography.labelLarge)
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** 選項＋答案＋解析的完整展開 */
@Composable
fun AnswerReveal(q: QuestionEntity, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (q.type == QuestionType.SINGLE || q.type == QuestionType.MULTIPLE) {
            q.options.forEachIndexed { i, o ->
                val ok = i in q.correctIndices
                Text(
                    "(${'A' + i}) $o" + if (ok) "  ✓" else "",
                    color = if (ok) CorrectGreen else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (ok) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        AnswerText(q, Modifier.padding(top = 4.dp))
        if (q.explanation.isNotBlank()) {
            Text("解析", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            Text(q.explanation, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultView(vm: QuizViewModel, onExit: () -> Unit, onRestart: (QuizRequest) -> Unit) {
    val total = vm.records.size
    val right = vm.records.count { it.correct }
    val wrong = vm.records.filter { !it.correct }
    val req = vm.request
    BackHandler(onBack = onExit)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("練習結果") },
                navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.Default.Close, "關閉") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val pct = if (total == 0) 0 else right * 100 / total
                    Text("$pct%", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold,
                        color = if (pct >= 60) CorrectGreen else WrongRed)
                    Text("答對 $right / $total 題", style = MaterialTheme.typography.titleMedium)
                    if (vm.masteredCount > 0) {
                        Text("🎉 ${vm.masteredCount} 題已熟練，移出錯題本", style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                    if (total < vm.questions.size) {
                        Text("（提前結束，共 ${vm.questions.size} 題）", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (wrong.isNotEmpty()) {
                Button(onClick = {
                    onRestart(QuizRequest("重做錯題", wrong.map { it.question.id }.shuffled(), isReview = true))
                }, modifier = Modifier.fillMaxWidth()) { Text("重做這 ${wrong.size} 題錯題") }
            }
            if (req != null) {
                OutlinedButton(onClick = {
                    onRestart(req.copy(questionIds = req.questionIds.shuffled()))
                }, modifier = Modifier.fillMaxWidth()) { Text("同一批題目再刷一次") }
            }
            if (wrong.isNotEmpty()) {
                Text("本次錯題", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                wrong.forEach { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(r.question.chapter, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(r.question.stem, style = MaterialTheme.typography.bodyLarge)
                            AnswerReveal(r.question, Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
