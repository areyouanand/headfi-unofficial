package org.headfi.app.ui.native

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.headfi.app.data.ContentRepository

@Composable
fun AlertsScreen(
    destination: NativeDestination.Alerts,
    repository: ContentRepository,
    refreshToken: Int,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenWeb: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val state = rememberContent(destination.url, refreshToken) {
        repository.alerts(destination.url)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            NativeHeader(
                title = "Alerts",
                subtitle = (state as? Loadable.Ready)?.value?.let { page ->
                    val unread = page.alerts.count { it.isUnread }
                    when {
                        unread > 0 -> "$unread unread"
                        page.alerts.isNotEmpty() -> "All caught up"
                        else -> null
                    }
                },
                onBack = if (canGoBack) onBack else null,
                onRefresh = onRefresh,
                onOpenBrowser = { onOpenWeb(destination.url) }
            )
        }

        when (state) {
            is Loadable.Loading -> item { LoadingState("Loading alerts…") }

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
                if (page.alerts.isEmpty()) {
                    item { EmptyState("No alerts yet.") }
                }
                items(page.alerts) { alert ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenUrl(alert.url) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(url = alert.avatarUrl, name = alert.title, size = 32.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (alert.isUnread) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                    Text(
                                        text = alert.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (alert.isUnread) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                alert.snippet?.let { snippet ->
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = snippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                alert.timeAgo?.let { time ->
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = time,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 1.dp
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
                            onFirst = { page.pageNav.firstUrl?.let(onOpenUrl) },
                            onPrevious = { page.pageNav.previousUrl?.let(onOpenUrl) },
                            onNext = { page.pageNav.nextUrl?.let(onOpenUrl) },
                            onLast = { page.pageNav.lastUrl?.let(onOpenUrl) }
                        )
                    }
                }
            }
        }
    }
}
