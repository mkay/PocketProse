// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Splitting a note for the editor, and putting it back.
 *
 * The property that matters is that [Segments.join] undoes [Segments.split] exactly. Anything less
 * and every note containing an image is rewritten the moment it is opened.
 */
class SegmentsTest {

    @Test
    fun `a note with no images is a single editable segment`() {
        val body = "\nDu erreichst mich nicht\nund das ist gut so\n"
        val segments = Segments.split(body)
        assertEquals(1, segments.size)
        assertTrue(segments.single() is Segment.Prose)
        assertEquals(body, Segments.join(segments))
    }

    @Test
    fun `an image line becomes its own segment and the text around it survives`() {
        val body = "## Strophe\n![](attachments/a.png)![](attachments/b.png)\nund weiter\n"
        val segments = Segments.split(body)
        assertEquals(3, segments.size)
        assertEquals("## Strophe\n", segments[0].raw)
        val images = segments[1] as Segment.Images
        assertEquals(listOf("attachments/a.png", "attachments/b.png"), images.images.map { it.path })
        assertEquals("und weiter\n", segments[2].raw)
        assertEquals(body, Segments.join(segments))
    }

    @Test
    fun `text sharing a line with images is kept`() {
        // Casablanca (chords).md has exactly this: three diagrams and a bare "3x".
        val body = "![](a.png)![](b.png)![](c.png) 3x\n"
        val images = Segments.split(body).single() as Segment.Images
        assertEquals(3, images.images.size)
        assertEquals("3x", images.trailing)
        assertEquals(body, images.raw)
    }

    @Test
    fun `an empty body still gives something to type into`() {
        assertEquals(listOf(Segment.Prose("")), Segments.split(""))
        assertEquals("", Segments.join(Segments.split("")))
    }

    @Test
    fun `a body ending without a newline rejoins unchanged`() {
        val body = "letzte Zeile"
        assertEquals(body, Segments.join(Segments.split(body)))
    }

    @Test
    fun `a body that is nothing but an image rejoins unchanged`() {
        val body = "![](attachments/a.png)\n"
        val segments = Segments.split(body)
        assertEquals(1, segments.size)
        assertEquals(body, Segments.join(segments))
    }

    @Test
    fun `an image alt text is kept`() {
        val images = Segments.split("![Griff A](a.png)\n").single() as Segment.Images
        assertEquals("Griff A", images.images.single().alt)
    }

    @Test
    fun `a PDF link is not an image and does not split the text`() {
        // Wer geht vor.md's Anhänge list is ordinary links; only `![` starts an image.
        val body = "- [Pasted Graphic 10](attachments/wer-geht-vor-pasted-graphic-10.pdf)\n"
        assertEquals(1, Segments.split(body).size)
        assertTrue(Segments.split(body).single() is Segment.Prose)
    }
}
