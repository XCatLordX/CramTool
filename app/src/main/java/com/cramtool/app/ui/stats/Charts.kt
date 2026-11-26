package com.cramtool.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.cramtool.app.data.DayBar
import com.cramtool.app.data.ExamResultEntity
import com.cramtool.app.data.StatsCalc
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val MD = DateTimeFormatter.ofPattern("M/d")

/**
 * 圖表主色：固定值（不跟動態桌布色），已用 dataviz 驗證工具檢查過與背景的對比
 * 淺色 #3949AB / 深色 #7986CB
 */
@Composable
fun chartColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF7986CB) else Color(0xFF3949AB)

fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "今天"
    today.minusDays(1) -> "昨天"
    else -> d.format(MD)
}

/** 每日作答量長條圖（單一數列，不需圖例）。點長條可選取並在上方顯示明細 */
@Composable
fun DailyBarChart(days: List<DayBar>, today: LocalDate, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val bar = chartColor()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val label = MaterialTheme.typography.labelSmall.copy(color = ink)
    val measurer = rememberTextMeasurer()
    val max = StatsCalc.niceMax(days.maxOfOrNull { it.total } ?: 0)
    val summary = days.filter { it.total > 0 }.joinToString("；") { "${dayLabel(it.date, today)} ${it.total} 題" }
        .ifEmpty { "最近 ${days.size} 天沒有作答紀錄" }

    Canvas(
        modifier.fillMaxWidth().height(170.dp)
            .semantics { contentDescription = "近 ${days.size} 天每日作答題數：$summary" }
            .pointerInput(days) {
                detectTapGestures { p ->
                    val left = 32.dp.toPx()
                    val slot = (size.width - left) / days.size
                    val i = ((p.x - left) / slot).toInt()
                    if (i in days.indices) onSelect(i)
                }
            },
    ) {
        val left = 32.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val top = 8.dp.toPx()
        val plotH = bottom - top
        // 格線與 y 軸刻度（淡色、退到背景）
        listOf(0, max / 2, max).forEach { v ->
            val y = bottom - plotH * v / max
            drawLine(grid, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            drawLabel(measurer, "$v", Offset(0f, y), label, alignRight = left - 6.dp.toPx())
        }
        val slot = (size.width - left) / days.size
        val barW = (slot * 0.62f).coerceAtLeast(2f)
        days.forEachIndexed { i, d ->
            if (d.total == 0) return@forEachIndexed
            val h = plotH * d.total / max
            val x = left + slot * i + (slot - barW) / 2
            val r = 4.dp.toPx().coerceAtMost(barW / 2)
            // 只有頂端圓角，底部貼齊基線
            val path = Path().apply {
                addRoundRect(RoundRect(x, bottom - h, x + barW, bottom, topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r)))
            }
            drawPath(path, if (i == selected || selected < 0) bar else bar.copy(alpha = 0.45f))
        }
        // x 軸標籤：第一天、中間、最後一天
        listOf(0, days.size / 2, days.lastIndex).distinct().forEach { i ->
            val cx = left + slot * i + slot / 2
            drawLabel(measurer, dayLabel(days[i].date, today), Offset(cx, size.height - 16.dp.toPx()), label, center = true)
        }
    }
}

/** 模擬考分數趨勢（單一數列折線，0–100 分） */
@Composable
fun ScoreLineChart(exams: List<ExamResultEntity>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val line = chartColor()
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val label = MaterialTheme.typography.labelSmall.copy(color = ink)
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier.fillMaxWidth().height(150.dp)
            .semantics { contentDescription = "最近 ${exams.size} 次模擬考分數：" + exams.joinToString("、") { "${it.score} 分" } }
            .pointerInput(exams) {
                detectTapGestures { p ->
                    val left = 32.dp.toPx(); val right = size.width - 12.dp.toPx()
                    if (exams.size == 1) { onSelect(0); return@detectTapGestures }
                    val step = (right - left) / (exams.size - 1)
                    onSelect(((p.x - left) / step).let { Math.round(it) }.coerceIn(0, exams.lastIndex))
                }
            },
    ) {
        val left = 32.dp.toPx(); val right = size.width - 12.dp.toPx()
        val top = 10.dp.toPx(); val bottom = size.height - 10.dp.toPx()
        fun y(score: Int) = bottom - (bottom - top) * score / 100f
        listOf(0, 60, 100).forEach { v ->
            drawLine(grid, Offset(left, y(v)), Offset(right, y(v)), strokeWidth = 1.dp.toPx())
            drawLabel(measurer, "$v", Offset(0f, y(v)), label, alignRight = left - 6.dp.toPx())
        }
        val step = if (exams.size > 1) (right - left) / (exams.size - 1) else 0f
        val pts = exams.mapIndexed { i, e -> Offset(if (exams.size == 1) (left + right) / 2 else left + step * i, y(e.score)) }
        if (pts.size > 1) {
            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
        pts.forEachIndexed { i, p ->
            val r = if (i == selected) 6.dp.toPx() else 4.dp.toPx()
            drawCircle(surface, radius = r + 2.dp.toPx(), center = p) // 2px 底色外環
            drawCircle(line, radius = r, center = p)
        }
    }
}

private fun DrawScope.drawLabel(
    m: TextMeasurer, text: String, anchor: Offset, style: TextStyle,
    alignRight: Float? = null, center: Boolean = false,
) {
    val layout = m.measure(text, style)
    val x = when {
        alignRight != null -> alignRight - layout.size.width
        center -> anchor.x - layout.size.width / 2
        else -> anchor.x
    }
    val y = if (center) anchor.y else anchor.y - layout.size.height / 2
    drawText(layout, topLeft = Offset(x.coerceIn(0f, size.width - layout.size.width), y.coerceAtLeast(0f)))
}

