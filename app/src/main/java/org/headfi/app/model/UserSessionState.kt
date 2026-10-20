package org.headfi.app.model

enum class ThemeMode(val label: String) {
    SYSTEM("Follow System"),
    LIGHT("Light"),
    DARK("Dark")
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
    val dynamicColors: Boolean = true,
    val amoledBlack: Boolean = false,
    val injectWebDarkTheme: Boolean = true,
    val adBlockerEnabled: Boolean = true,
    val hideSiteHeaderFooter: Boolean = false,
    val rememberLastPage: Boolean = true,
    val lastVisitedUrl: String = "https://www.head-fi.org/",
    val savedUsername: String = ""
)
