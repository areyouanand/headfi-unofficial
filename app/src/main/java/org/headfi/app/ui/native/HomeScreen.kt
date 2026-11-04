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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.headfi.app.data.ContentRepository
import org.headfi.app.model.QuickDestination
import org.headfi.app.model.UserSessionState
import org.headfi.app.net.xenforo.ThreadSummary
import org.headfi.app.net.xenforo.XenForoUrls

/**
 * Native landing screen. Head-Fi's real home page is an editorial portal that
 * would need a bespoke parser, so Home is a hub built from data the app already
 * has: unread counts, quick destinations, and the latest activity list.
 */
@Composable
fun HomeScreen(
    repository: ContentRepository,
    refreshToken: Int,
    session: UserSessionState,
    onRefresh: () -> Unit,
    onOpenDestination: (NativeDestination) -> Unit,
    onOpenWeb: (String) -> Unit,
    onOpenThread: (ThreadSummary) -> Unit
) {
    val latest = rememberContent(XenForoUrls.LATEST_THREADS_FEED, refreshToken) {
        repository.latestThreads()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            NativeHeader(
                title = "Head-Fi",
                subtitle = if (session.isSignedIn) {
                    "Signed in as ${session.username.ifBlank { "your account" }}"
                } else {
                    "Not signed in"
                },
                onRefresh = onRefresh,
                onOpenBrowser = { onOpenWeb("https://www.head-fi.org/") }
            )
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!session.isSignedIn) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sign in to Head-Fi",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Unread badges and notifications need a session",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { onOpenWeb(LOGIN_URL) }) {
                                Text("Sign in")
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction(
                        icon = Icons.Outlined.Explore,
                        title = "New posts",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onOpenDestination(
                                NativeDestination.ThreadList(WHATS_NEW_URL, "What's New")
                            )
                        }
                    )
                    QuickAction(
                        icon = Icons.Outlined.Forum,
                        title = "Forums",
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenDestination(NativeDestination.Forums()) }
                    )
                }
                // Member destinations are hidden while signed out, matching the
                // bottom bar: tapping them would only bounce to the login page.
                if (session.isSignedIn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickAction(
                            icon = Icons.Outlined.Notifications,
                            title = "Alerts",
                            badge = session.unreadAlerts,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenDestination(NativeDestination.Alerts()) }
                        )
                        QuickAction(
                            icon = Icons.Outlined.Mail,
                            title = "Messages",
                            badge = session.unreadConversations,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenWeb(QuickDestination.CONVERSATIONS.url) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickAction(
                            icon = Icons.Outlined.Bookmark,
                            title = "Watched",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onOpenDestination(
                                    NativeDestination.ThreadList(
                                        QuickDestination.WATCHED_THREADS.url,
                                        "Watched threads"
                                    )
                                )
                            }
                        )
                        QuickAction(
                            icon = Icons.Outlined.Bookmark,
                            title = "Bookmarks",
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenWeb(QuickDestination.BOOKMARKS.url) }
                        )
                    }
                }
            }
        }

        item { SectionLabel("Latest activity") }

        when (latest) {
            is Loadable.Loading -> item { LoadingState("Loading latest activity…") }

            is Loadable.Error -> item {
                ErrorState(
                    message = latest.message,
                    onRetry = onRefresh,
                    onOpenBrowser = { onOpenWeb(WHATS_NEW_URL) }
                )
            }

            is Loadable.Verification -> item {
                VerificationState(onVerify = { onOpenWeb(latest.url) })
            }

            is Loadable.Ready -> {
                items(latest.value.threads, key = { it.url }) { thread ->
                    ThreadRow(
                        thread = thread,
                        showForum = true,
                        onClick = { onOpenThread(thread) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                if (badge > 0) {
                    Badge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(14.dp)
                            .background(MaterialTheme.colorScheme.error, RoundedCornerShape(50))
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                if (badge > 0) {
                    Text(
                        text = "$badge unread",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private const val WHATS_NEW_URL = "https://www.head-fi.org/whats-new/posts/"
private const val LOGIN_URL = "https://www.head-fi.org/login/"
