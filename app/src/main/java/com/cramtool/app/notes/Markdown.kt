package com.cramtool.app.notes

/** Markdown 解析後的區塊，閱讀器每個區塊是 LazyColumn 的一列，方便用大綱跳轉 */
sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    /** @param marker 有序清單的「1.」，無序為 null；checked 為待辦清單狀態 */
    data class ListItem(val depth: Int, val marker: String?, val text: String, val checked: Boolean? = null) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Code(val language: String, val code: String) : MdBlock()
    data class Math(val tex: String) : MdBlock()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock()
    data object Rule : MdBlock()
    /** 圖片：![alt](路徑)，路徑為 App 內部檔案（匯入 Word／PPT 時解出） */
    data class Image(val alt: String, val src: String) : MdBlock()
}

data class OutlineEntry(val level: Int, val title: String, val blockIndex: Int)

object Markdown {
    private val HEADING = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val SETEXT_H1 = Regex("""^=+\s*$""")
    private val RULE = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
    private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val ORDERED = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
    private val TASK = Regex("""^\[([ xX])]\s+(.*)$""")
    private val FENCE = Regex("""^\s*(```|~~~)\s*([\w+#.-]*).*$""")
    private val IMAGE = Regex("""^!\[([^\]]*)]\(([^)\s]+)\)$""")
    private val TABLE_SEP = Regex("""^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")

    fun parse(src: String): List<MdBlock> {
        val lines = src.replace("\r\n", "\n").replace('\t', ' ').split('\n')
        val out = mutableListOf<MdBlock>()
        val para = StringBuilder()

        fun flushPara() {
            if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim())
            para.clear()
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // 程式碼區塊
            val fenceMatch = FENCE.matchEntire(line)
            if (fenceMatch != null) {
                flushPara()
                val fence = fenceMatch.groupValues[1]
                val lang = fenceMatch.groupValues[2]
                val body = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(fence)) { body += lines[i]; i++ }
                i++ // 跳過結尾 fence
                out += MdBlock.Code(lang, body.joinToString("\n").trimEnd())
                continue
            }

            // 數學區塊 $$ ... $$
            if (trimmed.startsWith("$$")) {
                flushPara()
                val first = trimmed.removePrefix("$$")
                if (first.endsWith("$$") && first.length >= 2) {
                    out += MdBlock.Math(first.removeSuffix("$$").trim())
                    i++; continue
                }
                val body = mutableListOf(first)
                i++
                while (i < lines.size && !lines[i].trim().endsWith("$$")) { body += lines[i]; i++ }
                if (i < lines.size) body += lines[i].trim().removeSuffix("$$")
                i++
                out += MdBlock.Math(body.joinToString("\n").trim())
                continue
            }

            if (trimmed.isEmpty()) { flushPara(); i++; continue }

            val headingMatch = HEADING.matchEntire(trimmed)
            if (headingMatch != null) {
                flushPara()
                out += MdBlock.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2])
                i++; continue
            }

            // Setext 標題：上一行是段落、這一行是 ===（不支援 ---，因為 LLM 常把 --- 當分隔線緊接在段落後）
            if (para.isNotBlank() && !para.contains('\n') && SETEXT_H1.matches(trimmed)) {
                val text = para.toString().trim()
                para.clear()
                out += MdBlock.Heading(1, text)
                i++; continue
            }

            if (RULE.matches(line)) { flushPara(); out += MdBlock.Rule; i++; continue }

            val img = IMAGE.matchEntire(trimmed)
            if (img != null) {
                flushPara()
                out += MdBlock.Image(img.groupValues[1], img.groupValues[2])
                i++; continue
            }

            // 表格：本行有 | 且下一行是分隔線
            if (trimmed.contains('|') && i + 1 < lines.size && TABLE_SEP.matches(lines[i + 1])) {
                flushPara()
                val header = splitRow(trimmed)
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < lines.size && lines[i].trim().contains('|') && lines[i].isNotBlank()) {
                    val r = splitRow(lines[i].trim())
                    rows += List(header.size) { r.getOrElse(it) { "" } }
                    i++
                }
                out += MdBlock.Table(header, rows)
                continue
            }

            if (trimmed.startsWith(">")) {
                flushPara()
                val body = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    body += lines[i].trim().removePrefix(">").removePrefix(" ")
                    i++
                }
                out += MdBlock.Quote(body.joinToString("\n").trim())
                continue
            }

            val bullet = BULLET.matchEntire(line)
            val ordered = ORDERED.matchEntire(line)
            if (bullet != null || ordered != null) {
                flushPara()
                val indent = (bullet ?: ordered)!!.groupValues[1].length
                var text = bullet?.groupValues?.get(2) ?: ordered!!.groupValues[3]
                val marker = ordered?.let { "${it.groupValues[2]}." }
                var checked: Boolean? = null
                TASK.matchEntire(text)?.let { t -> checked = t.groupValues[1] != " "; text = t.groupValues[2] }
                // 接續行（縮排且非新清單項）併入同一項
                i++
                while (i < lines.size && lines[i].isNotBlank() &&
                    BULLET.matchEntire(lines[i]) == null && ORDERED.matchEntire(lines[i]) == null &&
                    lines[i].startsWith("  ") && !lines[i].trim().startsWith("```")
                ) {
                    text += " " + lines[i].trim(); i++
                }
                out += MdBlock.ListItem(depth = (indent / 2).coerceAtMost(5), marker = marker, text = text, checked = checked)
                continue
            }

            // 一般段落（保留換行：Markdown 單一換行通常是 LLM 刻意分行）
            if (para.isNotEmpty()) para.append('\n')
            para.append(trimmed)
            i++
        }
        flushPara()
        return out
    }

    private fun splitRow(row: String): List<String> =
        row.trim().removePrefix("|").removeSuffix("|").split(Regex("""(?<!\\)\|""")).map { it.trim().replace("\\|", "|") }

    fun outline(blocks: List<MdBlock>): List<OutlineEntry> = blocks.mapIndexedNotNull { idx, b ->
        (b as? MdBlock.Heading)?.let { OutlineEntry(it.level, plainText(it.text), idx) }
    }

    /** 去掉行內標記，用於大綱與標題 */
    fun plainText(s: String): String = s
        .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")
        .replace(Regex("""(\*\*|__|~~|`)"""), "")
        .replace(Regex("""(?<![\w*])[*_](\S(?:.*?\S)?)[*_](?![\w*])"""), "$1")
        .trim()

    /** 用第一個標題當筆記名稱，沒有就用第一行 */
    fun guessTitle(src: String): String {
        val blocks = parse(src)
        val h = blocks.firstOrNull { it is MdBlock.Heading } as? MdBlock.Heading
        val t = h?.text ?: src.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        return plainText(t).take(60).ifBlank { "未命名筆記" }
    }
}
