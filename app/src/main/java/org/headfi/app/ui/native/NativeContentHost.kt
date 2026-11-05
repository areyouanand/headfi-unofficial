package org.headfi.app.ui.native

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.headfi.app.data.ContentRepository
import org.headfi.app.model.UserSessionState
import org.headfi.app.net.xenforo.ThreadSummary

/**
 * Dispatches the native navigation stack to the matching screen.
 *
 * Only reads are native. Anything the stack does not recognise is handed to
 * [onOpenWeb], so the WebView remains the fallback for sign-in, challenges,
 * replies, Classifieds and Showcase.
 */
@Composable
fun NativeContentHost(
    navigator: NativeNavigator,
    repository: ContentRepository,
    refreshToken: Int,
    textScale: Float,
    loadImages: Boolean,
    session: UserSessionState,
    onRefresh: () -> Unit,
    onOpenWeb: (String) -> Unit,
    onOpenExternal: (String) -> Unit
) {
    val canGoBack = navigator.canGoBack
    val onBack: () -> Unit = { navigator.back() }

    // One scroll position per destination, held above the screen composables so
    // Back from a thread returns the list to where the reader left it.
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    val listStateFor: (String) -> LazyListState = { key ->
        listStates.getOrPut(key) { LazyListState() }
    }

    val openDestination: (NativeDestination) -> Unit = { navigator.open(it) }
    val openUrl: (String) -> Unit = { url ->
        if (!navigator.openUrl(url)) onOpenWeb(url)
    }
    val openThread: (ThreadSummary) -> Unit = { thread ->
        // An unread thread opens at the first unread post, like XenForo itself.
        val target = thread.unreadUrl ?: thread.url
        navigator.open(NativeDestination.Thread(target, thread.title))
    }

    when (val destination = navigator.current) {
        is NativeDestination.Home -> HomeScreen(
            repository = repository,
            refreshToken = refreshToken,
            session = session,
            listState = listStateFor(destination.url),
            onRefresh = onRefresh,
            onOpenDestination = openDestination,
            onOpenWeb = onOpenWeb,
            onOpenUrl = openUrl,
            onOpenThread = openThread
        )

        is NativeDestination.Forums -> ForumsScreen(
            repository = repository,
            refreshToken = refreshToken,
            canGoBack = canGoBack,
            listState = listStateFor(destination.url),
            onBack = onBack,
            onRefresh = onRefresh,
            onOpenWeb = onOpenWeb,
            onOpenForum = { forum ->
                openDestination(NativeDestination.ThreadList(forum.url, forum.title))
            }
        )

        is NativeDestination.ThreadList -> ThreadListScreen(
            destination = destination,
            repository = repository,
            refreshToken = refreshToken,
            canGoBack = canGoBack,
            listState = listStateFor(destination.url),
            onBack = onBack,
            onRefresh = onRefresh,
            onOpenWeb = onOpenWeb,
            onOpenDestination = openDestination,
            onOpenThread = openThread
        )

        is NativeDestination.Thread -> ThreadScreen(
            destination = destination,
            repository = repository,
            refreshToken = refreshToken,
            canGoBack = canGoBack,
            textScale = textScale,
            loadImages = loadImages,
            listState = listStateFor(destination.url),
            onBack = onBack,
            onRefresh = onRefresh,
            onOpenWeb = onOpenWeb,
            onOpenDestination = openDestination,
            onOpenExternal = onOpenExternal
        )

        is NativeDestination.Alerts -> AlertsScreen(
            destination = destination,
            repository = repository,
            refreshToken = refreshToken,
            canGoBack = canGoBack,
            listState = listStateFor(destination.url),
            onBack = onBack,
            onRefresh = onRefresh,
            onOpenWeb = onOpenWeb,
            onOpenUrl = openUrl
        )
    }
}
