package org.headfi.app.net.xenforo

/** A single forum (node) on the Head-Fi forums index. */
data class ForumNode(
    val nodeId: Int?,
    val title: String,
    val url: String,
    val description: String?,
    val threadCount: Int?,
    val messageCount: Int?,
    val unread: Boolean
)

/** A category block (`block--category`) containing one or more forums. */
data class ForumCategory(
    val id: Int?,
    val title: String,
    val forums: List<ForumNode>
)

/** Thrown when a page does not look like the markup a parser expects. */
class XenForoParseException(message: String) : Exception(message)
