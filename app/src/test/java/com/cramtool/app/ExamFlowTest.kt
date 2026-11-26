package com.cramtool.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.cramtool.app.importer.QuestionParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 模擬考：設定 → 作答（含簡答）→ 標記 → 題號總覽 → 交卷 → 簡答自評 → 練錯題；統計頁顯示紀錄 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class ExamFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CramApp

    private fun shot(name: String) {
        rule.waitForIdle()
        runCatching {
            rule.runOnUiThread {
                val v = rule.activity.window.decorView
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(Canvas(bmp))
                File("build/screenshots").mkdirs()
                File("build/screenshots/exam_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    private val vlist = hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
    private fun has(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitText(t: String) {
        try { rule.waitUntil(10_000) { has(t) } } catch (e: Throwable) { shot("FAILED"); throw AssertionError("等不到「$t」", e) }
    }

    @Test fun examAndStats() {
        runBlocking {
            val sample = listOf("../samples/sample_bank.json", "samples/sample_bank.json").map(::File).first { it.exists() }
            app.repository.importQuestions("資料結構", "", QuestionParser.parse(sample.readText()).questions, null)
        }
        waitText("資料結構")
        rule.onNodeWithText("資料結構").performClick()
        waitText("章節範圍")
        rule.onNodeWithText("全部", substring = false).performClick() // 題數：全部 8 題
        rule.onNodeWithText("模擬考", substring = true).performClick()
        waitText("模擬考設定")
        rule.onNodeWithText("不限時").performClick()
        rule.onNodeWithText("開始考試").performClick()
        waitText("模擬考 1 / 8")
        shot("01_taking")

        // 逐題往下：遇到簡答題就作答，第 1 題標記稍後檢查
        rule.onNodeWithText("標記稍後檢查", substring = true).performClick()
        waitText("已標記")
        repeat(8) {
            if (has("兩個 Stack")) {
                rule.onAllNodesWithText("交卷後對照參考答案自評", substring = true).onFirst().performTextInput("用 inStack 和 outStack")
            }
            if (has("下一題")) rule.onNodeWithText("下一題").performClick()
        }

        // 題號總覽 → 交卷
        rule.onNodeWithContentDescription("題號總覽").performClick()
        waitText("已作答 1 · 未作答 7 · 標記 1")
        rule.onAllNodesWithText("交卷").onFirst().performClick()
        waitText("確定交卷")
        rule.onNodeWithText("還有 7 題未作答", substring = true).assertExists()
        // 對話框在最後一個視窗，取最後一個「交卷」按鈕
        val submits = rule.onAllNodesWithText("交卷")
        submits[submits.fetchSemanticsNodes().size - 1].performClick()

        // 成績：0 分（簡答待自評）
        waitText("模擬考成績")
        waitText("0 分")
        waitText("1 題簡答題待自評")
        shot("02_result")
        rule.onNode(vlist).performScrollToNode(hasText("我答對了"))
        rule.onNodeWithText("我答對了").performClick()
        waitText("13 分") // 1/8 = 12.5 → 13
        shot("03_graded")

        // 資料庫：成績已更新；8 筆作答紀錄
        rule.waitUntil(5_000) { runBlocking { app.repository.observeExams().first().firstOrNull()?.correct == 1 } }
        val exam = runBlocking { app.repository.observeExams().first().single() }
        assertEquals(8, exam.total); assertEquals(7, exam.unanswered)

        // 練習錯題 → 進入刷題
        rule.onNode(vlist).performScrollToNode(hasText("練習 7 題錯題"))
        rule.onNodeWithText("練習 7 題錯題").performClick()
        waitText("1 / 7")

        // 結束練習 → 題庫頁 → 返回首頁 → 統計分頁
        rule.onNodeWithContentDescription("結束").performClick()
        waitText("結束本次練習")
        rule.onNodeWithText("結束", substring = false).performClick()
        waitText("章節範圍")
        rule.onNodeWithContentDescription("返回").performClick()
        waitText("我的題庫")
        rule.onNodeWithText("統計").performClick()
        waitText("學習統計")
        waitText("共 1 次")
        shot("04_stats")
    }
}
