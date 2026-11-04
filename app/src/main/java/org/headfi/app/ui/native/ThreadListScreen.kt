package org.headfi.app.ui.native

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.headfi.app.data.ContentRepository
import org.headfi.app.net.xenforo.ThreadSummary

@Composable
fun ThreadListScreen(
    destination: NativeDestination.ThreadList,
    repository: ContentRepository,
    refreshToken: Int,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenWeb: (String) -> Unit,
    onOpenDestination: (NativeDestination) -> Unit,
    onOpenThread: (ThreadSummary) -> Unit
) {
    val state = rememberContent(destination.url, refreshToken) {
        repository.threadList(destination.url)
    }
    val ready = state as? Loadable.Ready
    val title = destination.title.ifBlank {
        ready?.value?.title ?: "Threads"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            NativeHeader(
                title = title,
                subtitle = ready?.value?.let { page ->
                    val count = page.threads.size
                    if (page.isPartial) {
                        "$count latest threads · summary"
                    } else {
                        "$count thread${if (count == 1) "" else "s"} · page ${page.pageNav.currentPage}"
                    }
                },
                onBack = if (canGoBack) onBack else null,
                onRefresh = onRefresh,
                onOpenBrowser = { onOpenWeb(destination.url) }
            )
        }

        if (ready?.value?.isPartial == true) {
            item {
                PartialFeedNotice(onVerify = { onOpenWeb(destination.url) })
            }
        }

        when (state) {
            is Loadable.Loading -> item { LoadingState("Loading threads…") }

            is Loadable.Error -> item {
                ErrorState(
                    message = state.message,
                    onRetry = onRefresh,
                    onOpenBrowser = { onOpenWeb(destination.url) }
                )
            }

            is Loadable.Verification -> item {
                VerificationState(onVerify = { onOpenWeb(state.url) })
            }

            is Loadable.Ready -> {
                val page = state.value
                if (page.threads.isEmpty()) {
                    item { EmptyState("No threads on this page.") }
                }
                items(page.threads, key = { it.url }) { thread ->
                    ThreadRow(
                        thread = thread,
                        showForum = destination.title.isBlank(),
                        onClick = { onOpenThread(thread) }
                    )
                }
                if (page.pageNav.hasPrevious || page.pageNav.hasNext ||
                    page.pageNav.hasFirst || page.pageNav.hasLast
                ) {
                    item {
                        PaginationRow(
                            label = "Page ${page.pageNav.currentPage} of ${page.pageNav.pageCount}",
                            hasFirst = page.pageNav.hasFirst,
                            hasPrevious = page.pageNav.hasPrevious,
                            hasNext = page.pageNav.hasNext,
                            hasLast = page.pageNav.hasLast,
                            onFirst = {
                                page.pageNav.firstUrl?.let {
                                    onOpenDestination(NativeDestination.ThreadList(it, destination.title))
                                }
                            },
                            onPrevious = {
                                page.pageNav.previousUrl?.let {
                                    onOpenDestination(NativeDestination.ThreadList(it, destination.title))
                                }
                            },
                            onNext = {
                                page.pageNav.nextUrl?.let {
                                    onOpenDestination(NativeDestination.ThreadList(it, destination.title))
                                }
                            },
                            onLast = {
                                page.pageNav.lastUrl?.let {
                                    onOpenDestination(NativeDestination.ThreadList(it, destination.title))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
