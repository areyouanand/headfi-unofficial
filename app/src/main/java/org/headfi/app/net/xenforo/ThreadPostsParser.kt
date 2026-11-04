package org.headfi.app.net.xenforo

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parses a thread page into [ForumPost]s.
 *
 * Posts are rendered by the server for signed-out visitors too, so the native
 * reader works before the WebView has ever signed in; only actions (reply,
 * react, attachments) need the WebView.
 */
object ThreadPostsParser {

    fun parse(html: String, baseUrl: String = XenForoUrls.BASE): ThreadPostsPage {
        val document = Jsoup.parse(html, baseUrl)

        // `.message--post` excludes inline conversation messages and profile
        // posts that share the `.message` component.
        val messageElements = document.select(".message--post")
        val posts = (if (messageElements.isNotEmpty()) messageElements else document.select(".message"))
            .mapIndexedNotNull { index, element -> parsePost(element, index, baseUrl) }

        if (posts.isEmpty()) {
            throw XenForoParseException(
                "No posts found (template=${XenForoMarkup.meta(html).template})"
            )
        }

        // Watch state: XenForo flips the same control between /watch and
        // /unwatch. Match only thread-scoped links — the site navigation also
        // contains `/watched/threads`, which must not be mistaken for the button.
        val watchAnchor = document.selectFirst(
            "a[href*='/unwatch'], a[href*='/threads/'][href*='/watch'], " +
                ".p-title-pageAction a[href*='/watch']"
        )
        val watching = watchAnchor?.let { anchor ->
            anchor.attr("href").contains("/unwatch") ||
                anchor.text().contains("Unwatch", ignoreCase = true)
        } == true
        val threadWatchHref = watchAnchor
            ?.let { it.absUrl("href").ifBlank { it.attr("href") } }
            ?.takeIf { it.isNotBlank() && it.contains("/threads/") }
            ?.let(XenForoUrls::absolute)

        return ThreadPostsPage(
            title = title(document),
            posts = posts,
            pageNav = PageNavParser.parse(document),
            csrfToken = XenForoMarkup.meta(html).csrf,
            isWatching = watching,
            watchUrl = threadWatchHref ?: XenForoUrls.watchUrl(baseUrl),
            unwatchUrl = threadWatchHref ?: XenForoUrls.unwatchUrl(baseUrl)
        )
    }

    private fun title(document: Document): String? =
        document.selectFirst("h1.p-title-value")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: document.title().substringBefore(" | ").trim().takeIf { it.isNotEmpty() }

    private fun parsePost(element: Element, index: Int, baseUrl: String): ForumPost? {
        val author = element.attr("data-author").trim()
            .ifBlank { element.selectFirst(".message-name .username")?.text()?.trim().orEmpty() }
        if (author.isEmpty()) return null

        val permalinkElement = element.selectFirst(".message-attribution-opposite a[href]")
        val body = element.selectFirst(".message-content .bbWrapper")
            ?: element.selectFirst(".bbWrapper")
        val postId = postId(element)

        // XenForo puts the reaction and per-post reply links in the post footer.
        val actionBar = element.selectFirst(".message-actionBar")
            ?: element.selectFirst(".actionBar")
            ?: element

        // The reaction control lives in the post's reactions bar, which is not
        // inside `.message-actionBar` on every theme, so search the whole post.
        val reaction = element.selectFirst("a[href*='/react'], a[data-reaction-id]")
        val reactionUrl = reaction
            ?.let { it.absUrl("href").ifBlank { it.attr("href") } }
            ?.takeIf { it.isNotBlank() }
            ?.let(XenForoUrls::absolute)

        val quoteUrl = actionBar.selectFirst("a[href*='/reply']")
            ?.let { it.absUrl("href").ifBlank { it.attr("href") } }
            ?.takeIf { it.isNotBlank() }
            ?.let(XenForoUrls::absolute)
            ?: postId?.let { "${XenForoUrls.replyUrl(baseUrl)}?quote=$it" }

        // The post's own timestamp: prefer the machine-readable attributes over
        // the relative text ("4 minutes ago") so the app can show a real date.
        val timeElement = element.selectFirst("time[data-time], time[datetime]")
            ?: element.selectFirst("time")
        val postedAt = timeElement?.attr("data-time")?.toLongOrNull()
            ?: timeElement?.attr("datetime")?.let(::parseDateTime)

        val reactionSummary = element.selectFirst(".reactionsBar .reactionsBar-link")
            ?.text()
            ?.trim()

        return ForumPost(
            postId = postId,
            author = author,
            avatarUrl = element.selectFirst(".message-cell--user img.avatar[src], .message-avatar img[src]")
                ?.let { imageUrl(it) },
            authorTitle = element.selectFirst(".message-userTitle")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            timestamp = timeElement
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            postNumber = permalinkElement?.text()?.trim()?.takeIf { it.isNotEmpty() },
            permalink = permalinkElement
                ?.absUrl("href")
                ?.takeIf { it.isNotBlank() }
                ?.let(XenForoUrls::absolute),
            isFirstPost = index == 0 || element.hasClass("message--firstPost"),
            blocks = PostBodyParser.parse(body),
            reactionUrl = reactionUrl,
            reactionId = reaction?.attr("data-reaction-id")?.toIntOrNull()
                ?: Regex("""reaction_id=(\d+)""")
                    .find(reactionUrl.orEmpty())
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull(),
            reactionLabel = reaction
                ?.selectFirst(".reaction-text")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= 24 },
            hasReacted = reaction?.hasClass("is-reacted") == true ||
                reaction?.attr("data-reacted") == "true" ||
                element.selectFirst(".reaction.is-reacted, a[href*='reaction_id=0']") != null ||
                // Head-Fi's reactions bar lists the current user as "You" once
                // they have reacted (verified on-device in v1.6.0-beta.3).
                reactionSummary?.contains("You") == true,
            reactionCount = ReactionSummary.countFrom(reactionSummary),
            postedAtEpoch = postedAt,
            quoteUrl = quoteUrl
        )
    }

    private fun postId(element: Element): Int? =
        Regex("""post-(\d+)""").find(element.attr("data-content"))?.groupValues?.get(1)?.toIntOrNull()

    /**
     * XenForo writes `datetime="2026-11-04T17:52:00+0000"` (no colon in the
     * offset), which ISO parsers reject unless normalised.
     */
    private fun parseDateTime(raw: String): Long? {
        val normalized = raw.replace(Regex("""([+-]\d{2})(\d{2})$"""), "$1:$2")
        return runCatching {
            java.time.OffsetDateTime.parse(normalized).toEpochSecond()
        }.getOrNull() ?: runCatching {
            java.time.Instant.parse(raw).epochSecond
        }.getOrNull()
    }

    private fun imageUrl(image: Element): String? {
        val src = image.absUrl("src").ifBlank { image.attr("src") }
        return src.takeIf { it.isNotBlank() }?.let(XenForoUrls::absolute)
    }
}
