package org.headfi.app.net

/**
 * The small surface [org.headfi.app.data.ContentRepository] needs from the
 * network layer. Keeping it behind an interface lets the repository be tested
 * on the JVM without an Android Context or a real Head-Fi connection.
 */
interface ContentFetcher {

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): PagePayload

    suspend fun postForm(
        url: String,
        fields: Map<String, String>
    ): Boolean

    /** One-line diagnostic for the Session health screen. */
    suspend fun probe(): String
}
