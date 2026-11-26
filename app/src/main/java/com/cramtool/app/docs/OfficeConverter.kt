package com.cramtool.app.docs

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 把 Word（.docx）與 PowerPoint（.pptx）轉成 Markdown，讓它們能用筆記閱讀器顯示
 * （大綱、分頁、字體、搜尋）。圖片解出到 [mediaDir]，以 ![](絕對路徑) 引用。
 *
 * 只取「內容」（標題、段落、條列、表格、圖片、講者備註），不還原版面設計。
 * 純 Java XML（DOM），可在 JVM 單元測試中執行。
 */
object OfficeConverter {

    data class Result(val title: String?, val markdown: String, val pages: Int)

    private val DISPLAYABLE = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

    private fun ZipFile.xml(name: String): Element? {
        val e = getEntry(name) ?: return null
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // 安全：不解析外部實體
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            isExpandEntityReferences = false
        }
        return getInputStream(e).use { f.newDocumentBuilder().parse(it).documentElement }
    }

    private fun Element.kids(): List<Element> {
        val out = ArrayList<Element>()
        var n: Node? = firstChild
        while (n != null) { if (n is Element) out += n; n = n.nextSibling }
        return out
    }
    private fun Element.kid(tag: String) = kids().firstOrNull { it.tagName == tag }
    private fun Element.all(tag: String): List<Element> {
        val nl = getElementsByTagName(tag)
        return (0 until nl.length).map { nl.item(it) as Element }
    }

    /** rels 檔：Id -> Target（已轉成 zip 內的完整路徑） */
    private fun ZipFile.rels(relsPath: String, baseDir: String): Map<String, String> {
        val root = xml(relsPath) ?: return emptyMap()
        return root.all("Relationship").associate { r ->
            r.getAttribute("Id") to resolve(baseDir, r.getAttribute("Target"))
        }
    }

    private fun resolve(baseDir: String, target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val parts = ArrayList(baseDir.split('/').filter { it.isNotEmpty() })
        target.split('/').forEach { seg ->
            when (seg) { ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex); ".", "" -> Unit; else -> parts += seg }
        }
        return parts.joinToString("/")
    }

    /** 解出圖片，回傳 Markdown 行（無法顯示的格式回傳說明文字） */
    private fun ZipFile.image(entryPath: String?, mediaDir: File, cache: MutableMap<String, String>): String? {
        entryPath ?: return null
        cache[entryPath]?.let { return it }
        val e = getEntry(entryPath) ?: return null
        val ext = entryPath.substringAfterLast('.', "").lowercase()
        val line = if (ext in DISPLAYABLE) {
            mediaDir.mkdirs()
            val out = File(mediaDir, entryPath.replace('/', '_'))
            if (!out.exists()) getInputStream(e).use { input -> out.outputStream().use { input.copyTo(it) } }
            "![圖片](${out.absolutePath})"
        } else "*（圖片格式 ${ext.uppercase()} 無法在 App 中顯示）*"
        cache[entryPath] = line
        return line
    }

    private fun escape(s: String) = s.replace(Regex("""([\\`*_\[\]$|])"""), """\\$1""")

    private fun coreTitle(zip: ZipFile): String? =
        zip.xml("docProps/core.xml")?.all("dc:title")?.firstOrNull()?.textContent?.trim()?.takeIf { it.isNotBlank() }

    // ============================== Word ==============================

    fun docx(file: File, mediaDir: File): Result = ZipFile(file).use { zip ->
        val doc = zip.xml("word/document.xml") ?: throw IllegalArgumentException("不是有效的 Word（.docx）檔")
        val rels = zip.rels("word/_rels/document.xml.rels", "word")
        // styleId -> 標題層級
        val headingOf = HashMap<String, Int>()
        zip.xml("word/styles.xml")?.all("w:style")?.forEach { st ->
            val id = st.getAttribute("w:styleId")
            val name = st.kid("w:name")?.getAttribute("w:val")?.lowercase().orEmpty()
            val lvl = Regex("""heading\s*(\d)""").find(name)?.groupValues?.get(1)?.toIntOrNull()
                ?: if (name == "title") 1 else null
            if (lvl != null) headingOf[id] = lvl.coerceIn(1, 6)
        }
        val cache = HashMap<String, String>()
        val out = StringBuilder()

        data class Seg(val text: String, val bold: Boolean, val italic: Boolean)

        fun runsText(p: Element, images: MutableList<String>): String {
            val segs = ArrayList<Seg>()
            fun visit(el: Element) {
                for (c in el.kids()) when (c.tagName) {
                    "w:r" -> {
                        val rPr = c.kid("w:rPr")
                        val bold = rPr?.kid("w:b")?.let { it.getAttribute("w:val") !in setOf("0", "false") } == true
                        val italic = rPr?.kid("w:i")?.let { it.getAttribute("w:val") !in setOf("0", "false") } == true
                        val t = StringBuilder()
                        for (rc in c.kids()) when (rc.tagName) {
                            "w:t" -> t.append(rc.textContent)
                            "w:tab" -> t.append(' ')
                            "w:br", "w:cr" -> t.append('\n')
                            "w:drawing", "w:pict", "w:object" -> {
                                val id = rc.all("a:blip").firstOrNull()?.getAttribute("r:embed")
                                    ?: rc.all("v:imagedata").firstOrNull()?.getAttribute("r:id")
                                zip.image(id?.let { rels[it] }, mediaDir, cache)?.let(images::add)
                            }
                        }
                        if (t.isNotEmpty()) segs += Seg(t.toString(), bold, italic)
                    }
                    "w:hyperlink", "w:ins", "w:smartTag", "w:sdt", "w:sdtContent", "w:fldSimple" -> visit(c)
                }
            }
            visit(p)
            // 相鄰且格式相同的片段先合併，再加上 ** / * 標記
            val merged = ArrayList<Seg>()
            for (sg in segs) {
                val last = merged.lastOrNull()
                if (last != null && last.bold == sg.bold && last.italic == sg.italic) merged[merged.lastIndex] = last.copy(text = last.text + sg.text)
                else merged += sg
            }
            return merged.joinToString("") { sg ->
                val text = escape(sg.text)
                if (text.isBlank() || (!sg.bold && !sg.italic)) text
                else {
                    val mark = (if (sg.bold) "**" else "") + (if (sg.italic) "*" else "")
                    text.takeWhile { it == ' ' } + mark + text.trim() + mark.reversed() + text.takeLastWhile { it == ' ' }
                }
            }
        }

        fun paragraph(p: Element) {
            val pPr = p.kid("w:pPr")
            val style = pPr?.kid("w:pStyle")?.getAttribute("w:val")
            val level = style?.let { headingOf[it] ?: Regex("""(?i)^heading(\d)$""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            val ilvl = pPr?.kid("w:numPr")?.kid("w:ilvl")?.getAttribute("w:val")?.toIntOrNull()
            val isList = pPr?.kid("w:numPr") != null
            val images = ArrayList<String>()
            val text = runsText(p, images).trim()
            when {
                text.isEmpty() -> Unit
                level != null -> out.append("#".repeat(level)).append(' ').append(text.replace("*", "").replace('\n', ' ')).append("\n\n")
                isList -> out.append("  ".repeat(ilvl ?: 0)).append("- ").append(text.replace('\n', ' ')).append('\n')
                else -> out.append(text.replace("\n", "  \n")).append("\n\n")
            }
            images.forEach { out.append(it).append("\n\n") }
        }

        fun table(tbl: Element) {
            val rows = tbl.kids().filter { it.tagName == "w:tr" }.map { tr ->
                tr.kids().filter { it.tagName == "w:tc" }.map { tc ->
                    tc.all("w:p").joinToString(" ") { runsText(it, ArrayList()).trim() }.replace("\n", " ").replace("|", "\\|")
                }
            }.filter { it.isNotEmpty() }
            if (rows.isEmpty()) return
            val cols = rows.maxOf { it.size }
            fun line(r: List<String>) = "| " + (0 until cols).joinToString(" | ") { r.getOrElse(it) { "" }.ifBlank { " " } } + " |"
            out.append('\n').append(line(rows[0])).append('\n')
            out.append("|").append(" --- |".repeat(cols)).append('\n')
            rows.drop(1).forEach { out.append(line(it)).append('\n') }
            out.append('\n')
        }

        fun block(el: Element) {
            for (c in el.kids()) when (c.tagName) {
                "w:p" -> paragraph(c)
                "w:tbl" -> table(c)
                "w:sdt" -> c.kid("w:sdtContent")?.let(::block)
            }
        }
        doc.kid("w:body")?.let(::block)
        val md = out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
        Result(coreTitle(zip), md, 0)
    }

    // ============================== PowerPoint ==============================

    fun pptx(file: File, mediaDir: File): Result = ZipFile(file).use { zip ->
        val pres = zip.xml("ppt/presentation.xml") ?: throw IllegalArgumentException("不是有效的 PowerPoint（.pptx）檔")
        val presRels = zip.rels("ppt/_rels/presentation.xml.rels", "ppt")
        val slides = pres.all("p:sldId").mapNotNull { presRels[it.getAttribute("r:id")] }
        val cache = HashMap<String, String>()
        val out = StringBuilder()
        val deckTitle = coreTitle(zip)
        if (deckTitle != null) out.append("# ").append(deckTitle).append("\n\n")

        fun paraText(p: Element): String {
            val sb = StringBuilder()
            for (c in p.kids()) when (c.tagName) {
                "a:r", "a:fld" -> {
                    val t = c.kid("a:t")?.textContent.orEmpty()
                    val bold = c.kid("a:rPr")?.getAttribute("b") == "1"
                    val e = escape(t)
                    sb.append(if (bold && e.isNotBlank()) "**${e.trim()}**" else e)
                }
                "a:br" -> sb.append(' ')
            }
            return sb.toString().replace("****", "").trim()
        }

        slides.forEachIndexed { i, path ->
            val slide = zip.xml(path) ?: return@forEachIndexed
            val dir = path.substringBeforeLast('/')
            val rels = zip.rels("$dir/_rels/${path.substringAfterLast('/')}.rels", dir)
            var title: String? = null
            val body = StringBuilder()

            fun shapes(tree: Element) {
                for (sh in tree.kids()) when (sh.tagName) {
                    "p:sp" -> {
                        val ph = sh.all("p:ph").firstOrNull()
                        val type = ph?.getAttribute("type").orEmpty()
                        val paras = sh.all("a:p").map { p ->
                            val lvl = p.kid("a:pPr")?.getAttribute("lvl")?.toIntOrNull() ?: 0
                            lvl to paraText(p)
                        }.filter { it.second.isNotBlank() }
                        if (paras.isEmpty()) continue
                        if ((type == "title" || type == "ctrTitle") && title == null) {
                            title = paras.joinToString(" ") { it.second.replace("*", "") }
                        } else {
                            // 內容版面配置區的文字預設是條列；一般文字方塊則當段落
                            val bulleted = ph != null && type != "subTitle"
                            paras.forEach { (lvl, t) ->
                                if (bulleted) body.append("  ".repeat(lvl)).append("- ").append(t).append('\n')
                                else body.append(t).append("\n\n")
                            }
                            if (bulleted) body.append('\n')
                        }
                    }
                    "p:pic" -> {
                        val id = sh.all("a:blip").firstOrNull()?.getAttribute("r:embed")
                        zip.image(id?.let { rels[it] }, mediaDir, cache)?.let { body.append(it).append("\n\n") }
                    }
                    "p:graphicFrame" -> sh.all("a:tbl").firstOrNull()?.let { tbl ->
                        val rows = tbl.all("a:tr").map { tr ->
                            tr.all("a:tc").map { tc -> tc.all("a:p").joinToString(" ") { paraText(it) }.replace("|", "\\|") }
                        }.filter { it.isNotEmpty() }
                        if (rows.isNotEmpty()) {
                            val cols = rows.maxOf { it.size }
                            fun line(r: List<String>) = "| " + (0 until cols).joinToString(" | ") { r.getOrElse(it) { "" }.ifBlank { " " } } + " |"
                            body.append(line(rows[0])).append('\n').append("|").append(" --- |".repeat(cols)).append('\n')
                            rows.drop(1).forEach { body.append(line(it)).append('\n') }
                            body.append('\n')
                        }
                    }
                    "p:grpSp" -> shapes(sh)
                }
            }
            slide.all("p:spTree").firstOrNull()?.let(::shapes)

            // 講者備註
            val notesPath = zip.rels("$dir/_rels/${path.substringAfterLast('/')}.rels", dir).values.firstOrNull { it.contains("notesSlide") }
            val notes = notesPath?.let { zip.xml(it) }?.all("p:sp")
                ?.filter { it.all("p:ph").firstOrNull()?.getAttribute("type") == "body" }
                ?.flatMap { sp -> sp.all("a:p").map(::paraText) }?.filter { it.isNotBlank() }.orEmpty()

            out.append("## ${i + 1}. ").append(title?.takeIf { it.isNotBlank() } ?: "投影片 ${i + 1}").append("\n\n")
            out.append(body.toString().trim()).append("\n\n")
            if (notes.isNotEmpty()) out.append("> 📝 **講者備註**\n").append(notes.joinToString("\n") { "> $it" }).append("\n\n")
        }
        val md = out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
        Result(deckTitle, md, slides.size)
    }
}
