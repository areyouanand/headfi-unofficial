package org.headfi.app.ui.native

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FirstPage
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LastPage
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.headfi.app.data.ContentResult
import org.headfi.app.net.HeadFiUserAgent
import org.headfi.app.net.xenforo.ThreadSummary

/** UI state for one native screen. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>

    /**
     * [refreshing] is true while a background refresh is running with the old
     * content still on screen. [refreshError] is set when that refresh failed;
     * the previously loaded content stays readable behind a small banner.
     */
    data class Ready<T>(
        val value: T,
        val refreshing: Boolean = false,
        val refreshError: String? = null
    ) : Loadable<T>
    data class Verification(val url: String) : Loadable<Nothing>
    data class Error(val message: String) : Loadable<Nothing>
}

/** True while a refresh is running over already-loaded content. */
val Loadable<*>.isRefreshing: Boolean
    get() = (this as? Loadable.Ready<*>)?.refreshing == true

/**
 * Runs [load] once per key (and again when [refreshToken] changes), mapping the
 * repository's result onto [Loadable].
 *
 * A refresh keeps the previous [Loadable.Ready] on screen (flagged as
 * refreshing) instead of resetting to [Loadable.Loading]. That avoids a blank
 * flash and, because the caller also owns its LazyListState, keeps the reader's
 * scroll position.
 */
@Composable
fun <T> rememberContent(
    key: String,
    refreshToken: Int,
    load: suspend () -> ContentResult<T>
): Loadable<T> {
    var state by remember(key) { mutableStateOf<Loadable<T>>(Loadable.Loading) }
    LaunchedEffect(key, refreshToken) {
        val previous = state as? Loadable.Ready
        state = if (previous != null) {
            Loadable.Ready(previous.value, refreshing = true)
        } else {
            Loadable.Loading
        }
        state = when (val result = load()) {
            is ContentResult.Ready -> Loadable.Ready(result.value)
            is ContentResult.NeedsVerification -> Loadable.Verification(result.url)
            is ContentResult.Failed ->
                if (previous != null) {
                    // Keep the readable page rather than replacing it with an
                    // error; the banner explains that the refresh failed.
                    Loadable.Ready(previous.value, refreshError = result.message)
                } else {
                    Loadable.Error(result.message)
                }
        }
    }
    return state
}

/**
 * Pull-to-refresh for a native screen. Material's box works here because its
 * child is a Compose LazyColumn (unlike the WebView, which needed
 * SwipeRefreshLayout — see HeadFiWebView).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NativePullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        content = content
    )
}

/**
 * Compact, scroll-away header. The app deliberately has no AppBar (v1.5.0), so
 * native screens put their title at the top of the scroll container instead of
 * pinning chrome over the content.
 */
@Composable
fun NativeHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onOpenBrowser: (() -> Unit)? = null,
    isWatching: Boolean = false,
    onWatch: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onWatch != null) {
            IconButton(onClick = onWatch) {
                Icon(
                    imageVector = if (isWatching) {
                        Icons.Outlined.NotificationsActive
                    } else {
                        Icons.Outlined.NotificationsNone
                    },
                    contentDescription = if (isWatching) "Watching thread" else "Watch thread",
                    tint = if (isWatching) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        if (onOpenBrowser != null) {
            IconButton(onClick = onOpenBrowser) {
                Icon(Icons.Outlined.Language, contentDescription = "Open in browser")
            }
        }
        if (onRefresh != null) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
        }
    }
}

@Composable
fun LoadingState(label: String = "Loading…", modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    onOpenBrowser: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Couldn't load this page",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry")
            }
            if (onOpenBrowser != null) {
                TextButton(onClick = onOpenBrowser) { Text("Open in browser") }
            }
        }
    }
}

/**
 * Cloudflare turned the request away, or the session has no clearance cookie.
 * Only the WebView can fix this, so the screen hands over explicitly instead of
 * pretending the page is empty.
 */
