package com.cramtool.app

import com.cramtool.app.data.SOURCE_DOCX
import com.cramtool.app.data.SOURCE_PDF
import com.cramtool.app.data.SOURCE_PPTX
import com.cramtool.app.docs.DocumentImporter
import com.cramtool.app.docs.OfficeConverter
import com.cramtool.app.notes.Lesson
import com.cramtool.app.notes.Markdown
import com.cramtool.app.notes.MdBlock
import com.cramtool.app.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OfficeConverterTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun docxToMarkdown() {
        val f = Fixtures.write(tmp.root, "a.docx", Fixtures.docx())
        val media = File(tmp.root, "a_media")
        val md = OfficeConverter.docx(f, media).markdown
        assertTrue(md, md.contains("# 第一章 細胞"))
        // 相鄰的粗體片段合併成一段
        assertTrue(md, md.contains("細胞是**生命的基本單位（重點）**。"))
        assertTrue(md, md.contains("- 細胞膜\n  - 磷脂雙層"))
        assertTrue(md, md.contains("## 1.1 比較"))
        assertTrue(md, md.contains("| 構造 | 功能 |") && md.contains("| 粒線體 | 產生 ATP |"))
        val img = Markdown.parse(md).filterIsInstance<MdBlock.Image>().single()
        assertTrue(File(img.src).exists())
    }

    @Test fun pptxOneSlidePerPage() {
        val f = Fixtures.write(tmp.root, "b.pptx", Fixtures.pptx())
        val r = OfficeConverter.pptx(f, File(tmp.root, "b_media"))
        assertEquals(2, r.pages)
        val md = r.markdown
        assertTrue(md, md.contains("## 1. 動物的組織"))
        assertTrue(md, md.contains("- 上皮組織\n  - 單層鱗狀上皮\n- 結締組織"))
        assertTrue(md, md.contains("講者備註") && md.contains("> 這張是考試重點"))
        assertTrue(md, md.contains("## 2. 恆定性"))
        // 分頁閱讀：一張投影片一頁
        val pages = Lesson.paginate(Markdown.parse(md))
        assertEquals(listOf("1. 動物的組織", "2. 恆定性"), pages.map { it.title })
        assertTrue(pages[0].blocks.any { it is MdBlock.Image })
    }

    @Test fun detectByContent() {
        assertEquals(SOURCE_PDF, DocumentImporter.detect(Fixtures.write(tmp.root, "x.bin", Fixtures.pdf(1))))
        assertEquals(SOURCE_DOCX, DocumentImporter.detect(Fixtures.write(tmp.root, "y.bin", Fixtures.docx())))
        assertEquals(SOURCE_PPTX, DocumentImporter.detect(Fixtures.write(tmp.root, "z.bin", Fixtures.pptx())))
        assertEquals(null, DocumentImporter.detect(Fixtures.write(tmp.root, "t.txt", "hello".toByteArray())))
    }

    @Test fun markdownEscapesSpecialCharacters() {
        // Word 內文出現 * 或 $ 時不應被當成格式
        val f = Fixtures.write(tmp.root, "c.docx", Fixtures.docx())
        val md = OfficeConverter.docx(f, File(tmp.root, "c_media")).markdown
        assertTrue(!md.contains("$"))
    }
}
