package org.headfi.app.net.xenforo

/**
 * Detects what a fetched Head-Fi page actually is.
 *
 * The important case is [isChallengePage]. When Cloudflare decides it does not
 * like a request, Head-Fi answers the `/bot/challenge` template with an ordinary
 * `200 OK`. A scraper that only checked the status code would parse the
 * challenge page and report "zero forums", which is indistinguishable from an
 * empty site. Every native screen must treat that as "hand back to the WebView",
 * never as content.
 */
object XenForoMarkup {

    const val TEMPLATE_CHALLENGE = "hfbot_challenge_widget"
    const val TEMPLATE_LOGIN = "login"

    /** The `<html>` element and the first scripts fit comfortably in this. */
    private const val HEAD_SCAN_CHARS = 20_000

    private val TEMPLATE = Regex("""data-template="([^"]*)"""")
    private val XF_VERSION = Regex("""data-xf="([^"]*)"""")
    private val LOGGED_IN = Regex("""data-logged-in="([^"]*)"""")

    /** Cloudflare's own interstitial, as opposed to Head-Fi's challenge template. */
    private val CLOUDFLARE_INTERSTITIAL_MARKERS = listOf(
        "<title>just a moment",
        "cf-chl-",
        "id=\"challenge-form\"",
        "cf_chl_opt"
    )

    data class Meta(
        val template: String?,
        val xfVersion: String?,
        val loggedIn: Boolean
    ) {
        val isLoginPage: Boolean get() = template == TEMPLATE_LOGIN
    }

    fun meta(html: String): Meta {
        val head = html.take(HEAD_SCAN_CHARS)
        return Meta(
            template = TEMPLATE.find(head)?.groupValues?.get(1),
            xfVersion = XF_VERSION.find(head)?.groupValues?.get(1),
            loggedIn = LOGGED_IN.find(head)?.groupValues?.get(1) == "true"
        )
    }

    fun isChallengePage(html: String, url: String): Boolean {
        if (url.contains(CHALLENGE_PATH)) return true

        val head = html.take(HEAD_SCAN_CHARS).lowercase()
        if (head.contains(TEMPLATE_CHALLENGE)) return true
        return CLOUDFLARE_INTERSTITIAL_MARKERS.any { head.contains(it) }
    }

    private const val CHALLENGE_PATH = "/bot/challenge"
}
