package org.headfi.app.net.xenforo

/** What kind of page a front-page card points at. */
enum class HomeFeedKind {
    THREAD,
    REVIEW,
    SHOWCASE,
    ARTICLE,
    OTHER
}

/**
 * One card from Head-Fi's editorial front page (`/`).
 *
 * The homepage is not a XenForo thread list: it is the `hfh_homepage` template,
 * whose `#hfh_grid_container` holds the curated cards. Each card carries its
 * image as a CSS `background-image`, with the headline and excerpt nested inside
 * `.hfh_slot_content` and `.hfh_slot_text`.
 */
data class HomeFeedItem(
    val title: String,
    val excerpt: String,
    val url: String,
    val imageUrl: String?,
    val kind: HomeFeedKind
)
