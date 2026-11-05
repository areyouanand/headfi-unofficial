package org.headfi.app.net.xenforo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertsParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing test fixture: $name"
        }.use { it.readBytes().decodeToString() }

    @Test
    fun `parses live-shaped alert rows`() {
        val page = AlertsParser.parse(fixture("alerts.html"))

        assertEquals(2, page.alerts.size)

        val unread = page.alerts[0]
        assertEquals(
            "Bob replied to the thread Best closed-back under 500.",
            unread.title
        )
        assertEquals("https://www.head-fi.org/posts/9002/", unread.url)
        assertNull(unread.snippet)
        assertEquals("2 minutes ago", unread.timeAgo)
        assertEquals("https://cdn.head-fi.org/avatars/b.jpg", unread.avatarUrl)
        assertTrue(unread.isUnread)

        val read = page.alerts[1]
        assertFalse(read.isUnread)
        assertEquals("https://www.head-fi.org/posts/9003/", read.url)
        assertEquals("Yesterday", read.timeAgo)
    }

    @Test
    fun `accepts a genuinely empty alerts page`() {
        val page = AlertsParser.parse(
            """<html data-xf="2.3" data-template="account_alerts"><body></body></html>"""
        )
        assertTrue(page.alerts.isEmpty())
    }

    @Test
    fun `refuses a page without alert rows`() {
        val result = runCatching { AlertsParser.parse("<html><body></body></html>") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is XenForoParseException)
    }
}
