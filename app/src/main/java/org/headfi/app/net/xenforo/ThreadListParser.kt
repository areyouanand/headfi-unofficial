package org.headfi.app.net.xenforo

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parses any XenForo thread listing: a forum view, "What's New", watched
 * threads, or search results.
 *
 * These pages share one row component (`.structItem--thread`), which is why a
 * single parser covers them. Throws [XenForoParseException] instead of
 * returning an empty list so callers can fall back to the WebView.
 */
object ThreadListParser {

    fun parse(html: String, baseUrl: String = XenForoUrls.BASE): ThreadListPage {
        val document = Jsoup.parse(html, baseUrl)

        val threads = document.select(".structItem--thread")
            .mapNotNull(::parseRow)

        if (threads.isEmpty()) {
            throw XenForoParseException(
                "No thread rows found (template=${XenForoMarkup.meta(html).template})"
            )
        }

        return ThreadListPage(
            title = document.selectFirst("h1.p-title-value")?.text()?.trim(),
            threads = threads,
            pageNav = PageNavParser.parse(document)
        )
    }

    private fun parseRow(row: Element): ThreadSummary? {
        val titleLink = row.select(".structItem-title a[href]")
            .firstOrNull { it.attr("href").contains("/threads/") }
            ?: row.selectFirst(".structItem-title a[href]")
            ?: return null

        val title = titleLink.text().trim()
        if (title.isEmpty()) return null

        val href = titleLink.absUrl("href").ifBlank { titleLink.attr("href") }

        return ThreadSummary(
            threadId = XenForoUrls.threadId(href) ?: idFromDataContent(row),
            title = title,
            url = XenForoUrls.absolute(href),
            author = row.selectFirst(".structItem-parts .username, .structItem-parts a[href*='/members/']")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            replyCount = stat(row, "Replies", 0),
            viewCount = stat(row, "Views", 1),
            lastPoster = row.selectFirst(".structItem-cell--latest .username, .structItem-cell--latest a[href*='/members/']")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            lastPostTime = row.selectFirst(".structItem-cell--latest time")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            prefix = row.selectFirst(".structItem-title .label")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            forumName = row.selectFirst(".structItem-parts a[href*='/forums/']")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            isSticky = row.hasClass("is-pinned") || row.hasClass("structItem--sticky"),
            isUnread = row.hasClass("structItem--unread") ||
                row.hasClass("is-unread") ||
                row.selectFirst(".structItem-iconContainer .unread") != null
        )
    }

    private fun idFromDataContent(row: Element): Int? =
        Regex("""thread-(\d+)""").find(row.attr("data-content"))?.groupValues?.get(1)?.toIntOrNull()

    private fun stat(row: Element, label: String, fallbackIndex: Int): Int? {
        val byLabel = row.select(".structItem-cell--meta .pairs")
            .firstOrNull { it.selectFirst("dt")?.text()?.contains(label, true) == true }
            ?.selectFirst("dd")
            ?.text()
            ?.toCount()

        if (byLabel != null) return byLabel

        return row.select(".structItem-cell--meta dd")
            .getOrNull(fallbackIndex)
            ?.text()
            ?.toCount()
    }

    /**
     * XenForo abbreviates large counts (`1.2K`, `3M`). Returns null for those
     * rather than guessing, so the UI can simply omit the stat.
     */
    private fun String.toCount(): Int? =
        trim().replace(",", "").takeIf { it.all(Char::isDigit) }?.toIntOrNull()
}
