package org.headfi.app.model

enum class ThemeMode(val label: String) {
    SYSTEM("Follow System"),
    LIGHT("Light"),
    DARK("Dark")
}

/**
 * How often the background worker re-checks Head-Fi for unread alerts and
 * private messages. WorkManager clamps anything below 15 minutes, so that is
 * the floor.
 */
enum class PollInterval(val minutes: Long, val label: String) {
    MIN_15(15, "Every 15 min"),
    MIN_30(30, "Every 30 min"),
    HOUR_1(60, "Hourly"),
    HOURS_3(180, "Every 3 hours");

    companion object {
        fun fromName(name: String?): PollInterval =
            entries.firstOrNull { it.name == name } ?: HOUR_1
    }
}

/**
 * Text scaling for page content, applied through `WebSettings.textZoom`.
 * Forum threads are dense, so a couple of steps up helps a lot on phones.
 */
enum class TextSize(val scale: Float, val label: String) {
    SMALL(0.85f, "Small"),
    DEFAULT(1.0f, "Default"),
    LARGE(1.15f, "Large"),
    EXTRA_LARGE(1.3f, "Extra large");

    companion object {
        fun fromName(name: String?): TextSize =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Which layout Head-Fi should serve: the mobile site or the desktop site. */
enum class SiteLayout(val label: String) {
    MOBILE("Mobile"),
    DESKTOP("Desktop");

    companion object {
        fun fromName(name: String?): SiteLayout =
            entries.firstOrNull { it.name == name } ?: MOBILE
    }
}

data class UserSessionState(
    val isLoggedIn: Boolean = false,
    val username: String = "",
    val avatarUrl: String = "",
    val unreadAlerts: Int = 0,
    val unreadConversations: Int = 0,
    val hasRememberMeCookie: Boolean = false
) {
    val totalUnread: Int
        get() = unreadAlerts + unreadConversations

    val isSignedIn: Boolean
        get() = isLoggedIn || hasRememberMeCookie
}

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledBlack: Boolean = false,
    val injectWebDarkTheme: Boolean = true,
    val adBlockerEnabled: Boolean = true,
    val rememberLastPage: Boolean = true,
    val lastVisitedUrl: String = "https://www.head-fi.org/",
    val savedUsername: String = "",
    val unreadNotificationsEnabled: Boolean = false,
    val pollInterval: PollInterval = PollInterval.HOUR_1,
    val textSize: TextSize = TextSize.DEFAULT,
    val blockImages: Boolean = false,
    val siteLayout: SiteLayout = SiteLayout.MOBILE,
    val bottomBarItems: List<BottomBarItem> = BottomBarItem.DEFAULT,
    val signInPromptDismissed: Boolean = false
)
