package org.headfi.app.data

import android.content.Context
import android.util.Log
import org.headfi.app.net.ContentFetcher
import org.headfi.app.net.HeadFiContentClient
import org.headfi.app.net.PagePayload
import org.headfi.app.net.xenforo.AlertsPage
import org.headfi.app.net.xenforo.AlertsParser
import org.headfi.app.net.xenforo.ForumCategory
import org.headfi.app.net.xenforo.ForumsIndexParser
import org.headfi.app.net.xenforo.HomeFeedItem
import org.headfi.app.net.xenforo.HomeFeedParser
import org.headfi.app.net.xenforo.RssThreadListParser
import org.headfi.app.net.xenforo.ThreadListPage
import org.headfi.app.net.xenforo.ThreadListParser
import org.headfi.app.net.xenforo.ThreadPostsPage
import org.headfi.app.net.xenforo.ThreadPostsParser
import org.headfi.app.net.xenforo.XenForoParseException
import org.headfi.app.net.xenforo.XenForoUrls

/** Outcome of loading a native screen. */
sealed interface ContentResult<out T> {
    data class Ready<T>(val value: T) : ContentResult<T>

    /** Cloudflare needs a WebView pass before the content is readable. */
    data class NeedsVerification(val url: String) : ContentResult<Nothing>

    data class Failed(val message: String) : ContentResult<Nothing>
}

/**
 * Fetches and parses the pages the native screens show.
 *
 * Parsing failures are surfaced as [ContentResult.Failed] rather than crashes,
 * and every screen renders that as "open in browser" — the WebView stays the
 * safety net when Head-Fi's markup drifts.
 */
