package com.cramtool.app.testutil

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 測試用的最小 PDF／DOCX／PPTX 檔 */
object Fixtures {
    /** 1x1 PNG */
    val PNG: ByteArray = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")

    fun pdf(pages: Int): ByteArray {
        val objs = ArrayList<String>()
        objs += "<< /Type /Catalog /Pages 2 0 R >>"
        val kids = (0 until pages).joinToString(" ") { "${3 + it} 0 R" }
        objs += "<< /Type /Pages /Kids [$kids] /Count $pages >>"
        repeat(pages) { objs += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 300] >>" }
        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = ArrayList<Int>()
        objs.forEachIndexed { i, o -> offsets += sb.length; sb.append("${i + 1} 0 obj\n$o\nendobj\n") }
        val xref = sb.length
        sb.append("xref\n0 ${objs.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { sb.append(String.format("%010d 00000 n \n", it)) }
        sb.append("trailer\n<< /Size ${objs.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun zip(entries: Map<String, Any>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            entries.forEach { (name, content) ->
                z.putNextEntry(ZipEntry(name))
                z.write(if (content is ByteArray) content else content.toString().toByteArray())
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private const val W = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""

    fun docx(): ByteArray = zip(mapOf(
        "[Content_Types].xml" to "<Types/>",
        "word/styles.xml" to """<w:styles $W>
            <w:style w:styleId="1"><w:name w:val="heading 1"/></w:style>
            <w:style w:styleId="Heading2"><w:name w:val="heading 2"/></w:style></w:styles>""",
        "word/_rels/document.xml.rels" to """<Relationships><Relationship Id="rId5" Target="media/image1.png"/></Relationships>""",
        "word/media/image1.png" to PNG,
        "word/document.xml" to """<w:document $W><w:body>
            <w:p><w:pPr><w:pStyle w:val="1"/></w:pPr><w:r><w:t>第一章 細胞</w:t></w:r></w:p>
            <w:p><w:r><w:t xml:space="preserve">細胞是</w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>生命的基本單位</w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>（重點）</w:t></w:r><w:r><w:t>。</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/></w:numPr></w:pPr><w:r><w:t>細胞膜</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="1"/></w:numPr></w:pPr><w:r><w:t>磷脂雙層</w:t></w:r></w:p>
            <w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:t>1.1 比較</w:t></w:r></w:p>
            <w:tbl><w:tr><w:tc><w:p><w:r><w:t>構造</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>功能</w:t></w:r></w:p></w:tc></w:tr>
              <w:tr><w:tc><w:p><w:r><w:t>粒線體</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>產生 ATP</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
            <w:p><w:r><w:drawing><a:blip r:embed="rId5"/></w:drawing></w:r></w:p>
        </w:body></w:document>""",
    ))

    private const val P = "xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""

    private fun slide(title: String, bullets: List<Pair<Int, String>>, withPic: Boolean = false) = """<p:sld $P><p:cSld><p:spTree>
        <p:sp><p:nvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:txBody><a:p><a:r><a:t>$title</a:t></a:r></a:p></p:txBody></p:sp>
        <p:sp><p:nvSpPr><p:nvPr><p:ph idx="1"/></p:nvPr></p:nvSpPr><p:txBody>${bullets.joinToString("") { (l, t) -> "<a:p><a:pPr lvl=\"$l\"/><a:r><a:t>$t</a:t></a:r></a:p>" }}</p:txBody></p:sp>
        ${if (withPic) "<p:pic><p:blipFill><a:blip r:embed=\"rId2\"/></p:blipFill></p:pic>" else ""}
        </p:spTree></p:cSld></p:sld>"""

    fun pptx(): ByteArray = zip(mapOf(
        "[Content_Types].xml" to "<Types/>",
        "ppt/presentation.xml" to """<p:presentation $P><p:sldIdLst><p:sldId r:id="rId2"/><p:sldId r:id="rId3"/></p:sldIdLst></p:presentation>""",
        "ppt/_rels/presentation.xml.rels" to """<Relationships><Relationship Id="rId2" Target="slides/slide1.xml"/><Relationship Id="rId3" Target="slides/slide2.xml"/></Relationships>""",
        "ppt/slides/slide1.xml" to slide("動物的組織", listOf(0 to "上皮組織", 1 to "單層鱗狀上皮", 0 to "結締組織"), withPic = true),
        "ppt/slides/_rels/slide1.xml.rels" to """<Relationships><Relationship Id="rId2" Target="../media/image1.png"/><Relationship Id="rId9" Target="../notesSlides/notesSlide1.xml"/></Relationships>""",
        "ppt/media/image1.png" to PNG,
        "ppt/notesSlides/notesSlide1.xml" to """<p:notes $P><p:cSld><p:spTree><p:sp><p:nvSpPr><p:nvPr><p:ph type="body"/></p:nvPr></p:nvSpPr><p:txBody><a:p><a:r><a:t>這張是考試重點</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:notes>""",
        "ppt/slides/slide2.xml" to slide("恆定性", listOf(0 to "負回饋", 0 to "正回饋")),
    ))

    fun write(dir: File, name: String, bytes: ByteArray) = File(dir, name).also { it.writeBytes(bytes) }
}
