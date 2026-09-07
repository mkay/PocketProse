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
    fun `a tag line at the head of a note leaves the text, and its blank lines go with it`() {
        // The shape 66 runs in the archive have: blank, tags, blank, then the song.
        val body = "\n#lyrics/snippet\n\nDu erreichst mich nicht\n"
        val segments = Segments.split(body)
        assertEquals(2, segments.size)
        val tags = segments[0] as Segment.Tags
        assertEquals(listOf("lyrics/snippet"), tags.tags)
        assertEquals("\n#lyrics/snippet\n\n", tags.raw)
        // The editor opens on the words, not on an empty first line.
        assertEquals("Du erreichst mich nicht\n", segments[1].raw)
        assertEquals(body, Segments.join(segments))
    }

    @Test
    fun `a tag line at the foot takes the blank line before it`() {
        val body = "Du erreichst mich nicht\n\n#lyrics/snippet\n"
        val segments = Segments.split(body)
        assertEquals(2, segments.size)
        assertEquals("Du erreichst mich nicht\n", segments[0].raw)
        assertEquals("\n#lyrics/snippet\n", segments[1].raw)
        assertEquals(body, Segments.join(segments))
    }

    @Test
    fun `consecutive tag lines are one run, not three chip rows`() {
        // Helen weiss das auch.md ends with exactly this, trailing spaces included.
        val body = "und weiter\n\n#album/debut \n#100\n#album/entsetzlich #busch \n"
        val segments = Segments.split(body)
        assertEquals(2, segments.size)
        val tags = segments[1] as Segment.Tags
        assertEquals(listOf("album/debut", "100", "album/entsetzlich", "busch"), tags.tags)
        assertEquals(body, Segments.join(segments))
    }

    @Test
    fun `a tag line directly under prose keeps the line break above it`() {
        // Nein, T wie taub.md and Wer nicht will 3.md: no blank line between the two.
        val body = "Hanse statt Gertrud \n#lyrics/snippet\n"
        val segments = Segments.split(body)
        assertEquals("Hanse statt Gertrud \n", segments[0].raw)
        assertEquals("#lyrics/snippet\n", segments[1].raw)
        assertEquals(body, Segments.join(segments))
    }

    @Test
    fun `a tag run between two paragraphs keeps the break that follows it`() {
        // Themen.md has tags at the head and again at the foot with prose between.
        val body = "A\n\n#lyrics/themen\n\nB\n"
        val segments = Segments.split(body)
        assertEquals(3, segments.size)
        assertEquals("A\n", segments[0].raw)
        assertEquals("\n#lyrics/themen\n", segments[1].raw)
        // Welding A and B together on screen would misrepresent the note.
        assertEquals("\nB\n", segments[2].raw)
        assertEquals(body, Segments.join(segments))
    }

    @Test
    fun `a note that is nothing but its tag line still gives somewhere to type`() {
        // Die Eule.md in full: 40 notes in the archive look like this.
        val body = "\n#lyrics/titel\n"
        val segments = Segments.split(body)
        assertEquals(2, segments.size)
        assertEquals("\n#lyrics/titel\n", segments[0].raw)
        assertEquals(Segment.Prose(""), segments[1])
        assertEquals(body, Segments.join(segments))
    }

    @Test
    fun `a heading is not a tag line and a sharp does not make one`() {
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
    fun `a numeric tag is a tag line like any other`() {
        val tags = Segments.split("\n#100\n").first() as Segment.Tags
        assertEquals(listOf("100"), tags.tags)
    }

    @Test
    fun `a PDF link is not an image and does not split the text`() {
        // Wer geht vor.md's Anhänge list is ordinary links; only `![` starts an image.
        val body = "- [Pasted Graphic 10](attachments/wer-geht-vor-pasted-graphic-10.pdf)\n"
        assertEquals(1, Segments.split(body).size)
        assertTrue(Segments.split(body).single() is Segment.Prose)
    }
}
