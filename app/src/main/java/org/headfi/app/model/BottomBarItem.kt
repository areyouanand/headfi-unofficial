package org.headfi.app.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Everything the user can pin to the bottom bar. Profile is not listed here:
 * it always sits last, since it is the way back into these settings.
 */
enum class BottomBarItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val section: HeadFiSection? = null
) {
    HOME("Home", Icons.Outlined.Home, Icons.Filled.Home, HeadFiSection.HOME),
    FORUMS("Forums", Icons.Outlined.Forum, Icons.Filled.Forum, HeadFiSection.FORUMS),
    WHATS_NEW("What's New", Icons.Outlined.Explore, Icons.Filled.Explore, HeadFiSection.WHATS_NEW),
    HEAD_GEAR("Head Gear", Icons.Outlined.Headphones, Icons.Filled.Headphones, HeadFiSection.HEAD_GEAR),
    CLASSIFIEDS("Classifieds", Icons.Outlined.Sell, Icons.Filled.Sell, HeadFiSection.CLASSIFIEDS),
    BACK("Back", Icons.AutoMirrored.Outlined.ArrowBack),
    FORWARD("Forward", Icons.AutoMirrored.Outlined.ArrowForward),
    REFRESH("Refresh", Icons.Outlined.Refresh),
    SEARCH("Search", Icons.Outlined.Search),
    FIND_IN_PAGE("Find", Icons.Outlined.FindInPage),
    WATCHED_THREADS("Watched", Icons.Outlined.Visibility),
    CONVERSATIONS("Messages", Icons.Outlined.MailOutline),
    BOOKMARKS("Bookmarks", Icons.Outlined.BookmarkBorder),
    ALERTS("Alerts", Icons.Outlined.NotificationsNone),
    SHARE("Share", Icons.Outlined.Share),
    OPEN_EXTERNAL("Browser", Icons.Outlined.OpenInBrowser);

    /** Member pages only make sense with a session, so these are hidden when signed out. */
    val requiresSignIn: Boolean
        get() = this in SIGNED_IN_ONLY

    fun isVisibleTo(signedIn: Boolean): Boolean = signedIn || !requiresSignIn

    /** Compact label for the bottom bar, where space is tight. */
    val shortLabel: String
        get() = when (this) {
            WHATS_NEW -> "News"
            else -> label
        }

    companion object {
        /** Four items plus Profile keeps the bar at Material's five-destination guidance. */
        const val MAX_SELECTED = 4

        private val SIGNED_IN_ONLY = setOf(WATCHED_THREADS, CONVERSATIONS, BOOKMARKS, ALERTS)

        val DEFAULT: List<BottomBarItem> = listOf(HOME, FORUMS, WHATS_NEW, CLASSIFIEDS)

        /** Order is kept exactly as stored, since the user arranges it. */
        fun fromNames(raw: String?): List<BottomBarItem> {
            val parsed = raw
                ?.split(',')
                ?.mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }
                ?.distinct()
                ?.take(MAX_SELECTED)
                .orEmpty()
            return parsed.ifEmpty { DEFAULT }
        }
    }
}
