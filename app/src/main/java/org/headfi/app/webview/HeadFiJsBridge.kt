package org.headfi.app.webview

import android.webkit.JavascriptInterface

class HeadFiJsBridge(
    private val onSessionUpdate: (
        isLoggedIn: Boolean,
        username: String,
        avatarUrl: String,
        unreadAlerts: Int,
        unreadConversations: Int
    ) -> Unit
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
}
