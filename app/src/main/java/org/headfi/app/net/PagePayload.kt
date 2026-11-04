package org.headfi.app.net

import org.headfi.app.net.xenforo.XenForoMarkup

/**
 * The result of one content fetch, classified so callers cannot accidentally
 * treat a Cloudflare challenge as real content.
 *
 * Phase 1 screens are expected to `when` over this: [Html] renders natively,
 * [NeedsVerification] hands control to the WebView, and [Unavailable] shows the
 * offline/retry path the app already has.
 */
sealed interface PagePayload {
    val url: String

    data class Html(
        override val url: String,
        val html: String,
        val meta: XenForoMarkup.Meta
    ) : PagePayload

    /** Turnstile clearance is missing or expired; only a WebView can fix this. */
    data class NeedsVerification(override val url: String) : PagePayload

    data class Unavailable(
        override val url: String,
        val reason: String,
        val statusCode: Int? = null
    ) : PagePayload
}
