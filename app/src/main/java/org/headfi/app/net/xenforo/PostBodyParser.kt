package org.headfi.app.net.xenforo

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Converts a XenForo post body (`div.bbWrapper`) into [PostBlock]s.
 *
 * XenForo stores posts as HTML, not plain text: bold/italic, inline links,
 * quotes, code, lists and images all arrive as tags. Rendering that as one
 * string would lose all of it, so this walks the tree and emits paragraphs,
 * quotes and images, recording character ranges for inline styling.
 *
 * Images are pulled out into their own blocks rather than inlined, because
 * Compose text cannot flow an image inside a paragraph.
 */
object PostBodyParser {

    fun parse(container: Element?): List<PostBlock> {
        if (container == null) return emptyList()
        val builder = Builder()
        builder.visitChildren(container)
        builder.flush()
        return builder.blocks.toList()
    }

    private class Builder {
        val blocks = mutableListOf<PostBlock>()
        private val text = StringBuilder()
        private val spans = mutableListOf<TextSpan>()

        fun visitChildren(element: Element) {
            element.childNodes().forEach(::visit)
        }

        fun flush() {
            val raw = text.toString().trim()
            if (raw.isNotEmpty()) {
                // The trim above shifts every span, so re-clamp to the trimmed text.
                val leading = text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val shifted = spans.mapNotNull { span ->
                    val start = (span.start - leading).coerceIn(0, raw.length)
                    val end = (span.end - leading).coerceIn(0, raw.length)
                    if (end > start) span.copy(start = start, end = end) else null
                }
                blocks += PostBlock.Paragraph(RichText(raw, shifted))
            }
            text.setLength(0)
            spans.clear()
        }

        private fun visit(node: Node) {
            when (node) {
                is TextNode -> appendText(node.text())
                is Element -> visitElement(node)
            }
        }

        private fun visitElement(element: Element) {
            when (element.tagName().lowercase()) {
                "br" -> appendRaw("\n")
                "script", "style", "noscript", "iframe", "video", "audio" -> Unit
                "img" -> {
                    val src = imageUrl(element)
                    if (src != null) {
                        flush()
                        blocks += PostBlock.Image(src, element.attr("alt").takeIf { it.isNotBlank() })
                    }
                }

                "blockquote" -> {
                    flush()
                    val content = element.selectFirst(".bbCodeBlock-content") ?: element
                    val quote = content.text().trim()
                    if (quote.isNotEmpty()) blocks += PostBlock.Quote(quote)
                }

                "li" -> {
                    flush()
                    appendRaw("• ")
                    visitChildren(element)
                    appendRaw("\n")
                }

                "p", "div", "section", "table", "tr", "header", "footer", "aside" -> {
                    flush()
                    visitChildren(element)
                    flush()
                }

                "ul", "ol" -> {
                    flush()
                    visitChildren(element)
                    flush()
                }

                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    flush()
                    val start = text.length
                    visitChildren(element)
                    if (text.length > start) addSpan(start, text.length, TextSpan.Kind.BOLD)
                    flush()
                }

                "a" -> {
                    val href = element.absUrl("href").ifBlank { element.attr("href") }
                    val start = text.length
                    visitChildren(element)
                    if (href.isNotBlank() && text.length > start) {
                        addSpan(start, text.length, TextSpan.Kind.LINK, href)
                    }
                }

                "b", "strong" -> styled(element, TextSpan.Kind.BOLD)
                "i", "em" -> styled(element, TextSpan.Kind.ITALIC)
                "u" -> styled(element, TextSpan.Kind.UNDERLINE)
                "s", "strike", "del" -> styled(element, TextSpan.Kind.STRIKETHROUGH)
                "code", "pre" -> styled(element, TextSpan.Kind.CODE)

                else -> visitChildren(element)
            }
        }

        private fun styled(element: Element, kind: TextSpan.Kind) {
            val start = text.length
            visitChildren(element)
            if (text.length > start) addSpan(start, text.length, kind)
        }

        private fun addSpan(start: Int, end: Int, kind: TextSpan.Kind, url: String? = null) {
            spans += TextSpan(start, end, kind, url)
        }

        /** Collapses HTML indentation to single spaces, but keeps explicit breaks. */
        private fun appendText(value: String) {
            if (value.isEmpty()) return
            val collapsed = value.replace(Regex("[\\t\\r\\n ]+"), " ")
            if (collapsed.isBlank() && text.isEmpty()) return
            text.append(collapsed)
        }

        private fun appendRaw(value: String) {
            text.append(value)
        }

        private fun imageUrl(element: Element): String? {
            if (element.hasClass("smilie")) return null
            val src = element.absUrl("src").ifBlank { element.attr("src") }
            if (src.isBlank() || src.startsWith("data:")) return null
            // Emoji and inline smilies are tiny; only real content images matter.
            val width = element.attr("width").toIntOrNull()
            val height = element.attr("height").toIntOrNull()
            if (width != null && height != null && width <= 32 && height <= 32) return null
            return XenForoUrls.absolute(src)
        }
    }
}
