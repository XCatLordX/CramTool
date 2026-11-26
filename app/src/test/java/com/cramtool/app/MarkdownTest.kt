package com.cramtool.app

import com.cramtool.app.data.FolderEntity
import com.cramtool.app.data.NoteRepository
import com.cramtool.app.notes.Markdown
import com.cramtool.app.notes.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    private val sample = """
        # 資料結構

        ## 第一章 陣列
        陣列是**連續記憶體**。
        第二行

        - 優點：O(1) 存取
          延續的說明
        - 缺點
          1. 插入慢
        - [x] 已讀

        > 引用一
        > 引用二

        ```kotlin
        val a = 1
        # 這不是標題
        ```

        | 結構 | 存取 |
        |---|:---:|
        | Array | O(1) |
        | List | O(n) |

        $$
        T(n) = 2T(n/2) + n
        $$

        ---
        ### 考前重點
        設定標題
        ===
    """.trimIndent()

    @Test fun parsesBlocks() {
        val b = Markdown.parse(sample)
        assertEquals(MdBlock.Heading(1, "資料結構"), b[0])
        assertEquals(MdBlock.Heading(2, "第一章 陣列"), b[1])
        assertEquals(MdBlock.Paragraph("陣列是**連續記憶體**。\n第二行"), b[2])
        assertEquals(MdBlock.ListItem(0, null, "優點：O(1) 存取 延續的說明"), b[3])
        assertEquals(MdBlock.ListItem(1, "1.", "插入慢"), b[5])
        assertEquals(MdBlock.ListItem(0, null, "已讀", checked = true), b[6])
        assertEquals(MdBlock.Quote("引用一\n引用二"), b[7])
        assertEquals(MdBlock.Code("kotlin", "val a = 1\n# 這不是標題"), b[8])
        val table = b[9] as MdBlock.Table
        assertEquals(listOf("結構", "存取"), table.header)
        assertEquals(listOf(listOf("Array", "O(1)"), listOf("List", "O(n)")), table.rows)
        assertEquals(MdBlock.Math("T(n) = 2T(n/2) + n"), b[10])
        assertEquals(MdBlock.Rule, b[11])
        assertEquals(MdBlock.Heading(3, "考前重點"), b[12])
        assertEquals(MdBlock.Heading(1, "設定標題"), b[13])
    }

    @Test fun outlineSkipsCodeAndPointsToBlocks() {
        val b = Markdown.parse(sample)
        val o = Markdown.outline(b)
        assertEquals(listOf("資料結構", "第一章 陣列", "考前重點", "設定標題"), o.map { it.title })
        o.forEach { assertTrue(b[it.blockIndex] is MdBlock.Heading) }
    }

    @Test fun plainTextAndTitle() {
        assertEquals("重點 與 code 和 連結", Markdown.plainText("**重點** 與 `code` 和 [連結](http://x)"))
        assertEquals("資料結構", Markdown.guessTitle(sample))
        assertEquals("第一行", Markdown.guessTitle("\n第一行\n第二行"))
        assertEquals("未命名筆記", Markdown.guessTitle("   "))
    }

    @Test fun shareRouting() {
        assertTrue(SessionStore.looksLikeQuestionBank("""{"questions":[{"question":"q","answer":true}]}"""))
        assertTrue(SessionStore.looksLikeQuestionBank("```json\n{\"questions\":[{\"question\":\"壞掉的\"\n```"))
        assertFalse(SessionStore.looksLikeQuestionBank("# 筆記\n\n- 重點"))
    }

    @Test fun folderDescendants() {
        val all = listOf(
            FolderEntity(1, null, "A"), FolderEntity(2, 1, "B"), FolderEntity(3, 2, "C"), FolderEntity(4, null, "D"),
        )
        assertEquals(setOf(2L, 3L), NoteRepository.descendantsOf(1, all))
        assertEquals(emptySet<Long>(), NoteRepository.descendantsOf(4, all))
    }
}
