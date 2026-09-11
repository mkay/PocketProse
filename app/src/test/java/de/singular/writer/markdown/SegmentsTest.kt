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
    fun `a line of hashtags is text in the editor like any other line`() {
        // Until 2026-09-09 a run of hashtag lines was lifted out of the editable text and drawn as
        // chips, absorbing the blank lines around it so the note did not open on an empty first
        // line. Tags come from the frontmatter now, so these are words on a page: one prose
        // segment, nothing hoisted, nothing hidden. 165 notes in the archive still carry such a
        // line and will show it until the author clears them — see the Tag rules in `CLAUDE.md`.
        val head = "\n#lyrics/snippet\n\nDu erreichst mich nicht\n"
        assertEquals(listOf(Segment.Prose(head)), Segments.split(head))

        // Die Eule.md in full — 40 notes look like this, and the field is the whole note.
        val alone = "\n#lyrics/titel\n"
        assertEquals(listOf(Segment.Prose(alone)), Segments.split(alone))

        // Helen weiss das auch.md's foot, trailing spaces included: three lines, still one segment.
        val run = "und weiter\n\n#album/debut \n#100\n#album/entsetzlich #busch \n"
        assertEquals(listOf(Segment.Prose(run)), Segments.split(run))
        assertEquals(run, Segments.join(Segments.split(run)))
    }

    @Test
    fun `a heading and a sharp are text, as everything in a body now is`() {
        val body = "## Strophe\nTarantino für zwei in F# Moll\n"
        val segments = Segments.split(body)
        assertEquals(listOf(Segment.Prose(body)), segments)
    }

    @Test
    fun `a hashtag inside a sentence leaves the sentence visible`() {
        val body = "das ist #lyrics/snippet und mehr\n"
        assertEquals(listOf(Segment.Prose(body)), Segments.split(body))
    }

    @Test
    fun `a PDF link is not an image and does not split the text`() {
        // Wer geht vor.md's Anhänge list is ordinary links; only `![` starts an image.
        val body = "- [Pasted Graphic 10](attachments/wer-geht-vor-pasted-graphic-10.pdf)\n"
        assertEquals(1, Segments.split(body).size)
        assertTrue(Segments.split(body).single() is Segment.Prose)
    }

    // ===== removing a picture =====

    @Test
    fun `removing the only picture on a line removes the line`() {
        assertEquals("", Segments.withoutImage("![](attachments/a.png)\n", ImageRef("", "attachments/a.png")))
    }

    @Test
    fun `removing one of a run keeps the others and one gap between them`() {
        val line = "![](a.png) ![](b.png) ![](c.png)\n"
        assertEquals("![](a.png) ![](c.png)\n", Segments.withoutImage(line, ImageRef("", "b.png")))
        assertEquals("![](b.png) ![](c.png)\n", Segments.withoutImage(line, ImageRef("", "a.png")))
        assertEquals("![](a.png) ![](b.png)\n", Segments.withoutImage(line, ImageRef("", "c.png")))
    }

    @Test
    fun `words beside a removed picture stay`() {
        // The one line in the archive that carries text beside its pictures reads "3x".
        assertEquals("3x\n", Segments.withoutImage("![](a.png) 3x\n", ImageRef("", "a.png")))
    }

    @Test
    fun `a picture that is not on the line changes nothing`() {
        val line = "![](a.png)\n"
        assertEquals(line, Segments.withoutImage(line, ImageRef("", "z.png")))
    }
}
