package org.headfi.app.net.xenforo

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Parses `/account/alerts`.
 *
 * XenForo 2.3 no longer wraps alerts in `.contentRow-title`: each alert is an
 * `li.alert` (which carries `is-unread`) containing a `.contentRow` whose
 * `.contentRow-main` is the sentence — "Alice replied to the thread <title>" —
 * with the target in `a.fauxBlockLink-blockLink`. Older/other themes still use
 * `.contentRow-title`, so both shapes are accepted.
 *
 * An empty page is only accepted when the template really is `account_alerts`;
 * otherwise a markup change must still surface as an error instead of "you have
 * no alerts".
 */
object AlertsParser {

    private const val TEMPLATE_ALERTS = "account_alerts"

    fun parse(html: String, baseUrl: String = XenForoUrls.BASE): AlertsPage {
        val document = Jsoup.parse(html, baseUrl)
        val meta = XenForoMarkup.meta(html)

        val rows = document.select("li.alert").ifEmpty { document.select(".contentRow") }
        val alerts = rows.mapNotNull(::parseRow)

        if (alerts.isEmpty() && meta.template != TEMPLATE_ALERTS) {
            throw XenForoParseException(
                "No alert rows found (template=${meta.template})"
            )
        }

        return AlertsPage(
            alerts = alerts,
            pageNav = PageNavParser.parse(document)
        )
    }

    private fun parseRow(row: Element): AlertItem? {
        val content = row.selectFirst(".contentRow") ?: row

        val link = content.selectFirst("a.fauxBlockLink-blockLink[href]")
            ?: content.selectFirst(".contentRow-title a[href]")
            ?: content.selectFirst(
                "a[href]:not([href*='/members/']):not([href*='/account/alert'])"
            )
            ?: return null

        val href = link.absUrl("href").ifBlank { link.attr("href") }
        if (href.isBlank()) return null

        val sentence = content.selectFirst(".contentRow-main")
            ?.clone()
            ?.apply {
                select(
                    ".contentRow-minor, .u-pullRight, .js-alertToggle, .alertToggler, script, style"
                ).remove()
            }
            ?.text()
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            .orEmpty()

        val linkedTitle = link.text().trim()
        val title = sentence.ifBlank { linkedTitle }
        if (title.isBlank()) return null

        return AlertItem(
            title = title,
            url = XenForoUrls.absolute(href),
            snippet = content.selectFirst(".contentRow-snippet")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            timeAgo = content.selectFirst(".contentRow-minor time, time")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            avatarUrl = content.selectFirst(".contentRow-figure img[src], img.avatar[src]")
                ?.let { image ->
                    image.absUrl("src").ifBlank { image.attr("src") }
                }
                ?.takeIf { it.isNotBlank() }
                ?.let(XenForoUrls::absolute),
            isUnread = row.hasClass("is-unread") || content.hasClass("is-unread")
        )
    }
}
