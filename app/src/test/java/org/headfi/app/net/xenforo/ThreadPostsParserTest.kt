package org.headfi.app.net.xenforo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadPostsParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing test fixture: $name"
        }.use { it.readBytes().decodeToString() }

    @Test
    fun `parses posts and their metadata`() {
        val page = ThreadPostsParser.parse(fixture("thread_posts.html"))

        assertEquals("Best closed-back under 500", page.title)
        assertEquals(2, page.posts.size)

        val first = page.posts[0]
        assertEquals(9001, first.postId)
        assertEquals("Alice", first.author)
        assertEquals("Headphoneus Supremus", first.authorTitle)
        assertEquals("https://cdn.head-fi.org/avatars/a.jpg", first.avatarUrl)
        assertEquals("#1", first.postNumber)
        assertEquals("Nov 1, 2026", first.timestamp)
        assertEquals("https://www.head-fi.org/threads/best-closed.500/post-9001", first.permalink)
        assertTrue(first.isFirstPost)

        assertFalse(page.posts[1].isFirstPost)
        assertEquals("Bob", page.posts[1].author)
    }

    @Test
    fun `keeps inline formatting and links inside a paragraph`() {
        val paragraph = ThreadPostsParser.parse(fixture("thread_posts.html"))
            .posts[0]
            .blocks
            .filterIsInstance<PostBlock.Paragraph>()
            .first()

        assertTrue(paragraph.content.text.contains("AKG K371"))
        assertTrue(paragraph.content.text.contains("this thread"))

        val bold = paragraph.content.spans.first { it.kind == TextSpan.Kind.BOLD }
        assertEquals("AKG K371", paragraph.content.text.substring(bold.start, bold.end))

        val link = paragraph.content.spans.first { it.kind == TextSpan.Kind.LINK }
        assertEquals(
            "https://www.head-fi.org/threads/other.600/",
            link.url
        )
        assertEquals("this thread", paragraph.content.text.substring(link.start, link.end))
    }

    @Test
    fun `extracts quotes, list items and images`() {
        val blocks = ThreadPostsParser.parse(fixture("thread_posts.html")).posts[0].blocks

        val quote = blocks.filterIsInstance<PostBlock.Quote>().single()
        assertTrue(quote.text.contains("Originally posted by Bob"))

        val listItems = blocks.filterIsInstance<PostBlock.Paragraph>()
            .map { it.content.text }
            .filter { it.startsWith("• ") }
        assertEquals(listOf("• Comfort", "• Isolation"), listItems)

        val image = blocks.filterIsInstance<PostBlock.Image>().single()
        assertEquals("https://www.head-fi.org/attachments/measurement.jpg", image.url)
        assertEquals("measurement", image.alt)
    }

    @Test
    fun `skips smilies so they do not become image blocks`() {
        val blocks = ThreadPostsParser.parse(fixture("thread_posts.html")).posts[1].blocks
        assertTrue(blocks.filterIsInstance<PostBlock.Image>().isEmpty())
        assertTrue(blocks.filterIsInstance<PostBlock.Paragraph>().any {
            it.content.text.contains("Agreed")
        })
    }

    @Test
    fun `reads the pager for thread pages`() {
        val nav = ThreadPostsParser.parse(fixture("thread_posts.html")).pageNav
        assertEquals(1, nav.currentPage)
        assertEquals("https://www.head-fi.org/threads/best-closed.500/page-2", nav.nextUrl)
    }

    @Test
    fun `thread without markup is rejected instead of rendered empty`() {
        val result = runCatching { ThreadPostsParser.parse("<html><body></body></html>") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is XenForoParseException)
    }

    @Test
    fun `unknown thumbnail dimensions are still treated as content images`() {
        val html = """
            <article class="message message--post" data-author="Bob">
              <div class="bbWrapper"><img src="https://cdn.head-fi.org/a.jpg"></div>
            </article>
        """.trimIndent()
        val image = ThreadPostsParser.parse(html).posts.single()
            .blocks.filterIsInstance<PostBlock.Image>().single()
        assertEquals("https://cdn.head-fi.org/a.jpg", image.url)
        assertNull(image.alt)
    }
}
