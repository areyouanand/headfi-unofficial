package org.headfi.app.net.xenforo

/** One row on `/account/alerts`. */
data class AlertItem(
    val title: String,
    val url: String,
    val snippet: String?,
    val timeAgo: String?,
    val avatarUrl: String?,
    val isUnread: Boolean
)

data class AlertsPage(
    val alerts: List<AlertItem>,
    val pageNav: PageNav
)
