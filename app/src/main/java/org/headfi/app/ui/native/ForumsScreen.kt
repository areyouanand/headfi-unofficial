package org.headfi.app.ui.native

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import org.headfi.app.net.xenforo.ForumNode
import org.headfi.app.net.xenforo.XenForoUrls

@Composable
fun ForumsScreen(
    repository: ContentRepository,
    refreshToken: Int,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenWeb: (String) -> Unit,
    onOpenForum: (ForumNode) -> Unit
) {
    val url = XenForoUrls.BASE + "/forums/"
    val state = rememberContent(url, refreshToken) { repository.forums(url) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            NativeHeader(
                title = "Forums",
                subtitle = when (state) {
                    is Loadable.Ready ->
                        "${state.value.sumOf { it.forums.size }} forums in " +
                            "${state.value.size} categories"
                    else -> null
                },
                onBack = if (canGoBack) onBack else null,
                onRefresh = onRefresh,
                onOpenBrowser = { onOpenWeb(url) }
            )
        }

        when (state) {
            is Loadable.Loading -> item { LoadingState("Loading forums…") }

            is Loadable.Error -> item {
                ErrorState(
                    message = state.message,
                    onRetry = onRefresh,
                    onOpenBrowser = { onOpenWeb(url) }
                )
            }

            is Loadable.Verification -> item {
                VerificationState(onVerify = { onOpenWeb(state.url) })
            }

            is Loadable.Ready -> state.value.forEach { category ->
                if (category.title.isNotBlank()) {
                    item(key = "category-${category.id}-${category.title}") {
                        SectionLabel(category.title)
                    }
                }
                items(category.forums, key = { it.url }) { forum ->
                    ForumRow(forum = forum, onClick = { onOpenForum(forum) })
                }
            }
        }
    }
}

@Composable
private fun ForumRow(forum: ForumNode, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (forum.unread) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = forum.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        forum.description?.let { description ->
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        val stats = buildList {
            forum.threadCount?.let { add("${it.formatted()} threads") }
            forum.messageCount?.let { add("${it.formatted()} posts") }
        }
        if (stats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stats.joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        thickness = 1.dp
    )
}

internal fun Int.formatted(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()
