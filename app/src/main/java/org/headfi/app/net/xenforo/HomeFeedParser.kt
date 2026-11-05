package org.headfi.app.net.xenforo

import org.jsoup.Jsoup

/**
 * Parses Head-Fi's editorial front page (`hfh_homepage`).
 *
 * The cards are deliberately parsed from the rendered DOM rather than a feed:
 * the front page is curated and does not expose an RSS equivalent with the same
 * items. Each card's image is a CSS background, not an `<img>`, so it is read
 * out of the inline style.
 *
 * Throws [XenForoParseException] when the grid is missing, so the caller can
 * fall back to the browser instead of showing an empty "front page".
 */
object HomeFeedParser {

    private val IMAGE_URL = Regex("""url\(([^)]+)\)""")

    fun parse(html: String, baseUrl: String = XenForoUrls.HOME): List<HomeFeedItem> {
        val document = Jsoup.parse(html, baseUrl)
        val cards = document.select("#hfh_grid_container .hfh_slot_container")

        if (cards.isEmpty()) {
            throw XenForoParseException(
                "No front-page cards found (template=${XenForoMarkup.meta(html).template})"
            )
        }

        return cards.mapNotNull(::parseCard)
            .distinctBy { it.url }
    }

    private fun parseCard(card: org.jsoup.nodes.Element): HomeFeedItem? {
        val link = card.selectFirst("a.hfh_slot_background[href]") ?: return null
        val href = link.absUrl("href").ifBlank { link.attr("href") }
        val url = XenForoUrls.absolute(href)
        if (url.isBlank()) return null

        val title = card.selectFirst(".hfh_slot_content .bbWrapper")
            ?.text()
            ?.trim()
            .orEmpty()
        if (title.isBlank()) return null

        val excerpt = card.selectFirst(".hfh_slot_text .bbWrapper")
            ?.text()
            ?.trim()
            .orEmpty()

        val image = IMAGE_URL.find(link.attr("style"))
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.trim('\'', '"')
            ?.takeIf { it.isNotBlank() }
            ?.let(XenForoUrls::absolute)

        return HomeFeedItem(
            title = title,
            excerpt = excerpt,
            url = url,
            imageUrl = image,
            kind = kindOf(url)
        )
    }

    private fun kindOf(url: String): HomeFeedKind {
        val path = XenForoUrls.pathOf(url)
        return when {
            path.startsWith("/threads/") -> HomeFeedKind.THREAD
            path.startsWith("/showcase/") && url.contains("#review") -> HomeFeedKind.REVIEW
            path.startsWith("/showcase/") -> HomeFeedKind.SHOWCASE
            path.startsWith("/articles/") -> HomeFeedKind.ARTICLE
            else -> HomeFeedKind.OTHER
        }
    }
}
