package org.headfi.app.data

import org.headfi.app.net.ContentFetcher
import org.headfi.app.net.PagePayload
import org.headfi.app.net.xenforo.XenForoMarkup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRepositoryTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing test fixture: $name"
        }.use { it.readBytes().decodeToString() }

    private class FakeFetcher(
        var payload: PagePayload,
        var postResult: Boolean = true
    ) : ContentFetcher {
        var getCount = 0
        override suspend fun get(url: String, headers: Map<String, String>): PagePayload {
            getCount++
            return payload
        }

        override suspend fun postForm(url: String, fields: Map<String, String>): Boolean =
            postResult

        override suspend fun probe(): String = "fake probe"
    }

    private fun html(html: String, url: String = "https://www.head-fi.org/forums/portable.4/"): PagePayload =
        PagePayload.Html(url = url, html = html, meta = XenForoMarkup.meta(html))

    @Test
    fun `parses a thread list and caches it`() = kotlinx.coroutines.runBlocking {
        val fetcher = FakeFetcher(html(fixture("thread_list.html")))
        val repository = ContentRepository(fetcher, null)

        val first = repository.threadList("https://www.head-fi.org/forums/portable.4/")
        assertTrue(first is ContentResult.Ready)
        assertEquals(2, (first as ContentResult.Ready).value.threads.size)

        val second = repository.threadList("https://www.head-fi.org/forums/portable.4/")
        assertTrue(second is ContentResult.Ready)
        assertEquals(1, fetcher.getCount)

        repository.invalidateAll()
        repository.threadList("https://www.head-fi.org/forums/portable.4/")
        assertEquals(2, fetcher.getCount)
    }

    @Test
    fun `passes a challenge through as needs-verification`() = kotlinx.coroutines.runBlocking {
        val fetcher = FakeFetcher(
            PagePayload.NeedsVerification("https://www.head-fi.org/bot/challenge")
        )
        val repository = ContentRepository(fetcher, null)

        val result = repository.alerts("https://www.head-fi.org/account/alerts")
        assertEquals(
            "https://www.head-fi.org/bot/challenge",
            (result as ContentResult.NeedsVerification).url
        )
    }

    @Test
    fun `turns http failures into a readable message`() = kotlinx.coroutines.runBlocking {
        val fetcher = FakeFetcher(
            PagePayload.Unavailable(
                url = "https://www.head-fi.org/forums/",
                reason = "HTTP 503",
                statusCode = 503
            )
        )
        val repository = ContentRepository(fetcher, null)

        val result = repository.forums("https://www.head-fi.org/forums/")
        val message = (result as ContentResult.Failed).message
        assertTrue(message.contains("HTTP 503"))
    }

    @Test
    fun `surfaces parse failures without throwing`() = kotlinx.coroutines.runBlocking {
        val fetcher = FakeFetcher(html("<html><body>not a thread list</body></html>"))
        val repository = ContentRepository(fetcher, null)

        val result = repository.threadList("https://www.head-fi.org/forums/portable.4/")
        assertTrue(result is ContentResult.Failed)
    }

    @Test
    fun `posts a reaction through the fetcher`() = kotlinx.coroutines.runBlocking {
        val fetcher = FakeFetcher(html(""), postResult = true)
        val repository = ContentRepository(fetcher, null)

        assertTrue(
            repository.react(
                reactionUrl = "https://www.head-fi.org/posts/1/react",
                csrfToken = "token",
                reactionId = 1
            )
        )
    }
}
