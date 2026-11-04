package org.headfi.app.net.xenforo

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Parses the XenForo 2.x forums index into [ForumCategory] / [ForumNode].
 *
 * Deliberately tolerant rather than clever: XenForo templates differ between
 * versions and add-ons, so the parser accepts a few plausible shapes and throws
 * [XenForoParseException] when none match. Callers are expected to fall back to
 * the WebView on that exception rather than show an empty list.
 *
 * Pure JVM: takes an HTML string, needs no Android APIs, and is covered by
 * `ForumsIndexParserTest`.
 */
object ForumsIndexParser {

    const val BASE_URL = "https://www.head-fi.org/forums/"

    fun parse(html: String, baseUrl: String = BASE_URL): List<ForumCategory> {
        val document = Jsoup.parse(html, baseUrl)

        val categories = document.select("div.block--category").mapNotNull { block ->
            val forums = block.select(".node--forum").mapNotNull(::parseNode)
            if (forums.isEmpty()) {
                null
            } else {
                ForumCategory(
                    id = block.classNames()
                        .firstOrNull { it.startsWith(CATEGORY_CLASS) && it != CATEGORY_CLASS }
                        ?.removePrefix(CATEGORY_CLASS)
                        ?.toIntOrNull(),
                    title = block.selectFirst("h2.block-header")?.text()?.trim().orEmpty(),
                    forums = forums
                )
            }
        }
        if (categories.isNotEmpty()) return categories

        // Some index variants (and search results) render forum nodes without
        // category wrappers.
        val bareForums = document.select(".node--forum").mapNotNull(::parseNode)
        if (bareForums.isNotEmpty()) {
            return listOf(ForumCategory(id = null, title = "", forums = bareForums))
        }

        throw XenForoParseException(
            "No forum nodes found; page is not a forums index " +
                "(template=${XenForoMarkup.meta(html).template})"
        )
    }

    private fun parseNode(node: Element): ForumNode? {
        val link = node.selectFirst("h3.node-title a[href]")
            ?: node.selectFirst("a.node-title[href]")
            ?: return null

        val title = link.text().trim()
        if (title.isEmpty()) return null

        return ForumNode(
            nodeId = node.attr("data-node-id").toIntOrNull(),
            title = title,
            url = link.absUrl("href").ifBlank { link.attr("href") },
            description = node.selectFirst(".node-description")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            threadCount = stat(node, label = "Thread", fallbackIndex = 0),
            messageCount = stat(node, label = "Message", fallbackIndex = 1),
            unread = node.hasClass("node--unread")
        )
    }

    /**
     * Reads a `<dl class="pairs"><dt>Threads</dt><dd>1,234</dd></dl>` stat.
     * Falls back to position when the label is worded differently.
     */
    private fun stat(node: Element, label: String, fallbackIndex: Int): Int? {
        val byLabel = node.select(".node-stats .pairs")
            .firstOrNull { pair ->
                pair.selectFirst("dt")?.text()?.contains(label, ignoreCase = true) == true
            }
            ?.selectFirst("dd")
            ?.text()
            ?.toCount()

        if (byLabel != null) return byLabel

        return node.select(".node-stats dd")
            .getOrNull(fallbackIndex)
            ?.text()
            ?.toCount()
    }

    /** `12,345` / `12.3K` are both reported; only exact integers are trusted. */
    private fun String.toCount(): Int? =
        trim().replace(",", "").takeIf { it.all(Char::isDigit) }?.toIntOrNull()

    private const val CATEGORY_CLASS = "block--category"
}
