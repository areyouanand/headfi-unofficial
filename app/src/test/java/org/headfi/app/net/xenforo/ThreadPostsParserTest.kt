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
    fun `reads reaction, quote and csrf so post actions work natively`() {
        val page = ThreadPostsParser.parse(fixture("thread_posts.html"))
        val first = page.posts[0]

        assertEquals(
            "1793795840,8a6bd311210485018ae3919f6bad7399",
            page.csrfToken
        )
        assertEquals("https://www.head-fi.org/posts/9001/react?reaction_id=1", first.reactionUrl)
        assertEquals(1, first.reactionId)
        assertEquals("Like", first.reactionLabel)
        assertFalse(first.hasReacted)
        assertEquals(
            "https://www.head-fi.org/threads/best-closed.500/reply?quote=9001",
            first.quoteUrl
        )
    }

    @Test
    fun `falls back to a quote url when the action bar is absent`() {
        val post = ThreadPostsParser.parse(
            html = fixture("thread_posts.html"),
            baseUrl = "https://www.head-fi.org/threads/best-closed.500/"
        ).posts[1]

        assertNull(post.reactionUrl)
        assertEquals(
            "https://www.head-fi.org/threads/best-closed.500/reply?quote=9002",
            post.quoteUrl
        )
    }

    @Test
    fun `detects the current user's reaction from the reactions bar`() {
        val html = """
            <article class="message message--post" data-author="Bob" data-content="post-77">
              <div class="bbWrapper">Nice.</div>
              <div class="reactionsBar js-reactionsList is-active">
                <ul class="reactionSummary">
                  <li><span class="reaction reaction--small reaction--1" data-reaction-id="1"></span></li>
                </ul>
                <a class="reactionsBar-link" href="/posts/77/reactions">You</a>
              </div>
            </article>
        """.trimIndent()

        val post = ThreadPostsParser.parse(html).posts.single()
        assertTrue(post.hasReacted)
    }

    @Test
    fun `reads an exact posting time`() {
        val first = ThreadPostsParser.parse(fixture("thread_posts.html")).posts[0]
        assertEquals(
            java.time.OffsetDateTime.parse("2026-11-01T10:00:00Z").toEpochSecond(),
            first.postedAtEpoch
        )
    }

    @Test
    fun `reads watch state and endpoints`() {
        val page = ThreadPostsParser.parse(
            html = fixture("thread_posts.html"),
            baseUrl = "https://www.head-fi.org/threads/best-closed.500/"
        )

        assertFalse(page.isWatching)
        assertEquals(
            "https://www.head-fi.org/threads/best-closed.500/watch",
            page.watchUrl
        )
        assertEquals(
            "https://www.head-fi.org/threads/best-closed.500/watch",
            page.unwatchUrl
        )
    }

    @Test
    fun `ignores the watched threads navigation link`() {
        val html = """
            <html data-xf="2.3" data-template="thread_view">
              <body>
                <nav>
                  <a href="/watched/threads">Watched threads</a>
                  <a href="/whats-new/posts/">New posts</a>
                </nav>
                <h1 class="p-title-value">T</h1>
                <a href="/threads/t.1/watch" class="button">Watch</a>
                <article class="message message--post" data-author="A">
                  <div class="bbWrapper">x</div>
                </article>
              </body>
            </html>
        """.trimIndent()

        val page = ThreadPostsParser.parse(html, "https://www.head-fi.org/threads/t.1/")

        assertFalse(page.isWatching)
        assertEquals("https://www.head-fi.org/threads/t.1/watch", page.watchUrl)
    }

    @Test
    fun `detects an already watched thread`() {
        val html = """
            <html data-xf="2.3" data-template="thread_view">
              <body>
                <h1 class="p-title-value">T</h1>
                <a href="/threads/t.1/watch" class="button button--link"
                   data-xf-click="switch-overlay">Unwatch</a>
                <article class="message message--post" data-author="A">
                  <div class="bbWrapper">x</div>
                </article>
              </body>
            </html>
        """.trimIndent()

        val page = ThreadPostsParser.parse(html, "https://www.head-fi.org/threads/t.1/")
        assertTrue(page.isWatching)
        assertEquals(
            "https://www.head-fi.org/threads/t.1/watch",
            page.unwatchUrl
        )
    }

    @Test
    fun `counts reactions from the summary text`() {
        assertEquals(1, ReactionSummary.countFrom("You"))
        assertEquals(2, ReactionSummary.countFrom("You and Bob"))
        assertEquals(3, ReactionSummary.countFrom("You, Bob and Carol"))
        assertEquals(4, ReactionSummary.countFrom("You and 3 others"))
        assertEquals(3, ReactionSummary.countFrom("3 people"))
        assertNull(ReactionSummary.countFrom(null))
        assertNull(ReactionSummary.countFrom("  "))
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
