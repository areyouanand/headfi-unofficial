package org.headfi.app.net

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Bridges OkHttp onto Android's WebView [CookieManager].
 *
 * This is the whole point of the native content layer: the WebView is the only
 * component that can solve Cloudflare Turnstile, so it mints `cf_clearance` and
 * the XenForo login cookies. Routing OkHttp through the same store means native
 * requests are already "logged in" and challenge-cleared, with no second login
 * flow and no duplicated cookie vault.
 *
 * Writes back into [CookieManager] as well, so a refreshed `xf_session` from a
 * native request keeps the WebView session alive too.
 */
class WebViewCookieJar(
    private val cookieManager: CookieManager = CookieManager.getInstance()
) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        // getCookie() returns only `name=value` pairs, with no attributes, so the
        // request URL supplies the defaults Cookie.parse() needs.
        val header = runCatching { cookieManager.getCookie(url.toString()) }.getOrNull()
            ?: return emptyList()

        return header.split(';').mapNotNull { pair ->
            val trimmed = pair.trim()
            if (trimmed.isEmpty()) null else Cookie.parse(url, trimmed)
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val target = url.toString()
        runCatching {
            cookies.forEach { cookieManager.setCookie(target, it.toString()) }
            cookieManager.flush()
        }
    }
}
