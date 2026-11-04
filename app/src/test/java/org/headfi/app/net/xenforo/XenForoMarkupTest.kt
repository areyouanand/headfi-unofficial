package org.headfi.app.net.xenforo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The challenge detector is load-bearing: Head-Fi serves the bot-challenge page
 * with `200 OK`, so without this check a native screen would confidently render
 * an empty forum list instead of asking the WebView to clear the challenge.
 */
class XenForoMarkupTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing test fixture: $name"
        }.use { it.readBytes().decodeToString() }

    @Test
    fun `detects the head-fi bot challenge template`() {
        val html = fixture("challenge.html")
        assertTrue(XenForoMarkup.isChallengePage(html, "https://www.head-fi.org/forums/"))
    }

    @Test
    fun `detects the challenge path even before parsing`() {
        assertTrue(
            XenForoMarkup.isChallengePage(
                html = "<html><body>loading</body></html>",
                url = "https://www.head-fi.org/bot/challenge"
            )
        )
    }

    @Test
    fun `detects a cloudflare interstitial`() {
        val html = """
            <!DOCTYPE html><html><head><title>Just a moment...</title></head>
            <body><form id="challenge-form"></form></body></html>
        """.trimIndent()
        assertTrue(XenForoMarkup.isChallengePage(html, "https://www.head-fi.org/forums/"))
    }

    @Test
    fun `does not flag a real forums page`() {
        val html = fixture("forums_index.html")
        assertFalse(XenForoMarkup.isChallengePage(html, "https://www.head-fi.org/forums/"))

        val meta = XenForoMarkup.meta(html)
        assertEquals("forum_list", meta.template)
        assertEquals("2.3", meta.xfVersion)
        assertTrue(meta.loggedIn)
        assertFalse(meta.isLoginPage)
    }

    @Test
    fun `identifies a logged-out page`() {
        val meta = XenForoMarkup.meta(fixture("challenge.html"))
        assertEquals("hfbot_challenge_widget", meta.template)
        assertFalse(meta.loggedIn)
    }
}
