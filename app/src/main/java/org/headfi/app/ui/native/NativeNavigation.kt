package org.headfi.app.ui.native

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import org.headfi.app.net.xenforo.XenForoUrls

/**
 * A screen the app renders natively. Anything not listed here stays in the
 * WebView, which is why the WebView is still shipped: it handles sign-in,
 * Cloudflare challenges, replies, Classifieds and Showcase.
 */
sealed interface NativeDestination {
    val url: String

    data class Home(
        override val url: String = XenForoUrls.BASE + "/"
    ) : NativeDestination

    data class Forums(
        override val url: String = "${XenForoUrls.BASE}/forums/"
    ) : NativeDestination

    /** A forum view, "What's New", watched threads or search results. */
    data class ThreadList(
        override val url: String,
        val title: String = ""
    ) : NativeDestination

    data class Thread(
        override val url: String,
        val title: String = ""
    ) : NativeDestination

    data class Alerts(
        override val url: String = "${XenForoUrls.BASE}/account/alerts"
    ) : NativeDestination
}

/**
 * Maps a Head-Fi URL onto a native screen, or returns `null` when the page needs
 * the WebView.
 *
 * Deliberately conservative: forms, editors and media-heavy sections are left
 * to the browser rather than half-supported natively.
 */
fun nativeDestinationFor(url: String): NativeDestination? {
    val path = XenForoUrls.pathOf(url)

    // Action URLs share the thread path but are forms, not reading views.
    if (path.contains("/reply") || path.contains("/post-thread") || path.contains("/edit")) {
        return null
    }

    return when {
        path.isEmpty() || path == "/" ->
            NativeDestination.Home(url)

        path.startsWith("/account/alerts") ->
            NativeDestination.Alerts(url)

        XenForoUrls.isForumsIndex(url) ->
            NativeDestination.Forums(url)

        XenForoUrls.isThread(url) ->
            NativeDestination.Thread(url)

        XenForoUrls.isForum(url) ->
            NativeDestination.ThreadList(url)

        path.startsWith("/whats-new/posts") ->
            NativeDestination.ThreadList(url, "What's New")

        path.startsWith("/watched/threads") ->
            NativeDestination.ThreadList(url, "Watched threads")

        path.startsWith("/search/") ->
            NativeDestination.ThreadList(url, "Search results")

        // Search results link straight to a post; XenForo redirects that to its
        // thread, which the native reader can parse. Keeps search useful without
        // needing the thread URL up front.
        path.startsWith("/posts/") -> NativeDestination.Thread(url, "Thread")

        else -> null
    }
}

/**
 * Back/forward stack for native screens. Mirrors the WebView's own history so
 * the bottom bar's Back and Forward buttons behave the same in both modes.
 */
@Stable
class NativeNavigator(initial: NativeDestination = NativeDestination.Home()) {

    private val entries = mutableStateListOf(initial)
    private var index by mutableIntStateOf(0)

    val current: NativeDestination get() = entries[index]

    val canGoBack: Boolean get() = index > 0

    val canGoForward: Boolean get() = index < entries.lastIndex

    fun open(destination: NativeDestination) {
        if (destination == current) return
        while (entries.lastIndex > index) {
            entries.removeAt(entries.lastIndex)
        }
        entries.add(destination)
        index = entries.lastIndex
    }

    /** Replaces the whole stack, e.g. when a bottom-bar section is tapped. */
    fun reset(destination: NativeDestination) {
        if (entries.size == 1 && current == destination) return
        entries.clear()
        entries.add(destination)
        index = 0
    }

    fun back(): Boolean {
        if (!canGoBack) return false
        index -= 1
        return true
    }

    fun forward(): Boolean {
        if (!canGoForward) return false
        index += 1
        return true
    }

    /** Returns false when the URL is not a native screen. */
    fun openUrl(url: String): Boolean {
        val destination = nativeDestinationFor(url) ?: return false
        open(destination)
        return true
    }
}
