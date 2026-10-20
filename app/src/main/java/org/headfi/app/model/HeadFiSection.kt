package org.headfi.app.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.ui.graphics.vector.ImageVector

enum class HeadFiSection(
    val title: String,
    val url: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val pathMatches: List<String>
) {
    HOME(
        title = "Home",
        url = "https://www.head-fi.org/",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        pathMatches = listOf("/", "/articles")
    ),
    FORUMS(
        title = "Forums",
        url = "https://www.head-fi.org/forums/",
        selectedIcon = Icons.Filled.Forum,
        unselectedIcon = Icons.Outlined.Forum,
        pathMatches = listOf("/forums", "/threads", "/categories")
    ),
    WHATS_NEW(
        title = "What's New",
        url = "https://www.head-fi.org/whats-new/posts/",
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore,
        pathMatches = listOf("/whats-new")
    ),
    HEAD_GEAR(
        title = "Head Gear",
        url = "https://www.head-fi.org/showcase/",
        selectedIcon = Icons.Filled.Headphones,
        unselectedIcon = Icons.Outlined.Headphones,
        pathMatches = listOf("/showcase")
    ),
    CLASSIFIEDS(
        title = "Classifieds",
        url = "https://www.head-fi.org/classifieds/",
        selectedIcon = Icons.Filled.Sell,
        unselectedIcon = Icons.Outlined.Sell,
        pathMatches = listOf("/classifieds")
    );

    companion object {
        fun fromUrl(url: String): HeadFiSection {
            val path = runCatching {
                java.net.URI(url).path ?: "/"
            }.getOrDefault("/")

            return when {
                path.startsWith("/classifieds") || path.contains("classifieds") -> CLASSIFIEDS
                path.startsWith("/showcase") -> HEAD_GEAR
                path.startsWith("/whats-new") -> WHATS_NEW
                path.startsWith("/forums") || path.startsWith("/threads") || path.startsWith("/categories") -> FORUMS
                else -> HOME
            }
        }
    }
}

enum class QuickDestination(
    val label: String,
    val subtitle: String,
    val url: String
) {
    NEW_POSTS("New Posts", "Latest unread forum activity", "https://www.head-fi.org/whats-new/posts/"),
    WATCHED_THREADS("Watched Threads", "Threads you are following", "https://www.head-fi.org/watched/threads"),
    WATCHED_FORUMS("Watched Forums", "Sub-forums you subscribe to", "https://www.head-fi.org/watched/forums"),
    ALERTS("Alerts", "Your notifications & mentions", "https://www.head-fi.org/account/alerts"),
    CONVERSATIONS("Private Messages", "Direct conversations with members", "https://www.head-fi.org/conversations/"),
    BOOKMARKS("Bookmarks", "Saved posts & classifieds", "https://www.head-fi.org/account/bookmarks"),
    ARTICLES("Articles & CanJam", "Official editorial & event coverage", "https://www.head-fi.org/articles/"),
    SPONSORS("Sponsors", "Head-Fi community sponsors", "https://www.head-fi.org/sponsors/")
}
