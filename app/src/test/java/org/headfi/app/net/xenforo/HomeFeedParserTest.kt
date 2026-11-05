package org.headfi.app.net.xenforo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing test fixture: $name"
        }.use { it.readBytes().decodeToString() }

    @Test
    fun `parses the front-page cards with images and excerpts`() {
        val items = HomeFeedParser.parse(fixture("home_page.html"))

        assertEquals(3, items.size)

        val first = items[0]
        assertEquals("Look Out U2: MOONDROP Introduces the Edge2", first.title)
        assertTrue(first.excerpt.startsWith("\"The MOONDROP Edge2"))
        assertEquals("https://www.head-fi.org/threads/981849/", first.url)
        assertEquals(
            "https://cdn.head-fi.org/highlights/8463_l?1793803278",
            first.imageUrl
        )
        assertEquals(HomeFeedKind.THREAD, first.kind)
    }

    @Test
    fun `classifies showcase reviews and articles`() {
        val items = HomeFeedParser.parse(fixture("home_page.html"))

        val review = items[1]
        assertEquals(HomeFeedKind.REVIEW, review.kind)
        assertEquals(
            "https://www.head-fi.org/showcase/29532/reviews#review-45193",
            review.url
        )
        assertEquals(
            "https://cdn.head-fi.org/highlights/8464_m?1793804037",
            review.imageUrl
        )

        val article = items[2]
        assertEquals(HomeFeedKind.ARTICLE, article.kind)
        assertNull(article.imageUrl)
    }

    @Test(expected = XenForoParseException::class)
    fun `throws when the grid is missing`() {
        HomeFeedParser.parse("<html><body>not the homepage</body></html>")
    }
}
