package org.headfi.app.net

import android.content.Context
import android.os.Looper
import android.webkit.WebSettings

/**
 * Single source of truth for the User-Agent sent by both the WebView and
 * [HeadFiContentClient]'s native requests.
 *
 * This matters more here than in a normal app: Cloudflare Turnstile issues
 * `cf_clearance` against the browser fingerprint that solved the challenge, and
 * that fingerprint includes the User-Agent. The cookie is minted by the WebView
 * and then replayed by OkHttp, so if the two send different UA strings the
 * clearance cookie can be rejected and every "native" request silently lands on
 * the bot-challenge page instead of real content.
 *
 * Therefore OkHttp must send *exactly* the string the WebView sent, which is why
 * the WebView's UA is captured once (on the main thread) and reused everywhere
 * rather than being rebuilt from a hard-coded Chrome version.
 */
object HeadFiUserAgent {

    /**
     * Used until [mobile] has been able to capture the real WebView UA — for
     * example in a WorkManager run where the WebView has never been created.
     */
    const val MOBILE_FALLBACK =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Mobile Safari/537.36"

    const val DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Safari/537.36"

    @Volatile
    private var cachedMobile: String? = null

    /**
     * Removes the tokens that give a WebView away (`; wv` and the `Version/X.Y`
     * suffix) so Cloudflare treats the client as plain Chrome Mobile.
     */
    fun scrub(userAgent: String): String = userAgent
        .replace("; wv", "")
        .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")

    /**
     * The UA the WebView uses. Captured once and cached for the process.
     *
     * [WebSettings.getDefaultUserAgent] touches the WebView provider, which is
     * only safe on the main thread, so background callers get
     * [MOBILE_FALLBACK] until a main-thread caller has populated the cache.
     */
    fun mobile(context: Context): String {
        cachedMobile?.let { return it }
        if (Looper.myLooper() != Looper.getMainLooper()) return MOBILE_FALLBACK
        return runCatching { scrub(WebSettings.getDefaultUserAgent(context)) }
            .getOrDefault(MOBILE_FALLBACK)
            .also { cachedMobile = it }
    }

    /** Call from the main thread early so workers inherit the exact same UA. */
    fun prefetch(context: Context) {
        mobile(context)
    }
}
