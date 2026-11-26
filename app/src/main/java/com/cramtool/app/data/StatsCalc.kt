package com.cramtool.app.data

import java.time.LocalDate

data class DayBar(val date: LocalDate, val total: Int, val correct: Int) {
    val accuracy: Float get() = if (total == 0) 0f else correct.toFloat() / total
}

data class Streak(val current: Int, val best: Int)

/** 學習統計的純計算（可單元測試） */
object StatsCalc {
    /** 最近 [days] 天（含今天），沒有作答的日子補 0 */
    fun lastDays(daily: List<DailyCount>, today: LocalDate, days: Int = 14): List<DayBar> {
        val map = daily.associateBy { it.day }
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val c = map[d.toString()]
            DayBar(d, c?.total ?: 0, c?.correct ?: 0)
        }
    }

    /**
     * 連續學習天數：今天有作答就從今天算起；今天還沒作答則從昨天算起（連續紀錄還沒中斷）。
     * @param activeDays yyyy-MM-dd，任意順序
     */
    fun streak(activeDays: List<String>, today: LocalDate): Streak {
        val set = activeDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSortedSet()
        if (set.isEmpty()) return Streak(0, 0)
        var start = if (today in set) today else today.minusDays(1)
        var current = 0
        while (start in set) { current++; start = start.minusDays(1) }
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (d in set) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run); prev = d
        }
        return Streak(current, best)
    }

    /** 需要加強的章節：作答次數夠多、正確率最低的前幾名 */
    fun weakChapters(stats: List<ChapterStat>, minAttempts: Int = 3, limit: Int = 5): List<ChapterStat> =
        stats.filter { it.attempts >= minAttempts && it.accuracy < 1f }
            .sortedWith(compareBy<ChapterStat> { it.accuracy }.thenByDescending { it.attempts })
            .take(limit)

    /** 圖表 y 軸上限：取「好看」的整數（5、10、20、50…） */
    fun niceMax(v: Int): Int {
        if (v <= 5) return 5
        val steps = listOf(10, 20, 30, 50, 100, 150, 200, 300, 500, 1000)
        return steps.firstOrNull { it >= v } ?: ((v + 999) / 1000 * 1000)
    }
}
