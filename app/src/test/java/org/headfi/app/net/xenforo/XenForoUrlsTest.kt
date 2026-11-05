package org.headfi.app.net.xenforo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XenForoUrlsTest {

    @Test
    fun `reads thread and forum ids`() {
        assertEquals(42, XenForoUrls.threadId("https://www.head-fi.org/threads/slug.42/"))
        assertEquals(42, XenForoUrls.threadId("https://www.head-fi.org/threads/slug.42/page-3"))
        assertEquals(4, XenForoUrls.forumId("https://www.head-fi.org/forums/portable.4/"))
        assertNull(XenForoUrls.threadId("https://www.head-fi.org/forums/portable.4/"))
    }

    @Test
    fun `builds the first-unread route`() {
        assertEquals(
            "https://www.head-fi.org/threads/slug.42/unread",
            XenForoUrls.firstUnreadUrl("https://www.head-fi.org/threads/slug.42/")
        )
        assertEquals(
            "https://www.head-fi.org/threads/slug.42/unread",
            XenForoUrls.firstUnreadUrl("https://www.head-fi.org/threads/slug.42/page-7")
        )
        assertEquals(
            "https://www.head-fi.org/threads/slug.42/unread",
            XenForoUrls.firstUnreadUrl("https://www.head-fi.org/threads/slug.42/unread")
        )
        assertNull(XenForoUrls.firstUnreadUrl("https://www.head-fi.org/forums/portable.4/"))
    }

    @Test
    fun `builds page urls for paths`() {
        assertEquals(
            "https://www.head-fi.org/threads/slug.42/",
            XenForoUrls.pageUrl("https://www.head-fi.org/threads/slug.42/page-7", 1)
        )
        assertEquals(
            "https://www.head-fi.org/threads/slug.42/page-5",
            XenForoUrls.pageUrl("https://www.head-fi.org/threads/slug.42/", 5)
        )
        assertEquals(
            "https://www.head-fi.org/forums/portable.4/page-2",
            XenForoUrls.pageUrl("https://www.head-fi.org/forums/portable.4/", 2)
        )
    }

    @Test
    fun `builds page urls for query pagers`() {
        assertEquals(
            "https://www.head-fi.org/search/search?keywords=iem&page=3",
            XenForoUrls.pageUrl("https://www.head-fi.org/search/search?keywords=iem", 3)
        )
        assertEquals(
            "https://www.head-fi.org/search/search?keywords=iem",
            XenForoUrls.pageUrl("https://www.head-fi.org/search/search?keywords=iem&page=3", 1)
        )
    }

    @Test
    fun `builds post, reaction, watch and reply urls`() {
        assertEquals("https://www.head-fi.org/posts/99/", XenForoUrls.postUrl(99))
        assertEquals("https://www.head-fi.org/posts/99/react", XenForoUrls.reactionUrl(99))
        assertEquals(
            "https://www.head-fi.org/threads/slug.42/watch",
            XenForoUrls.watchUrl("https://www.head-fi.org/threads/slug.42/page-2")
        )
        assertEquals(
            "https://www.head-fi.org/threads/slug.42/reply",
            XenForoUrls.replyUrl("https://www.head-fi.org/threads/slug.42/page-2")
        )
    }
}