@Composable
fun VerificationState(
    onVerify: () -> Unit,
    url: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.VerifiedUser,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Verify this session",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Head-Fi needs a browser check before it will show this page. " +
                "Open it once in the browser view and the native screens will work again. " +
                "If the check does not clear there either, use Account & Settings → " +
                "Session health & diagnostics.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onVerify) {
                Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open in browser")
            }
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
        TextButton(
            onClick = {
                val diagnostics = buildString {
                    appendLine("Head-Fi verification needed")
                    appendLine("URL: ${url ?: "unknown"}")
                    appendLine("User-Agent: ${HeadFiUserAgent.mobile(context)}")
                    appendLine("Time: ${java.time.Instant.now()}")
                }
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Head-Fi diagnostics", diagnostics))
            }
        ) {
            Text("Copy diagnostics")
        }
    }
}

/**
 * A refresh failed while old content is still on screen. Shown as a thin banner
 * above the list so the reader keeps what they were reading.
 */
@Composable
fun RefreshErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Couldn't refresh: $message",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun Avatar(url: String?, name: String, size: Dp = 36.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Text(
                text = name.trim().take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** One thread row, shared by forum views, What's New, watched and search. */
@Composable
fun ThreadRow(
    thread: ThreadSummary,
    onClick: () -> Unit,
    showForum: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (thread.prefix != null || thread.isSticky) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (thread.isSticky) LabelPill("PINNED")
                thread.prefix?.let { LabelPill(it) }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (thread.isUnread) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = thread.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (thread.isUnread) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        val meta = buildList {
            thread.author?.let { add(it) }
            if (showForum) thread.forumName?.let { add(it) }
            thread.replyCount?.let { add(if (it == 1) "1 reply" else "$it replies") }
            thread.lastPostTime?.let { add("Last post $it") }
        }
        if (meta.isNotEmpty()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = meta.joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        thread.snippet?.let { snippet ->
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        thickness = 1.dp
    )
}

@Composable
fun LabelPill(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

@Composable
fun PartialFeedNotice(onVerify: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.RssFeed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Showing the latest threads",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Head-Fi's feed is public, so this list skips the browser " +
                        "check. Verify the session for the full, paginated forum.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onVerify) { Text("Verify") }
        }
    }
}

@Composable
fun SectionLabel(text: String) {    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
fun PaginationRow(
    label: String,
    hasFirst: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    hasLast: Boolean,
    onFirst: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLast: () -> Unit,
    pageCount: Int = 1,
    currentPage: Int = 1,
    onJumpToPage: ((Int) -> Unit)? = null
) {
    var showJumpDialog by remember { mutableStateOf(false) }
    var pageText by remember { mutableStateOf("") }

    val submitPage = {
        val page = pageText.toIntOrNull()?.coerceIn(1, pageCount)
        if (page != null && onJumpToPage != null) {
            onJumpToPage(page)
            showJumpDialog = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onFirst, enabled = hasFirst) {
            Icon(Icons.Outlined.FirstPage, contentDescription = "First page")
        }
        TextButton(onClick = onPrevious, enabled = hasPrevious) { Text("Prev") }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (onJumpToPage != null && pageCount > 1) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            modifier = if (onJumpToPage != null && pageCount > 1) {
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        pageText = currentPage.coerceIn(1, pageCount.coerceAtLeast(1)).toString()
                        showJumpDialog = true
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            } else {
                Modifier
            }
        )
        TextButton(onClick = onNext, enabled = hasNext) { Text("Next") }
        IconButton(onClick = onLast, enabled = hasLast) {
            Icon(Icons.Outlined.LastPage, contentDescription = "Last page")
        }
    }

    if (showJumpDialog && onJumpToPage != null) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to page") },
            text = {
                OutlinedTextField(
                    value = pageText,
                    onValueChange = { value -> pageText = value.filter(Char::isDigit).take(5) },
                    label = { Text("Page (1–$pageCount)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(onDone = { submitPage() })
                )
            },
            confirmButton = {
                TextButton(
                    onClick = submitPage,
                    enabled = pageText.toIntOrNull()?.let { it in 1..pageCount } == true
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("Cancel") }
            }
        )
    }
}
