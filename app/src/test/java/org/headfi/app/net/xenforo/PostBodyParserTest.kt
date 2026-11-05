package org.headfi.app.net.xenforo

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostBodyParserTest {

    private fun parse(html: String): List<PostBlock> =
        PostBodyParser.parse(
            Jsoup.parse(
                "<div class=\"bbWrapper\">$html</div>",
                "https://www.head-fi.org/"
            ).selectFirst(".bbWrapper")
        )

    @Test
    fun `keeps paragraphs and inline styles`() {
        val blocks = parse(
            """<p>Hello <b>world</b> and <a href="/threads/other.1/">link</a>.</p>""" +
                "<p>Second<br>line</p>"
        )

        assertEquals(2, blocks.size)
        val first = blocks[0] as PostBlock.Paragraph
        assertEquals("Hello world and link.", first.content.text)

        val bold = first.content.spans.single { it.kind == TextSpan.Kind.BOLD }
        assertEquals("world", first.content.text.substring(bold.start, bold.end))

        val link = first.content.spans.single { it.kind == TextSpan.Kind.LINK }
        assertEquals("link", first.content.text.substring(link.start, link.end))
        assertEquals("https://www.head-fi.org/threads/other.1/", link.url)

        assertEquals("Second\nline", (blocks[1] as PostBlock.Paragraph).content.text)
    }

    @Test
    fun `extracts quotes, list items and content images`() {
        val blocks = parse(
            """<blockquote class="bbCodeBlock">""" +
                """<div class="bbCodeBlock-content">Quoted text</div></blockquote>""" +
                "<ul><li>One</li><li>Two</li></ul>" +
                """<img src="https://cdn.head-fi.org/a.jpg" width="600" height="400">""" +
                """<img class="smilie" src="https://cdn.head-fi.org/s.png">""" +
                """<img src="https://cdn.head-fi.org/tiny.png" width="16" height="16">"""
        )

        assertEquals(4, blocks.size)
        assertEquals("Quoted text", (blocks[0] as PostBlock.Quote).text)
        assertEquals("• One", (blocks[1] as PostBlock.Paragraph).content.text)
        assertEquals("• Two", (blocks[2] as PostBlock.Paragraph).content.text)
        val image = blocks[3] as PostBlock.Image
        assertEquals("https://cdn.head-fi.org/a.jpg", image.url)
    }

    @Test
    fun `ignores smilies and data urls`() {
        val blocks = parse(
            """<img src="data:image/png;base64,AAAA">""" +
                """<img src="https://cdn.head-fi.org/smile.png" width="20" height="20">"""
        )

        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `parses headings, code and links with relative urls`() {
        val blocks = parse(
            "<h2>Heading</h2><pre>val x = 1</pre>" +
                """<p><a href="/forums/portable.4/">Forum</a></p>"""
        )

        val heading = blocks[0] as PostBlock.Paragraph
        assertEquals("Heading", heading.content.text)
        assertTrue(heading.content.spans.any { it.kind == TextSpan.Kind.BOLD })

        val code = blocks[1] as PostBlock.Paragraph
        assertEquals("val x = 1", code.content.text)
        assertTrue(code.content.spans.any { it.kind == TextSpan.Kind.CODE })

        val link = blocks[2] as PostBlock.Paragraph
        assertEquals(
            "https://www.head-fi.org/forums/portable.4/",
            link.content.spans.single { it.kind == TextSpan.Kind.LINK }.url
        )
    }
}
