package org.headfi.app.ui.native

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.headfi.app.data.ContentRepository
import org.headfi.app.model.QuickDestination
import org.headfi.app.model.UserSessionState
import org.headfi.app.net.xenforo.HomeFeedItem
import org.headfi.app.net.xenforo.HomeFeedKind
import org.headfi.app.net.xenforo.ThreadSummary
import org.headfi.app.net.xenforo.XenForoUrls

/**
 * Native landing screen: quick destinations, the curated cards from Head-Fi's
 * real front page (with their images), and the latest activity list.
 */
@Composable
fun HomeScreen(
    repository: ContentRepository,
    refreshToken: Int,
    session: UserSessionState,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onOpenDestination: (NativeDestination) -> Unit,
    onOpenWeb: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenThread: (ThreadSummary) -> Unit
) {
    val latest = rememberContent(XenForoUrls.LATEST_THREADS_FEED, refreshToken) {
        repository.latestThreads()
    }
    val frontPage = rememberContent(XenForoUrls.HOME, refreshToken) {
        repository.homeFeed()
    }

    NativePullToRefresh(
        isRefreshing = latest.isRefreshing || frontPage.isRefreshing,
        onRefresh = onRefresh
    ) {
    LazyColumn(
        state = listState,
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

        (latest as? Loadable.Ready)?.refreshError
            ?.let { message -> item { RefreshErrorBanner(message, onRetry = onRefresh) } }

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

        item { SectionLabel("From Head-Fi") }

        when (frontPage) {
            is Loadable.Loading -> item { LoadingState("Loading Head-Fi's front page…") }

            is Loadable.Error -> item {
                FrontPageNotice(
                    message = "Head-Fi's front page couldn't load.",
                    actionLabel = "Open",
                    onAction = { onOpenWeb(XenForoUrls.HOME) }
                )
            }

            is Loadable.Verification -> item {
                FrontPageNotice(
                    message = "Verify this session to see Head-Fi's front page.",
                    actionLabel = "Verify",
                    onAction = { onOpenWeb(frontPage.url) }
                )
            }

            is Loadable.Ready -> {
                val cards = frontPage.value
                cards.firstOrNull()?.let { hero ->
                    item(key = "front-hero") {
                        FrontPageHero(item = hero, onClick = { onOpenUrl(hero.url) })
                    }
                }
                items(cards.drop(1), key = { "front-${it.url}" }) { item ->
                    FrontPageRow(item = item, onClick = { onOpenUrl(item.url) })
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
                VerificationState(
                    onVerify = { onOpenWeb(latest.url) },
                    url = latest.url,
                    onRetry = onRefresh
                )
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
}

@Composable
private fun FrontPageHero(item: HomeFeedItem, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            item.imageUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.18f),
                            0.30f to Color.Transparent,
                            0.55f to Color.Black.copy(alpha = 0.45f),
                            1f to Color.Black.copy(alpha = 0.95f)
                        )
                    )
            )
            FrontPageKindPill(
                kind = item.kind,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.excerpt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.excerpt,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FrontPageRow(item: HomeFeedItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(104.dp)
                .height(78.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            item.imageUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = frontPageKindLabel(item.kind),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.excerpt.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.excerpt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        thickness = 1.dp
    )
}

@Composable
private fun FrontPageKindPill(kind: HomeFeedKind, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
        modifier = modifier
    ) {
        Text(
            text = frontPageKindLabel(kind),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun FrontPageNotice(
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun frontPageKindLabel(kind: HomeFeedKind): String = when (kind) {
    HomeFeedKind.THREAD -> "Forum thread"
    HomeFeedKind.REVIEW -> "Review"
    HomeFeedKind.SHOWCASE -> "Head Gear"
    HomeFeedKind.ARTICLE -> "Article"
    HomeFeedKind.OTHER -> "Head-Fi"
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
