package com.cramtool.app

import com.cramtool.app.notes.Lesson
import com.cramtool.app.notes.Markdown
import com.cramtool.app.notes.MdBlock
import com.cramtool.app.notes.NotePrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonTest {
    private fun pages(md: String) = Lesson.paginate(Markdown.parse(md))

    @Test fun sampleLessonSplitsIntoSections() {
        val p = pages(NotePrompt.sample)
        assertEquals(
            listOf("導讀", "4 堆疊與佇列", "4.1 堆疊（Stack）", "4.2 佇列（Queue）", "4.3 本章小測驗", "5 樹", "5.1 二元樹"),
            p.map { it.title },
        )
        assertEquals(listOf(0, 1, 2, 2, 2, 1, 2), p.map { it.depth })
        assertEquals("4 堆疊與佇列", p[2].chapter)
        assertEquals(null, p[1].chapter)
        assertTrue(p[0].blocks.first() is MdBlock.Heading) // 導讀頁含課程標題
    }

    @Test fun emptyChapterHeadingIsCarriedIntoFirstSection() {
        val p = pages("# 課\n## 1 章\n### 1.1 節\n內容\n### 1.2 節二\n內容二")
        assertEquals(listOf("1.1 節", "1.2 節二"), p.map { it.title })
        // 「# 課」與「## 1 章」沒有內文 → 帶到第一頁最上方
        assertEquals(
            listOf(MdBlock.Heading(1, "課"), MdBlock.Heading(2, "1 章"), MdBlock.Heading(3, "1.1 節")),
            p[0].blocks.take(3),
        )
        assertEquals("1 章", p[1].chapter)
    }

    @Test fun onlyH2MeansEachH2IsAPage() {
        val p = pages("# T\n前言\n## A\na\n---\n## B\nb")
        assertEquals(listOf("導讀", "A", "B"), p.map { it.title })
        assertTrue(p[1].blocks.none { it is MdBlock.Rule }) // 分隔線被修掉
    }

    @Test fun noHeadingsIsSinglePage() {
        assertEquals(1, pages("只是一段文字").size)
    }

    @Test fun stripFenceAndContinuation() {
        assertEquals("# A\n內容", Lesson.stripOuterFence("```markdown\n# A\n內容\n```"))
        assertEquals("# A", Lesson.stripOuterFence("# A"))
        val first = "# 課\n### 1.1 a\n內容\n\n（未完，請回覆「繼續」）\n"
        val merged = Lesson.appendContinuation(first, "```markdown\n### 1.2 b\n更多\n```")
        assertEquals("# 課\n### 1.1 a\n內容\n\n### 1.2 b\n更多", merged)
    }
}
