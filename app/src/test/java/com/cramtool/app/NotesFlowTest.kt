package com.cramtool.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 筆記流程：建資料夾 → 在資料夾內新增筆記 → 閱讀 → 大綱跳轉 → 分享文字自動當作筆記 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class NotesFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        runCatching {
            rule.runOnUiThread {
                val v = rule.activity.window.decorView
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(Canvas(bmp))
                File(outDir, "notes_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    private fun waitText(text: String) {
        try {
            rule.waitUntil(15_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            shot("FAILED_$text")
            throw AssertionError("等不到畫面出現文字「$text」", e)
        }
    }

    private val longNote = buildString {
        appendLine("# 演算法筆記")
        for (c in 1..6) {
            appendLine("## 第${c}章 主題$c")
            for (p in 1..6) appendLine("- 第${c}章的重點 $p：**粗體** 與 `code`\n")
        }
        appendLine("### 最後的考前重點")
        appendLine("結尾段落-獨特文字")
    }

    @Test fun notesFlow() {
        rule.onNodeWithText("筆記").performClick()
        waitText("還沒有筆記")
        shot("01_empty")

        // 新增資料夾
        rule.onNodeWithContentDescription("新增資料夾").performClick()
        rule.onAllNodesWithText("資料夾名稱").onFirst().performTextInput("演算法")
        rule.onNodeWithText("建立").performClick()
        waitText("0 篇筆記")
        rule.onNodeWithText("演算法").performClick()
        waitText("這個資料夾是空的")

        // 在資料夾內新增筆記（模擬貼上）
        rule.runOnUiThread { SessionStore.pendingNote.value = longNote }
        rule.onNodeWithText("新增筆記").performClick()
        waitText("位置：演算法")
        shot("02_edit")
        rule.onNodeWithText("儲存").performClick()

        // 閱讀器
        waitText("第1章 主題1")
        shot("03_reader")
        rule.onNodeWithText("大綱").performClick()
        waitText("回到開頭")
        shot("04_outline")
        rule.onAllNodesWithText("最後的考前重點").onLast().performClick()
        waitText("結尾段落-獨特文字")
        rule.onNodeWithText("結尾段落-獨特文字").assertIsDisplayed()
        shot("05_jumped")

        // 回到資料夾，筆記出現在裡面
        rule.onNodeWithContentDescription("返回").performClick()
        waitText("演算法筆記")
        shot("06_folder")

        // 分享 Markdown 進來 → 自動開啟新增筆記（而非匯入題庫）
        rule.runOnUiThread { SessionStore.pendingImport.value = "# 分享來的筆記\n\n內容" }
        waitText("位置：筆記（最上層）")
        rule.onAllNodesWithText("分享來的筆記").onFirst().assertIsDisplayed()
    }
}
