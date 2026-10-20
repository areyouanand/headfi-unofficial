package org.headfi.app.webview

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlockEngine {

    private val blockedDomains = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagservices.com",
        "adnxs.com",
        "rubiconproject.com",
        "pubmatic.com",
        "criteo.com",
        "criteo.net",
        "openx.net",
        "amazon-adsystem.com",
        "casalemedia.com",
        "indexww.com",
        "3lift.com",
        "lijit.com",
        "sovrn.com",
        "viglink.com",
        "skimresources.com",
        "taboola.com",
        "outbrain.com",
        "moatads.com",
        "adsafeprotected.com",
        "scorecardresearch.com",
        "quantserve.com",
        "smartadserver.com",
        "advertising.com",
        "yieldmo.com",
        "teads.tv",
        "sharethrough.com",
        "gumgum.com",
        "bidswitch.net",
        "contextweb.com",
        "DistrictM.io",
        "media.net"
    )

    // Never block Cloudflare Turnstile, Head-Fi CDN, or FontAwesome
    private val allowListDomains = setOf(
        "head-fi.org",
        "cdn.head-fi.org",
        "cdn-dev.head-fi.org",
        "challenges.cloudflare.com",
        "cloudflare.com",
        "cdnjs.cloudflare.com",
        "googleapis.com",
        "gstatic.com"
    )

    fun shouldBlockRequest(request: WebResourceRequest, adBlockerEnabled: Boolean): Boolean {
        if (!adBlockerEnabled) return false
        val host = request.url?.host?.lowercase() ?: return false

        if (allowListDomains.any { host == it || host.endsWith(".$it") }) {
            return false
        }

        return blockedDomains.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }

    fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
