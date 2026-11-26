package com.cramtool.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 課程式筆記：載入範例 → 分頁閱讀 → 下一節 → 目錄跳轉 → 完成 → 列表顯示進度 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class LessonFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        runCatching {
            rule.runOnUiThread {
                val v = rule.activity.window.decorView
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(Canvas(bmp))
                File(outDir, "lesson_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    private fun waitText(text: String) {
        try {
            rule.waitUntil(15_000) {
                rule.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            shot("FAILED_$text")
            throw AssertionError("等不到畫面出現文字「$text」", e)
        }
    }

    /** 捲到頁面底部按「下一節」 */
    private fun clickNext() {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("下一節", substring = true))
        rule.onNodeWithText("下一節", useUnmergedTree = true).performClick()
    }

    @Test fun lessonFlow() {
        rule.onNodeWithText("筆記").performClick()
        waitText("還沒有筆記")
        rule.onNodeWithText("新增筆記", useUnmergedTree = true).performClick()
        waitText("載入範例課程")
        rule.onNodeWithText("載入範例課程").performScrollTo().performClick()
        waitText("資料結構入門（範例課程）")
        shot("01_edit_sample")
        rule.onNodeWithText("儲存").performClick()

        // 第 1 頁：導讀
        waitText("第 1 / 7 節")
        shot("02_page1")
        clickNext()
        waitText("第 2 / 7 節")
        clickNext()
        waitText("第 3 / 7 節")
        waitText("4.1 堆疊（Stack）")
        shot("03_section_4_1")

        // 全螢幕：工具列消失；點一下內文叫回來
        rule.onNodeWithContentDescription("全螢幕").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("目錄").fetchSemanticsNodes().isEmpty() }
        shot("03b_fullscreen")
        rule.onAllNodesWithText("後進先出", substring = true).onFirst().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("目錄").fetchSemanticsNodes().isNotEmpty() }

        // 目錄：已完成 2 節，跳到最後一節
        rule.onNodeWithContentDescription("目錄").performClick()
        waitText("已完成 2 / 7 節")
        rule.onNodeWithText("5.1 二元樹").performClick()
        waitText("第 7 / 7 節")
        shot("04_last")

        // 最後一頁按「完成課程」
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("完成課程", substring = true))
        rule.onNodeWithText("完成課程", substring = true, useUnmergedTree = true).performClick()
        waitText("完成課程！")
        rule.onNodeWithText("回到筆記列表").performClick()

        // 列表顯示學習進度（導讀、第 4 章導讀、5.1 共 3 節完成）
        waitText("學習進度 3/7 節")
        shot("05_list")
    }
}
