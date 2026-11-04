package org.headfi.app.net.xenforo

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Parses `/account/alerts` (and the conversation inbox uses a similar row, but
 * conversations stay in the WebView because the thread view is a chat UI).
 */
object AlertsParser {

    fun parse(html: String, baseUrl: String = XenForoUrls.BASE): AlertsPage {
        val document = Jsoup.parse(html, baseUrl)

        val alerts = document.select(".contentRow")
            .mapNotNull(::parseRow)

        if (alerts.isEmpty()) {
            throw XenForoParseException(
                "No alert rows found (template=${XenForoMarkup.meta(html).template})"
            )
        }

        return AlertsPage(
            alerts = alerts,
            pageNav = PageNavParser.parse(document)
        )
    }

    private fun parseRow(row: Element): AlertItem? {
        val link = row.selectFirst(".contentRow-title a[href]") ?: return null
        val title = link.text().trim()
        if (title.isEmpty()) return null

        val href = link.absUrl("href").ifBlank { link.attr("href") }

        return AlertItem(
            title = title,
            url = XenForoUrls.absolute(href),
            snippet = row.selectFirst(".contentRow-snippet")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            timeAgo = row.selectFirst(".contentRow-minor time, time")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            avatarUrl = row.selectFirst(".contentRow-figure img[src], img.avatar[src]")
                ?.let { image ->
                    image.absUrl("src").ifBlank { image.attr("src") }
                }
                ?.takeIf { it.isNotBlank() }
                ?.let(XenForoUrls::absolute),
            isUnread = row.hasClass("is-unread") || row.hasClass("contentRow--unread")
        )
    }
}
