package com.cramtool.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 筆記流程：新增筆記 → 閱讀 → 大綱跳轉 → 移動到資料夾 → 分享文字自動當作筆記 */
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
            rule.waitUntil(15_000) { rule.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
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

        // 建資料夾（Robolectric 無法在 Dialog 內輸入文字，這裡直接透過 repository 建立）
        val app = rule.activity.application as CramApp
        runBlocking { app.notes.createFolder("演算法", null) }
        waitText("0 篇筆記")

        // 在最上層新增筆記（模擬從剪貼簿貼上）
        rule.runOnUiThread { SessionStore.pendingNote.value = longNote }
        rule.onNodeWithText("新增", useUnmergedTree = true).performClick()
        rule.onNodeWithText("新增筆記（貼上 Markdown）").performClick()
        waitText("位置：筆記（最上層）")
        rule.onAllNodesWithText("演算法筆記").onFirst().assertIsDisplayed() // 自動帶入標題
        shot("02_edit")
        rule.onNodeWithText("儲存").performClick()

        // 有多個章節的筆記預設是分頁閱讀；用選單切換成捲動閱讀，測試大綱跳轉
        waitText("第 1 / ")
        rule.onNodeWithContentDescription("更多").performClick()
        rule.onNodeWithText("切換為捲動閱讀").performClick()
        waitText("大綱")
        shot("03_reader")
        rule.onNodeWithText("大綱", useUnmergedTree = true).performClick()
        waitText("回到開頭")
        shot("04_outline")
        rule.onAllNodesWithText("最後的考前重點").onLast().performClick()
        waitText("結尾段落-獨特文字")
        rule.onNodeWithText("結尾段落-獨特文字").assertIsDisplayed()
        // 標題應被捲到頂端，且頂部列顯示目前章節 → 畫面上同時有兩個「最後的考前重點」
        rule.waitUntil(5_000) { rule.onAllNodesWithText("最後的考前重點").fetchSemanticsNodes().size == 2 }
        shot("05_jumped")

        // 回到列表，用「移動到…」把筆記放進資料夾
        rule.onNodeWithContentDescription("返回").performClick()
        waitText("演算法筆記")
        shot("06_root")
        rule.onAllNodesWithContentDescription("更多選項")[1].performClick() // [0] 是資料夾，[1] 是筆記
        rule.onNodeWithText("移動到…").performClick()
        waitText("目前位置")
        shot("07_move_dialog")
        rule.onAllNodesWithText("演算法").onLast().performClick()
        waitText("1 篇筆記")
        rule.onNodeWithText("演算法").performClick()
        waitText("演算法筆記")
        shot("08_in_folder")

        // 分享 Markdown 進來 → 自動開啟新增筆記（而非匯入題庫）
        rule.runOnUiThread { SessionStore.pendingImport.value = "# 分享來的筆記\n\n內容" }
        waitText("位置：筆記（最上層）")
        rule.onAllNodesWithText("分享來的筆記").onFirst().assertIsDisplayed()
    }
}
