package org.headfi.app.net.xenforo

/** A span of style over a [RichText]'s characters, with no UI dependency. */
data class TextSpan(
    val start: Int,
    val end: Int,
    val kind: Kind,
    val url: String? = null
) {
    enum class Kind { BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, CODE, LINK }
}

data class RichText(val text: String, val spans: List<TextSpan> = emptyList())

/** Renderable pieces of a forum post body. */
sealed interface PostBlock {
    data class Paragraph(val content: RichText) : PostBlock
    data class Quote(val text: String) : PostBlock
    data class Image(val url: String, val alt: String? = null) : PostBlock
}

/** One post in a thread. */
data class ForumPost(
    val postId: Int?,
    val author: String,
    val avatarUrl: String?,
    val authorTitle: String?,
    val timestamp: String?,
    val postNumber: String?,
    val permalink: String?,
    val isFirstPost: Boolean,
    val blocks: List<PostBlock>
)

data class ThreadPostsPage(
    val title: String?,
    val posts: List<ForumPost>,
    val pageNav: PageNav
)
