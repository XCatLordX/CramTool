package com.cramtool.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.cramtool.app.importer.QuestionParser
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 長按多選、長按滑動範圍選取、拖曳到資料夾、批次移動與刪除 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class SelectionFlowTest {
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
                File("build/screenshots/select_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    private fun waitText(t: String) {
        try {
            rule.waitUntil(10_000) { rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) { shot("FAILED"); throw AssertionError("等不到「$t」", e) }
    }
    private fun waitGone(t: String) = rule.waitUntil(10_000) {
        rule.onAllNodesWithText(t, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    }

    /** 某段文字在清單（LazyColumn）座標中的中心點 */
    private fun centerInList(text: String): Offset {
        val list = rule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        val b = rule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        return Offset(list.width / 2, b.center.y - list.top)
    }

    /** 在清單上：長按 from，不放手拖到 to */
    private fun longPressDrag(from: String, to: String) {
        val a = centerInList(from); val b = centerInList(to)
        rule.onNode(hasScrollAction()).performTouchInput {
            down(a)
            advanceEventTime(800)
            val steps = 12
            for (i in 1..steps) moveTo(a + (b - a) * (i / steps.toFloat()))
            advanceEventTime(100)
            up()
        }
    }

    @Test fun notesMultiSelectDragAndBulkOps() {
        runBlocking {
            app.notes.createFolder("目的地", null)
            // 依更新時間排序（新→舊）：筆記A 最新，所以列表順序是 A、B、C、D
            for ((i, t) in listOf("筆記D", "筆記C", "筆記B", "筆記A").withIndex()) {
                app.notes.createNote(t, "# $t\n內容 $i", null)
                Thread.sleep(5)
            }
        }
        rule.onNodeWithText("筆記").performClick()
        waitText("筆記A")

        // 1. 長按 → 選取 1 項；點另一項 → 2 項；✕ 取消
        rule.onNodeWithText("筆記A").performTouchInput { longClick() }
        waitText("已選取 1 項")
        rule.onNodeWithText("筆記B").performClick()
        waitText("已選取 2 項")
        shot("01_two_selected")
        rule.onNodeWithContentDescription("取消選取").performClick()
        waitGone("已選取 2 項")

        // 2. 長按滑動：A → C 一次選 3 項
        longPressDrag("筆記A", "筆記C")
        waitText("已選取 3 項")
        shot("02_drag_select")

        // 3. 長按已選項目拖到「目的地」資料夾 → 3 項移入
        longPressDrag("筆記B", "目的地")
        waitText("3 篇筆記")
        waitGone("筆記A")
        shot("03_dropped")

        // 4. 剩下筆記D：長按 → 刪除
        rule.onNodeWithText("筆記D").performTouchInput { longClick() }
        waitText("已選取 1 項")
        rule.onNodeWithContentDescription("刪除所選").performClick()
        rule.onNodeWithText("刪除").performClick()
        waitGone("筆記D")

        // 5. 進資料夾 → 全選 → 移動到最上層
        rule.onNodeWithText("目的地").performClick()
        waitText("筆記A")
        rule.onNodeWithText("筆記A").performTouchInput { longClick() }
        waitText("已選取 1 項")
        rule.onNodeWithContentDescription("更多").performClick()
        rule.onNodeWithText("全選").performClick()
        waitText("已選取 3 項")
        rule.onNodeWithContentDescription("移動到").performClick()
        waitText("（目前位置）") // 等對話框完整顯示再點
        rule.waitForIdle()
        rule.onNodeWithText("筆記（最上層）", substring = true).performClick()
        waitText("這個資料夾是空的")
    }

    @Test fun banksMultiSelectDelete() {
        runBlocking {
            val qs = QuestionParser.parse("""[{"question":"q","answer":true}]""").questions
            app.repository.importQuestions("題庫一", "", qs, null)
            app.repository.importQuestions("題庫二", "", qs, null)
        }
        waitText("題庫一")
        rule.onNodeWithText("題庫一").performTouchInput { longClick() }
        waitText("已選取 1 項")
        rule.onNodeWithText("題庫二").performClick()
        waitText("已選取 2 項")
        shot("04_banks_selected")
        rule.onNodeWithContentDescription("刪除所選").performClick()
        rule.onNodeWithText("刪除").performClick()
        waitText("還沒有題庫")
    }
}
