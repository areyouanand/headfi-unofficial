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
}
