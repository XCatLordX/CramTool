package com.cramtool.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cramtool.app.QuizRequest
import com.cramtool.app.data.StatsCalc
import com.cramtool.app.ui.cramApp
import com.cramtool.app.ui.exam.formatClock
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

private const val DAYS = 14

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onPractice: (QuizRequest) -> Unit, bottomBar: @Composable () -> Unit = {}) {
    val repo = cramApp().repository
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val since = remember { today.minusDays(DAYS - 1L).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val daily by remember { repo.observeDaily(since) }.collectAsState(initial = emptyList())
    val activeDays by remember { repo.observeActiveDays() }.collectAsState(initial = emptyList())
    val chapters by remember { repo.observeChapterStats() }.collectAsState(initial = emptyList())
    val banks by remember { repo.observeBankStats() }.collectAsState(initial = emptyList())
    val exams by remember { repo.observeExams() }.collectAsState(initial = emptyList())
    val lessons by remember { repo.observeLessonProgress() }.collectAsState(initial = emptyList())

    val days = StatsCalc.lastDays(daily, today, DAYS)
    val streak = StatsCalc.streak(activeDays, today)
    val weak = StatsCalc.weakChapters(chapters)
    val todayBar = days.last()
    val week = days.takeLast(7)
    var selectedDay by rememberSaveable { mutableIntStateOf(DAYS - 1) }
    val examsAsc = exams.take(10).reversed()
    var selectedExam by rememberSaveable { mutableIntStateOf(-1) }

    Scaffold(topBar = { TopAppBar(title = { Text("學習統計") }) }, bottomBar = bottomBar) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- 數字總覽 ----
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("今日作答", "${todayBar.total}", if (todayBar.total > 0) "正確率 ${pct(todayBar.accuracy)}" else "今天還沒刷題", Modifier.weight(1f))
                    StatTile("連續天數", "${streak.current}", "最長 ${streak.best} 天", Modifier.weight(1f))
                    val wt = week.sumOf { it.total }; val wc = week.sumOf { it.correct }
                    StatTile("近 7 天", "$wt", if (wt > 0) "正確率 ${pct(wc.toFloat() / wt)}" else "—", Modifier.weight(1f))
                }
            }

            // ---- 每日作答 ----
            item {
                StatCard("近 $DAYS 天作答題數") {
                    if (days.all { it.total == 0 }) {
                        Empty("還沒有作答紀錄。開始刷題或模擬考後，這裡會顯示每天的作答量。")
                    } else {
                        val d = days[selectedDay.coerceIn(0, days.lastIndex)]
                        Text(
                            "${dayLabel(d.date, today)}：" + if (d.total == 0) "沒有作答" else "作答 ${d.total} 題，答對 ${d.correct} 題（${pct(d.accuracy)}）",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        )
                        DailyBarChart(days, today, selectedDay, onSelect = { selectedDay = it }, modifier = Modifier.padding(top = 8.dp))
                        Text("點長條可查看當天明細", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ---- 模擬考 ----
            item {
                StatCard("模擬考成績") {
                    if (exams.isEmpty()) {
                        Empty("還沒有模擬考紀錄。到題庫頁按「模擬考」開始一次限時測驗。")
                    } else {
                        val avg = exams.map { it.score }.average()
                        Text("共 ${exams.size} 次 · 平均 ${"%.0f".format(avg)} 分 · 最高 ${exams.maxOf { it.score }} 分",
                            style = MaterialTheme.typography.bodyMedium)
                        if (examsAsc.size >= 2) {
                            val sel = selectedExam.takeIf { it in examsAsc.indices } ?: examsAsc.lastIndex
                            val e = examsAsc[sel]
                            Text("${dateTime(e.finishedAt)}　${e.bankName}：${e.score} 分", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            ScoreLineChart(examsAsc, sel, onSelect = { selectedExam = it }, modifier = Modifier.padding(top = 4.dp))
                        }
                        exams.take(5).forEach { e ->
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Row {
                                Column(Modifier.weight(1f)) {
                                    Text(e.bankName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${dateTime(e.finishedAt)} · 答對 ${e.correct}/${e.total} · 用時 ${formatClock(e.durationSec)}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("${e.score} 分", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ---- 需要加強的章節 ----
            item {
                StatCard("需要加強的章節") {
                    if (weak.isEmpty()) {
                        Empty("作答次數還不夠（每章至少 3 次）或全部答對，暫時沒有弱點章節。")
                    } else weak.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(c.chapter, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${c.bankName} · 正確率 ${pct(c.accuracy)}（${c.correct}/${c.attempts} 次）",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LinearProgressIndicator(progress = { c.accuracy }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    val ids = repo.questionIdsIn(c.bankId, c.chapter).shuffled()
                                    if (ids.isNotEmpty()) onPractice(QuizRequest("${c.chapter}", ids))
                                }
                            }) { Text("練這章") }
                        }
                    }
                }
            }

            // ---- 題庫掌握度 ----
            item {
                StatCard("題庫掌握度") {
                    if (banks.isEmpty()) Empty("還沒有題庫。")
                    else banks.forEachIndexed { i, b ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text(b.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "已作答 ${b.answered}/${b.total} 題 · " + (if (b.attempts > 0) "正確率 ${pct(b.accuracy)} · " else "") + "錯題本 ${b.wrong} 題",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(
                            progress = { if (b.total == 0) 0f else b.answered.toFloat() / b.total },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }

            // ---- 筆記 ----
            item {
                val total = lessons.sumOf { it.pageCount }
                val done = lessons.sumOf { r -> r.completedPages.split('\n').count { it.isNotBlank() }.coerceAtMost(r.pageCount) }
                StatCard("筆記學習") {
                    if (total == 0) Empty("還沒有開啟過課程式筆記。")
                    else {
                        Text("${lessons.size} 篇課程筆記 · 已完成 $done / $total 節（${pct(done.toFloat() / total)}）", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            }
            item {
                Text(
                    "每日作答與連續天數從 v0.7.0 開始記錄；題庫正確率包含更早的作答。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatTile(title: String, value: String, sub: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp))
}

private fun pct(f: Float) = "${Math.round(f * 100)}%"
private val dtFmt = SimpleDateFormat("M/d HH:mm", Locale.getDefault())
private fun dateTime(ms: Long) = dtFmt.format(Date(ms))

