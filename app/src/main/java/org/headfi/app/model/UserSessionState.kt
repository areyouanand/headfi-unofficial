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
}

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledBlack: Boolean = false,
    val injectWebDarkTheme: Boolean = true,
    val adBlockerEnabled: Boolean = true,
    val hideSiteHeaderFooter: Boolean = false,
    val rememberLastPage: Boolean = true,
    val lastVisitedUrl: String = "https://www.head-fi.org/",
    val savedUsername: String = "",
    val unreadNotificationsEnabled: Boolean = false,
    val pollInterval: PollInterval = PollInterval.HOUR_1
)
