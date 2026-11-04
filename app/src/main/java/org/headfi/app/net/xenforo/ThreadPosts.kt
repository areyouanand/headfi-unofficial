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
    val blocks: List<PostBlock>,
    /** XenForo's "Like"/reaction endpoint for this post, taken from the page. */
    val reactionUrl: String? = null,
    val reactionId: Int? = null,
    val reactionLabel: String? = null,
    val hasReacted: Boolean = false,
    /** Number of reactions on the post, parsed from the reactions bar. */
    val reactionCount: Int? = null,
    /** Exact posting time in epoch seconds (`data-time`), for a real date/time. */
    val postedAtEpoch: Long? = null,
    /** Per-post reply link, normally `/threads/<slug>.<id>/reply?quote=<postId>`. */
    val quoteUrl: String? = null
)

/**
 * Turns the reactions bar's summary text into a count.
 *
 * XenForo renders the viewers' names, not a number: "You", "You and Bob",
 * "You, Bob and Carol", or "You and 3 others". Counting names also gives a
 * sensible result for the collapsed form.
 */
internal object ReactionSummary {

    fun countFrom(text: String?): Int? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return null

        Regex("""(\d+)\s+(?:other|others|person|people)""", RegexOption.IGNORE_CASE)
            .find(value)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.let { collapsed ->
                val includesYou = value.contains("You", ignoreCase = false)
                return collapsed + if (includesYou) 1 else 0
            }

        val names = value
            .split(",", " and ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return names.size.coerceAtLeast(1)
    }
}

data class ThreadPostsPage(
    val title: String?,
    val posts: List<ForumPost>,
    val pageNav: PageNav,
    /** CSRF token required to POST a reaction back to XenForo. */
    val csrfToken: String? = null,
    /** True when the viewer already watches this thread. */
    val isWatching: Boolean = false,
    val watchUrl: String? = null,
    val unwatchUrl: String? = null
)
