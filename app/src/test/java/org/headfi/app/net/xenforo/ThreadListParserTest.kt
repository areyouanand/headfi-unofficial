package org.headfi.app.net.xenforo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadListParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing test fixture: $name"
        }.use { it.readBytes().decodeToString() }

    @Test
    fun `parses a forum view`() {
        val page = ThreadListParser.parse(fixture("thread_list.html"))

        assertEquals("Portable Headphones", page.title)
        assertEquals(2, page.threads.size)

        val pinned = page.threads[0]
        assertEquals(100, pinned.threadId)
        assertEquals("Pinned guide to IEMs", pinned.title)
        assertEquals("https://www.head-fi.org/threads/pinned-guide.100/", pinned.url)
        assertEquals("Alice", pinned.author)
        assertEquals("Review", pinned.prefix)
        assertEquals(1_234, pinned.replyCount)
        assertEquals(98_765, pinned.viewCount)
        assertEquals("Bob", pinned.lastPoster)
        assertEquals("Nov 1, 2026", pinned.lastPostTime)
        assertEquals("Portable Headphones", pinned.forumName)
        assertTrue(pinned.isSticky)
        assertFalse(pinned.isUnread)
        assertNull(pinned.unreadUrl)
    }

    @Test
    fun `marks unread threads and drops abbreviated counts`() {
        val unread = ThreadListParser.parse(fixture("thread_list.html")).threads[1]

        assertTrue(unread.isUnread)
        assertFalse(unread.isSticky)
        assertEquals(8, unread.replyCount)
        // "1.2K" cannot be read exactly, so it is omitted rather than guessed.
        assertNull(unread.viewCount)
        assertNull(unread.prefix)
        assertEquals(
            "https://www.head-fi.org/threads/closed-back.101/unread",
            unread.unreadUrl
        )
    }

    @Test
    fun `synthesizes a first-unread route when the row has no link`() {        val html = """
            <html data-xf="2.3" data-template="forum_view"><body>
            <div class="structItem structItem--thread structItem--unread"
                 data-content="thread-42">
                <div class="structItem-title">
                    <a href="/threads/slug.42/page-3">A thread</a>
                </div>
            </div>
            </body></html>
        """.trimIndent()

        val thread = ThreadListParser.parse(html).threads.single()
        assertEquals(
            "https://www.head-fi.org/threads/slug.42/page-3",
            thread.url
        )
        assertEquals(
            "https://www.head-fi.org/threads/slug.42/unread",
            thread.unreadUrl
        )
    }

    @Test
    fun `reads the pager`() {
        val nav = ThreadListParser.parse(fixture("thread_list.html")).pageNav

        assertEquals(1, nav.currentPage)
        assertEquals(3, nav.pageCount)
        assertTrue(nav.hasNext)
        assertFalse(nav.hasPrevious)
        assertEquals(
            "https://www.head-fi.org/forums/portable-headphones.4/page-2",
            nav.nextUrl
        )
        // First/last shortcuts come from the rendered page numbers.
        assertEquals(
            "https://www.head-fi.org/forums/portable-headphones.4/",
            nav.firstUrl
        )
        assertFalse(nav.hasFirst)
        assertEquals(
            "https://www.head-fi.org/forums/portable-headphones.4/page-3",
            nav.lastUrl
        )
        assertTrue(nav.hasLast)
    }

    @Test
    fun `parses search results from content rows`() {
        val page = ThreadListParser.parse(fixture("search_results.html"))

        assertEquals(2, page.threads.size)

        val thread = page.threads[0]
        assertEquals("MOONDROP EDGE Gets an Upgrade", thread.title)
        assertEquals(
            "https://www.head-fi.org/threads/moondrop-edge.981849/",
            thread.url
        )
        assertEquals("...the EDGE 2 brings a refreshed experience...", thread.snippet)
        assertEquals("Portable Headphones", thread.forumName)
        assertEquals("Today at 9:00 AM", thread.lastPostTime)

        val post = page.threads[1]
        assertEquals("https://www.head-fi.org/posts/19302448/", post.url)
        assertNull(post.forumName)

        assertEquals(2, page.pageNav.pageCount)
    }
}
