package org.headfi.app.net.xenforo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * JVM tests for the XenForo threading. No Android or network access: the
 * parsers take raw HTML, so a template change can be caught here instead of on
 * a device.
 */
class ForumsIndexParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing test fixture: $name"
        }.use { it.readBytes().decodeToString() }

    @Test
    fun `parses categories and forum nodes`() {
        val categories = ForumsIndexParser.parse(fixture("forums_index.html"))

        assertEquals(2, categories.size)

        val headphones = categories[0]
        assertEquals("Headphones", headphones.title)
        assertEquals(1, headphones.id)
        assertEquals(2, headphones.forums.size)

        val portable = headphones.forums[0]
        assertEquals(4, portable.nodeId)
        assertEquals("Portable Headphones", portable.title)
        assertEquals(
            "https://www.head-fi.org/forums/portable-headphones.4/",
            portable.url
        )
        assertEquals("Everything about portable headphones", portable.description)
        assertEquals(12_345, portable.threadCount)
        assertEquals(678_901, portable.messageCount)
        assertTrue(portable.unread)

        val fullSize = headphones.forums[1]
        assertEquals("Full-Size Headphones", fullSize.title)
        assertEquals(
            "https://www.head-fi.org/forums/full-size-headphones.5/",
            fullSize.url
        )
        assertFalse(fullSize.unread)
    }

    @Test
    fun `keeps forums that have no stats or description`() {
        val source = categories()[1].forums.single()
        assertEquals(21, source.nodeId)
        assertEquals("Dedicated Source Components", source.title)
        assertNull(source.threadCount)
        assertNull(source.messageCount)
        assertNull(source.description)
    }

    @Test
    fun `accepts uncategorised forum nodes`() {
        val html = """
            <html data-xf="2.3"><body>
              <div class="node node--forum" data-node-id="99">
                <h3 class="node-title"><a href="/forums/loose.99/">Loose Forum</a></h3>
              </div>
            </body></html>
        """.trimIndent()

        val categories = ForumsIndexParser.parse(html)
        assertEquals(1, categories.size)
        assertNull(categories[0].id)
        assertEquals("Loose Forum", categories[0].forums.single().title)
    }

    @Test
    fun `refuses a page that is not a forums index`() {
        try {
            ForumsIndexParser.parse(fixture("challenge.html"))
            fail("Expected XenForoParseException for a challenge page")
        } catch (expected: XenForoParseException) {
            assertTrue(expected.message!!.contains("hfbot_challenge_widget"))
        }
    }

    private fun categories(): List<ForumCategory> =
        ForumsIndexParser.parse(fixture("forums_index.html"))
}
