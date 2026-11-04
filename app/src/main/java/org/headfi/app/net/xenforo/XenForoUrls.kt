package org.headfi.app.net.xenforo

import org.jsoup.nodes.Document

/** Site URLs and the small amount of URL arithmetic the parsers need. */
object XenForoUrls {

    const val BASE = "https://www.head-fi.org"

    private val THREAD_ID = Regex("""/threads/(?:[^/]+\.)?(\d+)(?:/|$)""")
    private val FORUM_ID = Regex("""/forums/(?:[^/]+\.)?(\d+)(?:/|$)""")

    fun threadId(url: String): Int? = THREAD_ID.find(url)?.groupValues?.get(1)?.toIntOrNull()

    fun forumId(url: String): Int? = FORUM_ID.find(url)?.groupValues?.get(1)?.toIntOrNull()

    fun isThread(url: String): Boolean = url.contains("/threads/")

    /** `/forums/<slug>.<id>/` — a single forum, not the index. */
    fun isForum(url: String): Boolean = forumId(url) != null

    fun isForumsIndex(url: String): Boolean {
        val path = pathOf(url)
        return path == "/forums" || path == "/forums/"
    }

    /**
     * RSS mirror of a forum page, e.g. `/forums/portable-headphones.4/` →
     * `/forums/portable-headphones.4/index.rss`.
     *
     * Head-Fi does not put these feeds behind Cloudflare's bot challenge, so they
     * are the fallback when the HTML page needs verification. Only real forum
     * pages have one; search, What's New and watched threads do not.
     */
    fun rssFor(url: String): String? {
        if (!isForum(url)) return null
        val forum = Regex("""^(/forums/[^/]+)""").find(pathOf(url))
            ?.groupValues
            ?.get(1)
            ?: return null
        return "$BASE$forum/index.rss"
    }

    /** Latest threads across the whole board; public, no challenge. */
    const val LATEST_THREADS_FEED = "https://www.head-fi.org/forums/-/index.rss"

    /** Canonical permalink for a single post. */
    fun postUrl(postId: Int): String = "$BASE/posts/$postId/"

    /** XenForo's reaction endpoint for a post. */
    fun reactionUrl(postId: Int): String = "$BASE/posts/$postId/react"

    /** `/threads/<slug>.<id>/watch` for the given thread URL. */
    fun watchUrl(threadUrl: String): String = threadAction(threadUrl, "watch")

    /**
     * XenForo's "Stop watching" posts `stop=1` to the same `/watch` route; there
     * is no `/unwatch` endpoint (verified against the live switch-overlay control).
     */
    fun unwatchUrl(threadUrl: String): String = watchUrl(threadUrl)

    private fun threadAction(url: String, action: String): String {
        val clean = pathOf(url)
            .replace(Regex("""/page-\d+/?$"""), "/")
            .trimEnd('/')
        return if (clean.startsWith("/threads/")) "$BASE$clean/$action" else "$BASE/threads/"
    }

    fun pathOf(url: String): String = runCatching {
        java.net.URI(url).path.orEmpty()
    }.getOrDefault(url)

    /** `/threads/<slug>.<id>/reply` for the given thread URL. */
    fun replyUrl(threadUrl: String): String {
        val id = threadId(threadUrl) ?: return absolute("/threads/")
        val slug = Regex("""/threads/([^/]+)""").find(pathOf(threadUrl))?.groupValues?.get(1)
            ?: "thread.$id"
        val clean = slug.substringBefore(".")
        return "$BASE/threads/$clean.$id/reply"
    }

    fun absolute(url: String): String = when {
        url.isBlank() -> BASE
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("/") -> BASE + url
        else -> "$BASE/$url"
    }
}

/** Pagination for any XenForo list, read from the rendered pager. */
data class PageNav(
    val currentPage: Int = 1,
    val pageCount: Int = 1,
    val previousUrl: String? = null,
    val nextUrl: String? = null,
    val firstUrl: String? = null,
    val lastUrl: String? = null
) {
    val hasPrevious: Boolean get() = previousUrl != null
    val hasNext: Boolean get() = nextUrl != null
    val hasFirst: Boolean get() = firstUrl != null && currentPage > 1
    val hasLast: Boolean get() = lastUrl != null && currentPage < pageCount
}

object PageNavParser {

    fun parse(document: Document): PageNav {
        val current = document.selectFirst(".pageNav-page--current")
            ?.text()
            ?.trim()
            ?.toIntOrNull()
            ?: 1

        val highestVisible = document.select(".pageNav-page")
            .mapNotNull { it.text().trim().toIntOrNull() }
            .maxOrNull()
            ?: current

        val pageCount = maxOf(highestVisible, current)

        // The pager renders "1 … 12 13 14 … 213", so the first and last page
        // links are normally present even when the middle is collapsed.
        val pageLinks = document.select(".pageNav-page a[href]")
            .mapNotNull { link ->
                val number = link.text().trim().toIntOrNull() ?: return@mapNotNull null
                number to absoluteHref(link)
            }
            .toMap()

        val base = baseUrl(document.baseUri())

        val firstUrl = pageLinks[1] ?: base
        val lastUrl = when {
            pageCount <= 1 -> firstUrl
            pageLinks[pageCount] != null -> pageLinks[pageCount]
            // Query-based pagers (search) cannot be rebuilt by appending a path.
            !base.contains('?') -> "${base}page-$pageCount"
            else -> null
        }

        return PageNav(
            currentPage = current,
            pageCount = pageCount,
            previousUrl = link(document, "a.pageNav-jump--prev[href]"),
            nextUrl = link(document, "a.pageNav-jump--next[href]"),
            firstUrl = firstUrl,
            lastUrl = lastUrl
        )
    }

    private fun link(document: Document, selector: String): String? =
        document.selectFirst(selector)
            ?.absUrl("href")
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst(selector)
                ?.attr("href")
                ?.takeIf { it.isNotBlank() }
                ?.let(XenForoUrls::absolute)

    private fun absoluteHref(link: org.jsoup.nodes.Element): String =
        XenForoUrls.absolute(
            link.absUrl("href").ifBlank { link.attr("href") }
        )

    /** Strips `/page-N` and any query so a first/last page can be rebuilt. */
    private fun baseUrl(url: String): String {
        if (url.isBlank()) return XenForoUrls.BASE + "/"
        val path = url.substringBefore('?').replace(Regex("""/page-\d+/?$"""), "/")
        return if (path.endsWith("/")) path else "$path/"
    }
}
