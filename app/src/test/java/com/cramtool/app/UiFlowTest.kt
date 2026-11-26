package com.cramtool.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 端對端流程：匯入 → 選範圍 → 刷題 → 結果 → 錯題本。
 * 會在 app/build/screenshots/ 輸出截圖，方便人工檢查畫面。
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class UiFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitText(text: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun fullFlow() {
        shot("01_home_empty")

        // 模擬從其他 App 分享 JSON 進來
        val sample = listOf("../samples/sample_bank.json", "samples/sample_bank.json").map(::File).first { it.exists() }
        rule.runOnUiThread { SessionStore.pendingImport.value = sample.readText() }
        waitText("解析成功")
        shot("02_import_preview")

        rule.onNodeWithText("匯入 8 題").performScrollTo().performClick()
        waitText("章節範圍")
        shot("03_bank_config")

        // 題數選全部、依題號，方便預期第一題
        rule.onNodeWithText("全部", substring = false).performClick()
        rule.onNodeWithText("依題號").performClick()
        rule.onNodeWithText("開始刷題", substring = true).performClick()
        waitText("1 / 8")
        // 第 1 題：選錯（A. O(1)）
        rule.onNodeWithText("O(1)").performClick()
        shot("04_quiz_selected")
        rule.onNodeWithText("確認答案").performClick()
        waitText("答錯了")
        shot("05_quiz_wrong")
        rule.onNodeWithText("下一題").performClick()

        // 第 2 題：填空，答對
        waitText("2 / 8")
        rule.onAllNodesWithText("你的答案").onFirst().performTextInput("o(n log n)")
        rule.onNodeWithText("確認答案").performClick()
        waitText("答對了")
        shot("06_quiz_fill_correct")
        rule.onNodeWithText("下一題").performClick()

        // 第 3 題：是非，提前結束
        waitText("3 / 8")
        rule.onNodeWithText("正確 (O)").performClick()
        rule.onNodeWithText("確認答案").performClick()
        rule.onNodeWithContentDescription("結束").performClick()
        rule.onNodeWithText("結束", substring = false).performClick()
        waitText("練習結果")
        shot("07_result")

        // 去錯題本
        rule.onNodeWithContentDescription("關閉").performClick()
        rule.onNodeWithContentDescription("返回").performClick()
        waitText("我的題庫")
        shot("08_home_with_bank")
        rule.onNodeWithText("錯題本有", substring = true).performClick()
        waitText("複習這")
        shot("09_wrongbook")
    }
}
