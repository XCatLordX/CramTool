package com.cramtool.app

import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.cramtool.app.data.SOURCE_DOCX
import com.cramtool.app.data.SOURCE_PDF
import com.cramtool.app.data.SOURCE_PPTX
import com.cramtool.app.docs.DocumentImporter
import com.cramtool.app.docs.ImportException
import com.cramtool.app.testutil.Fixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 匯入 PDF／Word／PPT → 出現在筆記列表 → 閱讀；舊格式拒絕；刪除時移除檔案 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class DocumentFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CramApp

    private fun waitText(t: String) = rule.waitUntil(10_000) {
        rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun import(name: String, bytes: ByteArray) = runBlocking {
        val f = Fixtures.write(rule.activity.cacheDir, name, bytes)
        DocumentImporter.import(rule.activity, Uri.fromFile(f), null, app.notes)
    }

    @Test fun importAndRead() {
        val ppt = import("動物組織.pptx", Fixtures.pptx())
        val doc = import("細胞講義.docx", Fixtures.docx())
        val pdf = import("課本.pdf", Fixtures.pdf(3))
        assertEquals(SOURCE_PPTX, ppt.type); assertEquals(2, ppt.pages)
        assertEquals(SOURCE_DOCX, doc.type); assertEquals(SOURCE_PDF, pdf.type)

        // 舊格式：清楚的錯誤訊息
        val old = runCatching { import("舊講義.doc", "x".toByteArray()) }.exceptionOrNull()
        assertTrue(old is ImportException && old.message!!.contains(".docx"))

        rule.onNodeWithText("筆記").performClick()
        waitText("動物組織"); waitText("細胞講義"); waitText("課本")
        rule.onNodeWithText("PowerPoint", substring = true).assertExists()

        // PPT：一張投影片一頁、可滑動、有圖片與講者備註
        rule.onNodeWithText("動物組織").performClick()
        waitText("第 1 / 2 節")
        waitText("上皮組織")
        waitText("這張是考試重點")
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("圖片").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().performTouchInput { swipeLeft() }
        waitText("第 2 / 2 節")
        waitText("負回饋")
        rule.onNodeWithContentDescription("返回").performClick()

        // Word：標題、粗體、表格
        waitText("細胞講義")
        rule.onNodeWithText("細胞講義").performClick()
        waitText("生命的基本單位")
        rule.onNodeWithContentDescription("返回").performClick()

        // 刪除文件筆記時一併刪除原始檔與圖片
        val path = runBlocking { app.notes.getNote(ppt.noteId)!!.sourcePath!! }
        assertTrue(File(path).exists())
        runBlocking { app.notes.deleteNote(ppt.noteId) }
        assertFalse(File(path).exists())
        assertFalse(File(path + "_media").exists())
    }
}
