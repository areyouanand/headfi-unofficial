package org.headfi.app.net.xenforo

/** One row in a forum view, "What's New", watched threads, or search results. */
data class ThreadSummary(
    val threadId: Int?,
    val title: String,
    val url: String,
    val author: String?,
    val replyCount: Int?,
    val viewCount: Int?,
    val lastPoster: String?,
    val lastPostTime: String?,
    val prefix: String?,
    val forumName: String?,
    val isSticky: Boolean,
    val isUnread: Boolean,
    /** `/threads/<slug>.<id>/unread`, when the row offers a first-unread route. */
    val unreadUrl: String? = null,
    /** Matched text shown on search results/alerts; null on ordinary thread lists. */
    val snippet: String? = null
)

data class ThreadListPage(
    val title: String?,
    val threads: List<ThreadSummary>,
    val pageNav: PageNav,
    /**
     * True when this came from an RSS feed: newest threads only, no stickies and
     * no pagination. The UI shows a "verify for the full list" notice.
     */
    val isPartial: Boolean = false
)
