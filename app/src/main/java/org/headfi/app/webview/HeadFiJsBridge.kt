package org.headfi.app.webview

import android.webkit.JavascriptInterface

/**
 * Bridge between the Head-Fi page running inside the WebView and the native
 * Material 3 shell.
 *
 * @param onScrollChanged reports document scroll positions as
 *        `(scrollY, oldScrollY)` so the smart auto-hiding toolbars keep working
 *        even when the WebView view itself does not emit `onScrollChanged`
 *        (fixed layouts, custom scroll containers, zoomed-out pages).
 */
class HeadFiJsBridge(
    private val onSessionUpdate: (
        isLoggedIn: Boolean,
        username: String,
        avatarUrl: String,
        unreadAlerts: Int,
        unreadConversations: Int
    ) -> Unit,
    private val onScrollChanged: (scrollY: Int, oldScrollY: Int) -> Unit
) {
    @JavascriptInterface
    fun onXenForoStateDetected(
        isLoggedIn: Boolean,
        username: String?,
        avatarUrl: String?,
        unreadAlerts: Int,
        unreadConversations: Int
    ) {
        onSessionUpdate(
            isLoggedIn,
            username?.trim().orEmpty(),
            avatarUrl?.trim().orEmpty(),
            unreadAlerts.coerceAtLeast(0),
            unreadConversations.coerceAtLeast(0)
        )
    }

    /**
     * Document scroll position reported from the page itself. Used as a
     * secondary source for the auto-hide toolbar logic so direction detection
     * never silently dies when the native view reports no scroll changes.
     */
    @JavascriptInterface
    fun onPageScrolled(scrollY: Int, oldScrollY: Int) {
        if (scrollY < 0 || oldScrollY < 0) return
        onScrollChanged(scrollY, oldScrollY)
    }
}
