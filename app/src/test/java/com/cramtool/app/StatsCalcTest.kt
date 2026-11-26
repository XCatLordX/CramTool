package com.cramtool.app

import com.cramtool.app.data.ChapterStat
import com.cramtool.app.data.DailyCount
import com.cramtool.app.data.StatsCalc
import com.cramtool.app.data.Streak
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsCalcTest {
    private val today = LocalDate.of(2026, 11, 26)

    @Test fun lastDaysFillsGaps() {
        val d = StatsCalc.lastDays(listOf(DailyCount("2026-11-26", 10, 7), DailyCount("2026-11-20", 3, 3)), today, 14)
        assertEquals(14, d.size)
        assertEquals(LocalDate.of(2026, 11, 13), d.first().date)
        assertEquals(10, d.last().total)
        assertEquals(3, d.first { it.date == LocalDate.of(2026, 11, 20) }.total)
        assertEquals(0, d[1].total)
    }

    @Test fun streakCountsFromTodayOrYesterday() {
        assertEquals(Streak(3, 3), StatsCalc.streak(listOf("2026-11-26", "2026-11-25", "2026-11-24"), today))
        // 今天還沒刷：從昨天算，連續紀錄還在
        assertEquals(Streak(2, 2), StatsCalc.streak(listOf("2026-11-25", "2026-11-24"), today))
        // 中斷超過一天 → 0，但最長紀錄保留
        assertEquals(Streak(0, 4), StatsCalc.streak(listOf("2026-11-20", "2026-11-19", "2026-11-18", "2026-11-17"), today))
        assertEquals(Streak(0, 0), StatsCalc.streak(emptyList(), today))
    }

    @Test fun weakChaptersNeedEnoughAttemptsAndSortByAccuracy() {
        val s = listOf(
            ChapterStat(1, "B", "Ch1", attempts = 10, correct = 9, questions = 5),
            ChapterStat(1, "B", "Ch2", attempts = 10, correct = 3, questions = 5),
            ChapterStat(1, "B", "Ch3", attempts = 2, correct = 0, questions = 5), // 次數不夠
            ChapterStat(1, "B", "Ch4", attempts = 6, correct = 6, questions = 3), // 全對
            ChapterStat(1, "B", "Ch5", attempts = 4, correct = 2, questions = 2),
        )
        assertEquals(listOf("Ch2", "Ch5", "Ch1"), StatsCalc.weakChapters(s).map { it.chapter })
    }

    @Test fun niceMax() {
        assertEquals(5, StatsCalc.niceMax(0)); assertEquals(5, StatsCalc.niceMax(4))
        assertEquals(10, StatsCalc.niceMax(7)); assertEquals(50, StatsCalc.niceMax(31)); assertEquals(2000, StatsCalc.niceMax(1001))
    }
}
