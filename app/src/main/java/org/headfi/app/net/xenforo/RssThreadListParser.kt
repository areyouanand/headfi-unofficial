package org.headfi.app.net.xenforo

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/**
 * Parses XenForo's RSS thread feeds.
 *
 * These feeds are important for one reason: **Head-Fi's RSS endpoints are not
 * behind the Cloudflare bot challenge**, while the HTML pages are. On a fresh
 * install with no `cf_clearance` cookie the app can therefore still fill a forum
 * with its latest threads instead of showing an empty "verify" screen.
 *
 * The trade-off is a summary: the 30–50 newest threads, no stickies, no
 * pagination. [ThreadListPage.isPartial] marks it so the UI can say so.
 */
object RssThreadListParser {

    fun parse(xml: String, baseUrl: String): ThreadListPage {
        val document = Jsoup.parse(xml, baseUrl, Parser.xmlParser())

        val threads = document.select("item, entry").mapNotNull(::parseItem)
        if (threads.isEmpty()) {
            throw XenForoParseException("No items in RSS feed")
        }

        return ThreadListPage(
            title = document.selectFirst("channel > title, feed > title")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            threads = threads,
            pageNav = PageNav(),
            isPartial = true
        )
    }

    private fun parseItem(item: Element): ThreadSummary? {
        val link = item.selectFirst("link")?.text()?.trim()
            ?: item.selectFirst("link[href]")?.attr("href")?.trim()
            ?: return null
        val title = item.selectFirst("title")?.text()?.trim() ?: return null
        if (title.isEmpty() || link.isEmpty()) return null

        val absolute = XenForoUrls.absolute(link)

        return ThreadSummary(
            threadId = threadId(item, absolute),
            title = title,
            url = absolute,
            author = creator(item),
            replyCount = null,
            viewCount = null,
            lastPoster = null,
            lastPostTime = item.selectFirst("pubDate, published, updated")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            prefix = null,
            forumName = item.selectFirst("category")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            isSticky = false,
            isUnread = false
        )
    }

    private fun creator(item: Element): String? {
        // XenForo writes both `<dc:creator>Name</dc:creator>` and an RSS
        // `<author>email@example.com (Name)</author>`.
        item.selectFirst("dc|creator")?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        val author = item.selectFirst("author")?.text().orEmpty()
        return Regex("""\(([^)]+)\)\s*$""").find(author)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun threadId(item: Element, url: String): Int? =
        Regex("""urn:xenforo:thread:(\d+)""")
            .find(item.selectFirst("guid")?.text().orEmpty())
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: XenForoUrls.threadId(url)
}
