package org.headfi.app.ui.native

import androidx.compose.foundation.background
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.headfi.app.data.ContentRepository
import org.headfi.app.net.xenforo.ForumPost
import org.headfi.app.net.xenforo.PostBlock
import org.headfi.app.net.xenforo.RichText
import org.headfi.app.net.xenforo.TextSpan
import org.headfi.app.net.xenforo.XenForoUrls
import org.headfi.app.util.DownloadAndShareHelper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ThreadScreen(
    destination: NativeDestination.Thread,
    repository: ContentRepository,
    refreshToken: Int,
    canGoBack: Boolean,
    textScale: Float,
    loadImages: Boolean,
    listState: LazyListState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenWeb: (String) -> Unit,
    onOpenDestination: (NativeDestination) -> Unit,
    onOpenExternal: (String) -> Unit
) {
    val state = rememberContent(destination.url, refreshToken) {
        repository.threadPosts(destination.url)
    }
    val ready = state as? Loadable.Ready
    val title = ready?.value?.title ?: destination.title.ifBlank { "Thread" }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reactingPostId by remember { mutableStateOf<Int?>(null) }
    // Optimistic reaction state: updated in place instead of reloading the page,
    // which would throw away the reader's scroll position. The server value wins
    // again on the next refresh.
    var reactedOverrides by remember(destination.url) {
        mutableStateOf<Map<Int, Boolean>>(emptyMap())
    }
    val csrfToken = ready?.value?.csrfToken

    var showWatchDialog by remember { mutableStateOf(false) }
    var watchBusy by remember { mutableStateOf(false) }
    var viewerImageUrl by remember { mutableStateOf<String?>(null) }
    var watchingOverride by remember(destination.url) { mutableStateOf<Boolean?>(null) }
    val isWatching = watchingOverride ?: ready?.value?.isWatching ?: false

    val performWatch: (Boolean) -> Unit = { email ->
        val csrf = csrfToken
        if (csrf == null) {
            showWatchDialog = false
            onOpenWeb(destination.url)
        } else {
            val watchUrl = ready?.value?.watchUrl ?: XenForoUrls.watchUrl(destination.url)
            scope.launch {
                watchBusy = true
                val accepted = repository.watchThread(
                    watchUrl = watchUrl,
                    csrfToken = csrf,
                    emailSubscribe = email
                )
                watchBusy = false
                showWatchDialog = false
                if (accepted) {
                    watchingOverride = true
                    repository.invalidateAll()
                } else {
                    onOpenWeb(destination.url)
                }
            }
        }
    }

    val performUnwatch: () -> Unit = {
        val csrf = csrfToken
        if (csrf == null) {
            showWatchDialog = false
            onOpenWeb(destination.url)
        } else {
            val unwatchUrl = ready?.value?.unwatchUrl ?: XenForoUrls.unwatchUrl(destination.url)
            scope.launch {
                watchBusy = true
                val accepted = repository.unwatchThread(unwatchUrl, csrf)
                watchBusy = false
                showWatchDialog = false
                if (accepted) {
                    watchingOverride = false
                    repository.invalidateAll()
                } else {
                    onOpenWeb(destination.url)
                }
            }
        }
    }

    // Like/Quote/Reply/Share: only reading was native before v1.6.0-beta.2.
    val reactPost: (ForumPost) -> Unit = { post ->
        val postId = post.postId
        // Head-Fi's live theme does not always render the reaction anchor, so
        // fall back to XenForo's canonical /posts/<id>/react route.
        val reactionUrl = post.reactionUrl ?: postId?.let(XenForoUrls::reactionUrl)
        val permalink = post.permalink ?: postId?.let(XenForoUrls::postUrl) ?: destination.url
        if (postId == null || reactionUrl == null || csrfToken == null) {
            // No reaction link (signed out) or no CSRF token: let the site do it.
            Log.i(
                "HeadFiReact",
                "fallback: postId=$postId reactionUrl=$reactionUrl csrf=${csrfToken != null} " +
                    "post=$permalink"
            )
            onOpenWeb(permalink)
        } else if (reactingPostId == null) {
            val wasReacted = reactedOverrides[postId] ?: post.hasReacted
            scope.launch {
                reactingPostId = postId
                val accepted = repository.react(
                    reactionUrl = reactionUrl,
                    csrfToken = csrfToken,
                    reactionId = post.reactionId ?: DEFAULT_REACTION_ID
                )
                reactingPostId = null
                if (accepted) {
                    reactedOverrides = reactedOverrides + (postId to !wasReacted)
                } else {
                    onOpenWeb(permalink)
                }
            }
        }
    }

    val quotePost: (ForumPost) -> Unit = { post ->
        val fallback = XenForoUrls.replyUrl(destination.url)
        onOpenWeb(post.quoteUrl ?: post.postId?.let { "$fallback?quote=$it" } ?: fallback)
    }

    val replyPost: (ForumPost) -> Unit = {
        onOpenWeb(XenForoUrls.replyUrl(destination.url))
    }

    val sharePost: (ForumPost) -> Unit = { post ->
        DownloadAndShareHelper.sharePage(
            context = context,
            title = post.author,
            url = post.permalink
                ?: post.postId?.let(XenForoUrls::postUrl)
                ?: destination.url
        )
    }

    NativePullToRefresh(isRefreshing = state.isRefreshing, onRefresh = onRefresh) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            NativeHeader(
                title = title,
                subtitle = ready?.value?.pageNav?.let { nav ->
                    "Page ${nav.currentPage} of ${nav.pageCount}"
                },
                onBack = if (canGoBack) onBack else null,
                onRefresh = onRefresh,
                onOpenBrowser = { onOpenWeb(destination.url) },
                isWatching = isWatching,
                onWatch = { showWatchDialog = true }
            )
        }

        ready?.refreshError?.let { message ->
            item { RefreshErrorBanner(message, onRetry = onRefresh) }
        }

        when (state) {
            is Loadable.Loading -> item { LoadingState("Loading thread…") }

            is Loadable.Error -> item {
                ErrorState(
                    message = state.message,
                    onRetry = onRefresh,
                    onOpenBrowser = { onOpenWeb(destination.url) }
                )
            }

            is Loadable.Verification -> item {
                VerificationState(
                    onVerify = { onOpenWeb(state.url) },
                    url = state.url,
                    onRetry = onRefresh
                )
            }

            is Loadable.Ready -> {
                val page = state.value
                items(page.posts) { post ->
                    val isReacted = post.postId
                        ?.let { reactedOverrides[it] }
                        ?: post.hasReacted
                    // Keep the visible count in step with the optimistic toggle.
                    val reactionCount = ((post.reactionCount ?: 0) + when {
                        isReacted && !post.hasReacted -> 1
                        !isReacted && post.hasReacted -> -1
                        else -> 0
                    }).coerceAtLeast(0)
                    PostCard(
                        post = post,
                        textScale = textScale,
                        loadImages = loadImages,
                        isReacted = isReacted,
                        isReacting = reactingPostId == post.postId,
                        reactionCount = reactionCount,
                        onReact = { reactPost(post) },
                        onQuote = { quotePost(post) },
                        onReply = { replyPost(post) },
                        onShare = { sharePost(post) },
                        onLinkClick = { url ->
                            when {
                                onNavigateLink(url, onOpenDestination) -> Unit
                                url.startsWith(XenForoUrls.BASE) -> onOpenWeb(url)
                                else -> onOpenExternal(url)
                            }
                        },
                        onImageClick = { url -> viewerImageUrl = url }
                    )
                }

                item {
                    if (page.pageNav.hasPrevious || page.pageNav.hasNext ||
                        page.pageNav.hasFirst || page.pageNav.hasLast
                    ) {
                        PaginationRow(
                            label = "Page ${page.pageNav.currentPage} of ${page.pageNav.pageCount}",
                            hasFirst = page.pageNav.hasFirst,
                            hasPrevious = page.pageNav.hasPrevious,
                            hasNext = page.pageNav.hasNext,
                            hasLast = page.pageNav.hasLast,
                            onFirst = {
                                page.pageNav.firstUrl?.let {
                                    onOpenDestination(NativeDestination.Thread(it, title))
                                }
                            },
                            onPrevious = {
                                page.pageNav.previousUrl?.let {
                                    onOpenDestination(NativeDestination.Thread(it, title))
                                }
                            },
                            onNext = {
                                page.pageNav.nextUrl?.let {
                                    onOpenDestination(NativeDestination.Thread(it, title))
                                }
                            },
                            onLast = {
                                page.pageNav.lastUrl?.let {
                                    onOpenDestination(NativeDestination.Thread(it, title))
                                }
                            },
                            pageCount = page.pageNav.pageCount,
                            currentPage = page.pageNav.currentPage,
                            onJumpToPage = { target ->
                                onOpenDestination(
                                    NativeDestination.Thread(
                                        XenForoUrls.pageUrl(destination.url, target),
                                        title
                                    )
                                )
                            }
                        )
                    }
                }

                item {
                    Button(
                        onClick = { onOpenWeb(XenForoUrls.replyUrl(destination.url)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Reply in browser")
                    }
                    Text(
                        text = "Replies, reactions and attachments use Head-Fi's own editor, " +
                            "so they open in the browser view.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
    }

    if (showWatchDialog) {
        WatchThreadDialog(
            isWatching = isWatching,
            busy = watchBusy,
            onWatch = performWatch,
            onUnwatch = performUnwatch,
            onDismiss = { showWatchDialog = false }
        )
    }

    viewerImageUrl?.let { imageUrl ->
        ImageViewerDialog(
            url = imageUrl,
            onDismiss = { viewerImageUrl = null },
            onOpenExternal = {
                viewerImageUrl = null
                onOpenExternal(it)
            }
        )
    }
}

/**
 * Mirrors Head-Fi's own watch dialog: alerts-only or alerts plus email, and
 * "Stop watching" once the thread is already being watched.
 */
@Composable
private fun WatchThreadDialog(
    isWatching: Boolean,
    busy: Boolean,
    onWatch: (email: Boolean) -> Unit,
    onUnwatch: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (isWatching) "Watching this thread" else "Watch this thread") },
        text = {
            Text(
                if (isWatching) {
                    "You'll get an alert when someone replies. Email notifications " +
                        "can be changed on the site."
                } else {
                    "Get an alert when someone replies. Head-Fi can also email you " +
                        "about new replies."
                }
            )
        },
        confirmButton = {
            if (isWatching) {
                TextButton(onClick = onUnwatch, enabled = !busy) {
                    Text("Stop watching")
                }
            } else {
                Row {
                    TextButton(onClick = { onWatch(false) }, enabled = !busy) {
                        Text("Alerts only")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = { onWatch(true) }, enabled = !busy) {
                        Text("Alerts + email")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        }
    )
}

@Composable
private fun PostCard(
    post: ForumPost,
    textScale: Float,
    loadImages: Boolean,
    isReacted: Boolean,
    isReacting: Boolean,
    reactionCount: Int,
    onReact: () -> Unit,
    onQuote: () -> Unit,
    onReply: () -> Unit,
    onShare: () -> Unit,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Avatar(url = post.avatarUrl, name = post.author, size = 36.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = post.author,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (post.isFirstPost) {
                        Spacer(modifier = Modifier.width(6.dp))
                        LabelPill("OP")
                    }
                    post.postNumber?.let { number ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = number,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                post.authorTitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Absolute date/time where possible: the site's `time` element
                // carries data-time, and "4 minutes ago" is useless next to a
                // post you are reading later.
                val posted = formatPostDate(post.postedAtEpoch) ?: post.timestamp
                posted?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            post.blocks.forEach { block ->
                when (block) {
                    is PostBlock.Paragraph -> PostParagraph(
                        content = block.content,
                        textScale = textScale,
                        onLinkClick = onLinkClick
                    )

                    is PostBlock.Quote -> QuoteBlock(block.text, textScale)

                    is PostBlock.Image -> if (loadImages) {
                        AsyncImage(
                            model = block.url,
                            contentDescription = block.alt,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageClick(block.url) }
                        )
                    } else {
                        ImagePlaceholder()
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            PostAction(
                icon = if (isReacted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = buildString {
                    append(post.reactionLabel ?: "Like")
                    if (reactionCount > 0) append(" · $reactionCount")
                },
                enabled = !isReacting,
                modifier = Modifier.weight(1.25f),
                onClick = onReact
            )
            PostAction(
                icon = Icons.Outlined.FormatQuote,
                label = "Quote",
                modifier = Modifier.weight(1f),
                onClick = onQuote
            )
            PostAction(
                icon = Icons.AutoMirrored.Outlined.Reply,
                label = "Reply",
                modifier = Modifier.weight(1f),
                onClick = onReply
            )
            PostAction(
                icon = Icons.Outlined.Share,
                label = "Share",
                modifier = Modifier.weight(1f),
                onClick = onShare
            )
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        thickness = 1.dp
    )
}

@Composable
private fun PostAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun PostParagraph(
    content: RichText,
    textScale: Float,
    onLinkClick: (String) -> Unit
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(content, linkColor) { content.toAnnotatedString(linkColor) }

    ClickableText(
        text = annotated,
        style = TextStyle(
            fontSize = (15f * textScale).sp,
            lineHeight = (22f * textScale).sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        onClick = { offset ->
            annotated.getStringAnnotations(LINK_TAG, offset, offset)
                .firstOrNull()
                ?.let { onLinkClick(it.item) }
        }
    )
}

@Composable
private fun QuoteBlock(text: String, textScale: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = (14f * textScale).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}

@Composable
private fun ImagePlaceholder() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Image hidden by Data Saver",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Returns true when the link was handled natively. */
private fun onNavigateLink(
    url: String,
    onOpenDestination: (NativeDestination) -> Unit
): Boolean {
    if (url.startsWith(XenForoUrls.BASE)) {
        val destination = nativeDestinationFor(url) ?: return false
        onOpenDestination(destination)
        return true
    }
    return false
}

private const val LINK_TAG = "HEADFI_LINK"

/** XenForo's first/default reaction is "Like". */
private const val DEFAULT_REACTION_ID = 1

private val POST_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")

private fun formatPostDate(epochSeconds: Long?): String? = epochSeconds?.let { epoch ->
    Instant.ofEpochSecond(epoch)
        .atZone(ZoneId.systemDefault())
        .format(POST_DATE_FORMAT)
}

private fun RichText.toAnnotatedString(linkColor: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    spans.forEach { span ->
        val start = span.start.coerceIn(0, text.length)
        val end = span.end.coerceIn(start, text.length)
        if (end <= start) return@forEach

        when (span.kind) {
            TextSpan.Kind.BOLD ->
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)

            TextSpan.Kind.ITALIC ->
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)

            TextSpan.Kind.UNDERLINE ->
                addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)

            TextSpan.Kind.STRIKETHROUGH ->
                addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)

            TextSpan.Kind.CODE -> addStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = linkColor.copy(alpha = 0.08f)
                ),
                start,
                end
            )

            TextSpan.Kind.LINK -> {
                addStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    start,
                    end
                )
                span.url?.let { addStringAnnotation(LINK_TAG, it, start, end) }
            }
        }
    }
}
