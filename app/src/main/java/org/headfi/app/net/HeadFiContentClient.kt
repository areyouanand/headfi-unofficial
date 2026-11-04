package org.headfi.app.net

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.headfi.app.data.SessionPreferencesRepository
import org.headfi.app.net.xenforo.ForumsIndexParser
import org.headfi.app.net.xenforo.XenForoMarkup
import java.io.IOException

/**
 * Fetches Head-Fi pages over HTTP using the WebView's cookies, and classifies
 * the response before any parser sees it.
 *
 * Not a replacement for the WebView yet — this is the read path that Phase 1
 * screens will use, while forms, the reply editor and sign-in stay in the
 * WebView. Nothing here renders UI.
 */
class HeadFiContentClient(
    context: Context,
    private val repository: SessionPreferencesRepository
) {

    private val appContext = context.applicationContext
    private val client = HeadFiHttpClient.get(appContext)

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): PagePayload = withContext(Dispatchers.IO) {
        val request = try {
            buildRequest(url, headers)
        } catch (e: IllegalArgumentException) {
            return@withContext PagePayload.Unavailable(url, "Bad URL: ${e.message}")
        }

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val finalUrl = response.request.url.toString()

                when {
                    !response.isSuccessful -> PagePayload.Unavailable(
                        url = finalUrl,
                        reason = "HTTP ${response.code}",
                        statusCode = response.code
                    )

                    XenForoMarkup.isChallengePage(body, finalUrl) ->
                        PagePayload.NeedsVerification(finalUrl)

                    body.isBlank() -> PagePayload.Unavailable(finalUrl, "Empty response body")

                    else -> PagePayload.Html(
                        url = finalUrl,
                        html = body,
                        meta = XenForoMarkup.meta(body)
                    )
                }
            }
        } catch (e: IOException) {
            PagePayload.Unavailable(url, "Network error: ${e.message}")
        }
    }

    suspend fun forumsIndex(): PagePayload = get(URL_FORUMS_INDEX)

    /**
     * One-shot diagnostic used by the debug build at startup so the content path
     * can be checked on a real device via logcat, without any UI (automated UI
     * inspection is unavailable on the target phone — see HANDOFF.md).
     */
    suspend fun probe(): String = when (val payload = forumsIndex()) {
        is PagePayload.Html -> {
            val parsed = runCatching { ForumsIndexParser.parse(payload.html) }
                .fold(
                    onSuccess = { categories ->
                        "parsed ${categories.size} categories / " +
                            "${categories.sumOf { it.forums.size }} forums"
                    },
                    onFailure = { "parser failed: ${it.message}" }
                )
            "GET /forums/ -> HTML (xf=${payload.meta.xfVersion}, " +
                "loggedIn=${payload.meta.loggedIn}) $parsed"
        }

        is PagePayload.NeedsVerification ->
            "GET /forums/ -> challenge page; open the WebView first to mint cf_clearance"

        is PagePayload.Unavailable ->
            "GET /forums/ -> unavailable: ${payload.reason}"
    }

    private suspend fun buildRequest(url: String, headers: Map<String, String>) =
        okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", HeadFiUserAgent.mobile(appContext))
            .header("Accept", ACCEPT_HTML)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Upgrade-Insecure-Requests", "1")
            .apply {
                headers.forEach { (name, value) -> header(name, value) }

                // The cookie jar covers the normal case. On a cold boot the
                // cookie database can be trimmed by the OS before the WebView has
                // run, so fall back to the DataStore vault the app already keeps.
                val live = runCatching {
                    CookieManager.getInstance().getCookie(url)
                }.getOrNull()
                if (live.isNullOrBlank()) {
                    repository.cookieHeader()?.let { header("Cookie", it) }
                }
            }
            .get()
            .build()

    private companion object {
        const val URL_FORUMS_INDEX = "https://www.head-fi.org/forums/"
        const val ACCEPT_HTML =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
    }
}
