package org.headfi.app.net.xenforo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RssThreadListParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing test fixture: $name"
        }.use { it.readBytes().decodeToString() }

    @Test
    fun `parses the feed into thread summaries`() {
        val page = RssThreadListParser.parse(
            fixture("rss_threads.xml"),
            "https://www.head-fi.org/forums/-/index.rss"
        )

        assertTrue(page.isPartial)
        assertEquals("Headphones (full-size)", page.title)
        assertEquals(2, page.threads.size)
        assertFalse(page.pageNav.hasNext)

        val first = page.threads[0]
        assertEquals(981816, first.threadId)
        assertEquals("AUNE AR3000 Impressions and Discussion thread", first.title)
        assertEquals(
            "https://www.head-fi.org/threads/aune-ar3000-impressions-and-discussion-thread.981816/",
            first.url
        )
        // dc:creator wins over the RSS author email.
        assertEquals("Currawong", first.author)
        assertEquals("Headphones (full-size)", first.forumName)
        assertEquals("Wed, 04 Nov 2026 12:57:16 +0000", first.lastPostTime)
        assertNull(first.replyCount)
        assertNull(first.prefix)

        assertEquals("Alice", page.threads[1].author)
    }

    @Test
    fun `rejects a feed with no items`() {
        val result = runCatching {
            RssThreadListParser.parse("<rss><channel></channel></rss>", XenForoUrls.BASE)
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is XenForoParseException)
    }

    @Test
    fun `builds rss urls only for forum pages`() {
        assertEquals(
            "https://www.head-fi.org/forums/headphones-full-size.4/index.rss",
            XenForoUrls.rssFor("https://www.head-fi.org/forums/headphones-full-size.4/")
        )
        assertEquals(
            "https://www.head-fi.org/forums/headphones-full-size.4/index.rss",
            XenForoUrls.rssFor("https://www.head-fi.org/forums/headphones-full-size.4/page-3")
        )
        assertNull(XenForoUrls.rssFor("https://www.head-fi.org/whats-new/posts/"))
        assertNull(XenForoUrls.rssFor("https://www.head-fi.org/threads/foo.123/"))
    }
}
