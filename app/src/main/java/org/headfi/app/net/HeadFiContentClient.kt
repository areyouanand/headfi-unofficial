package org.headfi.app.net

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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
            newBuilder(url, headers).get().build()
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

    private suspend fun newBuilder(url: String, headers: Map<String, String>) =
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

    /**
     * Performs a XenForo form POST (currently only reactions).
     *
     * Returns true only when the server accepted it: a 2xx challenge page is a
     * rejection, and so is being bounced to the login page because the session
     * expired. Callers fall back to the WebView instead of pretending it worked.
     */
    suspend fun postForm(
        url: String,
        fields: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply {
            fields.forEach { (name, value) -> add(name, value) }
        }.build()

        val request = try {
            newBuilder(
                url,
                mapOf(
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            ).post(body).build()
        } catch (e: IllegalArgumentException) {
            return@withContext false
        }

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val finalUrl = response.request.url.toString()
                val challenge = XenForoMarkup.isChallengePage(text, finalUrl)
                val login = XenForoMarkup.meta(text).isLoginPage
                val errors = text.contains("\"errors\"")
                val accepted = response.isSuccessful && !challenge && !login && !errors

                // One line per action: the only way to diagnose a rejected POST on
                // a device (see HANDOFF: no automated UI inspection on the target).
                Log.i(
                    TAG,
                    "POST $url -> ${response.request.url.encodedPath} code=${response.code} " +
                        "challenge=$challenge login=$login errors=$errors accepted=$accepted"
                )
                accepted
            }
        } catch (e: IOException) {
            Log.w(TAG, "POST failed: ${e.message}")
            false
        }
    }

    private companion object {
        const val TAG = "HeadFiReact"
        const val URL_FORUMS_INDEX = "https://www.head-fi.org/forums/"
        const val ACCEPT_HTML =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
    }
}