class ContentRepository internal constructor(
    private val client: ContentFetcher,
    private val session: SessionPreferencesRepository?
) {

    constructor(context: Context) : this(
        client = HeadFiContentClient(
            context = context.applicationContext,
            repository = SessionPreferencesRepository(context.applicationContext)
        ),
        session = SessionPreferencesRepository(context.applicationContext)
    )

    private val cache = object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    private data class CacheEntry(val value: Any, val storedAtMs: Long)

    suspend fun forums(url: String): ContentResult<List<ForumCategory>> =
        load(url) { ForumsIndexParser.parse(it, url) }

    suspend fun threadPosts(url: String): ContentResult<ThreadPostsPage> =
        load(url) { ThreadPostsParser.parse(it, url) }

    suspend fun alerts(url: String): ContentResult<AlertsPage> =
        load(url) { AlertsParser.parse(it, url) }

    /** Curated cards from Head-Fi's front page. */
    suspend fun homeFeed(): ContentResult<List<HomeFeedItem>> =
        load(XenForoUrls.HOME) { HomeFeedParser.parse(it, XenForoUrls.HOME) }

    /** Runs one live diagnostic request for the Session health screen. */
    suspend fun probe(): String = client.probe()

    /** Cookie names held in the app-private (and non-backed-up) session vault. */
    suspend fun storedCookieNames(): List<String> = session?.cookieNames().orEmpty()

    /**
     * Sends a reaction ("Like") for a post. Returns false when XenForo rejected
     * it — a challenge page, an expired session, or an error payload — so the
     * caller can hand that post to the WebView instead.
     */
    suspend fun react(reactionUrl: String, csrfToken: String, reactionId: Int): Boolean =
        client.postForm(
            url = reactionUrl,
            fields = mapOf(
                "_xfToken" to csrfToken,
                "reaction_id" to reactionId.toString()
            )
        )

    /**
     * Subscribes to a thread. XenForo's watch dialog posts `email_subscribe=1|0`
     * to the thread's `/watch` route; `false` gives in-app alerts only.
     */
    suspend fun watchThread(
        watchUrl: String,
        csrfToken: String,
        emailSubscribe: Boolean
    ): Boolean = client.postForm(
        url = watchUrl,
        fields = mapOf(
            "_xfToken" to csrfToken,
            "email_subscribe" to if (emailSubscribe) "1" else "0"
        )
    )

    suspend fun unwatchThread(unwatchUrl: String, csrfToken: String): Boolean =
        client.postForm(
            url = unwatchUrl,
            fields = mapOf(
                "_xfToken" to csrfToken,
                "stop" to "1"
            )
        )

    suspend fun threadList(url: String): ContentResult<ThreadListPage> {
        cached<ThreadListPage>(url)?.let { return ContentResult.Ready(it) }

        val payload = client.get(url)

        if (payload is PagePayload.Html) {
            runCatching { ThreadListParser.parse(payload.html, url) }
                .onSuccess { page ->
                    put(url, page)
                    return ContentResult.Ready(page)
                }
        }

        // RSS is not behind Cloudflare's challenge, so a forum can still show its
        // newest threads when the HTML page needs verification. Marked partial.
        rssFallback(url)?.let { return it }

        return when (payload) {
            is PagePayload.NeedsVerification -> ContentResult.NeedsVerification(payload.url)
            is PagePayload.Unavailable -> ContentResult.Failed(unavailableMessage(payload))
            else -> ContentResult.Failed("Could not read this page")
        }
    }

    /** Home's "latest activity" list: the board-wide RSS feed. */
    suspend fun latestThreads(): ContentResult<ThreadListPage> = load(
        url = XenForoUrls.LATEST_THREADS_FEED,
        headers = RSS_ACCEPT
    ) {
        RssThreadListParser.parse(it, XenForoUrls.LATEST_THREADS_FEED)
    }

    private suspend fun rssFallback(url: String): ContentResult<ThreadListPage>? {
        val rssUrl = XenForoUrls.rssFor(url) ?: return null
        val payload = client.get(rssUrl, RSS_ACCEPT)
        if (payload !is PagePayload.Html) return null

        return runCatching { RssThreadListParser.parse(payload.html, rssUrl) }
            .getOrNull()
            ?.let { page ->
                put(url, page)
                ContentResult.Ready(page)
            }
    }

    private suspend fun <T : Any> load(
        url: String,
        headers: Map<String, String> = emptyMap(),
        parse: (String) -> T
    ): ContentResult<T> {
        cached<T>(url)?.let { return ContentResult.Ready(it) }

        return when (val payload = client.get(url, headers)) {
            is PagePayload.Html -> {
                try {
                    val value = parse(payload.html)
                    put(url, value)
                    ContentResult.Ready(value)
                } catch (e: XenForoParseException) {
                    Log.w(TAG, "Could not parse $url", e)
                    ContentResult.Failed(
                        "Head-Fi's page layout changed. Open this page in the browser to keep reading."
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read $url", e)
                    ContentResult.Failed(
                        "Could not read this page. Open it in the browser to continue."
                    )
                }
            }

            is PagePayload.NeedsVerification -> ContentResult.NeedsVerification(payload.url)
            is PagePayload.Unavailable -> ContentResult.Failed(unavailableMessage(payload))
        }
    }

    /**
     * Turns the network layer's technical reason into something a reader can act
     * on. The raw reason stays in logcat for diagnosis.
     */
    private fun unavailableMessage(payload: PagePayload.Unavailable): String = when {
        payload.statusCode != null ->
            "Head-Fi returned HTTP ${payload.statusCode}. It may be temporarily unavailable."

        payload.reason.startsWith("Network error") ->
            "No connection to Head-Fi. Check your network and try again."

        payload.reason.startsWith("Empty response") ->
            "Head-Fi sent an empty page. Try again, or open it in the browser."

        else -> payload.reason
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> cached(url: String): T? {
        val entry = synchronized(cache) { cache[url] } ?: return null
        if (System.currentTimeMillis() - entry.storedAtMs > CACHE_TTL_MS) return null
        return entry.value as? T
    }

    private fun put(url: String, value: Any) {
        synchronized(cache) {
            cache[url] = CacheEntry(value, System.currentTimeMillis())
        }
    }

    fun invalidate(url: String) {
        synchronized(cache) { cache.remove(url) }
    }

    /** Refresh should bypass the short-lived cache, so drop everything. */
    fun invalidateAll() {
        synchronized(cache) { cache.clear() }
    }

    private companion object {
        const val TAG = "HeadFiContent"
        const val MAX_CACHE_ENTRIES = 24
        const val CACHE_TTL_MS = 60_000L
        val RSS_ACCEPT = mapOf(
            "Accept" to "application/rss+xml, application/xml;q=0.9, */*;q=0.8"
        )
    }
}
