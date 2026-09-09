// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import de.singular.writer.markdown.Note
import de.singular.writer.markdown.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The editor's own state, and the identity it carries so a save cannot land in the wrong file.
 *
 * Written after a real one did. `Atlantik.md` lost `lyrics/snippet` from its `tags:` list on
 * 2026-09-07 because the document had been rebuilt from an index that had not caught up, came back
 * with no tags, and was then saved — and an empty tag set is a legitimate thing to save, so nothing
 * downstream could tell it from the writer unchecking everything. The defence has to be here, where
 * the state is, rather than in the write path, which cannot know where the state came from.
 */
class NoteDocumentTest {

    private val text = """
        ---
        title: "Atlantik"
        created: 2025-04-25T16:56:15.332Z
        updated: 2025-04-26T15:04:16.978Z
        tags:
          - "lyrics/snippet"
        ---

        #lyrics/snippet

        Du erreichst mich nicht
    """.trimIndent() + "\n"

    private fun documentFor(note: Note, name: String = "Atlantik.md") =
        NoteDocument(name, note.body, note.tags, note.title.orEmpty())

    @Test
    fun `a document opens with the tags its note carries`() {
        val document = documentFor(Note.parse(text))
        assertEquals(listOf("lyrics/snippet"), document.tags)
    }

    @Test
    fun `adding a tag keeps the ones already there`() {
        // The regression itself: the note came back with `tags: ["100"]` and lost what it had.
        val note = Note.parse(text)
        val document = documentFor(note)
        document.toggleTag("100")
        assertEquals(listOf("lyrics/snippet", "100"), document.tags)

        val saved = note.withTags(document.body(), document.tags, NOW)!!
        assertEquals(listOf("lyrics/snippet", "100"), saved.frontmatter.tags)
        // The frontmatter is the only place a tag is written. The body is the song.
        assertEquals(note.body, saved.body)
    }

    @Test
    fun `a tag added and taken off again is nothing to write`() {
        val note = Note.parse(text)
        val document = documentFor(note)
        document.toggleTag("100")
        document.toggleTag("100")
        assertEquals(listOf("lyrics/snippet"), document.tags)
        // Back where it started, so there is nothing to write at all.
        assertEquals(null, note.withTags(document.body(), document.tags, NOW))
    }

    @Test
    fun `input is folded to lowercase on the way in`() {
        val document = documentFor(Note.parse(text))
        document.toggleTag("#Busch")
        assertEquals(listOf("lyrics/snippet", "busch"), document.tags)
    }

    @Test
    fun `a hashtag written in the body is not a tag on the note`() {
        // It used to show as on, because to a reader it was written in the note. Since the
        // frontmatter became the only source of truth it is what it looks like: text. The chip sheet
        // shows `lyrics/snippet` and nothing else, and toggling tags will not disturb that line.
        val strayText = text.replace("#lyrics/snippet\n", "#lyrics/snippet #busch\n")
        val document = documentFor(Note.parse(strayText))
        assertEquals(listOf("lyrics/snippet"), document.tags)
    }

    @Test
    fun `a document carries the name of the file it was built from`() {
        // What `saveOpenNote` compares before writing. A document whose name does not match the note
        // being saved is one composition's editor pointed at another composition's file.
        assertEquals("Atlantik.md", documentFor(Note.parse(text)).name)
        assertEquals("Wer geht vor.md", documentFor(Note.parse(text), "Wer geht vor.md").name)
    }

    @Test
    fun `a note can open with something other than text`() {
        // A chord sheet opening on a diagram: segment 0 is an image with no buffer behind it, so
        // anything reaching for "the first buffer" by index rather than by kind crashes on it — as
        // the format bar once did. Tag runs used to be the common case here, 66 of them at the head
        // of their note; since tags left the body it is the images that carry this.
        val document = NoteDocument(
            "Casablanca (chords).md",
            "![](attachments/a.png)\nAm\n",
            emptyList(),
            "Casablanca",
        )
        assertTrue(document.segments.first() is Segment.Images)
        assertEquals(1, document.firstProseIndex)
    }

    @Test
    fun `a note that is nothing but an image has no editable text at all`() {
        // Not a defect: there is genuinely nothing to type into. Whatever draws the fields has to
        // cope with finding none rather than assuming there is at least one.
        val document = NoteDocument("Casablanca (chords).md", "![](attachments/a.png)\n", emptyList(), "Casablanca")
        assertEquals(-1, document.firstProseIndex)
    }

    companion object {
        private val NOW: Instant = Instant.parse("2026-09-07T19:40:28.597Z")
    }

    // --- the blank line under the frontmatter ---------------------------------------------------

    @Test
    fun `the gap under the frontmatter is not shown as an empty first line`() {
        // A note's body begins right after the closing `---`, so the gap every note in the archive
        // has there is the body's first character. Shown as written it is an empty line above the
        // words — and with the title in a field of its own it separates nothing from anything.
        val document = documentFor(Note.parse(text))
        val shown = document.segments.filterIsInstance<Segment.Prose>().first().raw
        assertTrue("the editor opened on a blank line: ${shown.take(20)}", shown.startsWith("#lyrics/snippet"))
    }

    @Test
    fun `and it is put back, so opening a note and closing it writes nothing`() {
        val note = Note.parse(text)
        val document = documentFor(note)
        assertEquals(note.body, document.body())
        assertNull("an untouched note was written", note.withBody(document.body(), NOW))
    }

    @Test
    fun `a note that arrived without the gap is not given one`() {
        // The two notes the author hand-corrected before this was built. A property the file already
        // had is restored; one it never had is not imposed — the same bargain as the trailing
        // newline at the other end of the file.
        val note = Note.parse("---\ntitle: \"Algebra\"\ntags: []\n---\nEins ist die größte Zahl\n")
        val document = documentFor(note, "Algebra.md")
        assertEquals("Eins ist die größte Zahl\n", document.segments.first().raw)
        assertEquals(note.body, document.body())
    }

    @Test
    fun `a blank first line the author actually types is kept`() {
        // They see one blank line and the file holds two: the frontmatter's own, plus theirs. The
        // hidden line is structure, and it never eats a line somebody wrote.
        val note = Note.parse(text)
        val document = documentFor(note)
        val buffer = document.bufferAt(document.firstProseIndex)
        buffer.setTextAndPlaceCursorAtEnd("\n" + buffer.text)
        assertEquals("\n" + note.body, document.body())

        // And it is still there the next time the note is opened: the file holds two blank lines,
        // the editor hides the frontmatter's and shows theirs, and reopening writes nothing.
        val saved = note.withBody(document.body(), NOW)!!
        assertEquals("\n" + note.body, saved.body)
        val reopened = documentFor(saved)
        assertTrue(reopened.segments.first().raw.startsWith("\n#lyrics/snippet"))
        assertEquals(saved.body, reopened.body())
        assertNull("reopening rewrote the note", saved.withBody(reopened.body(), NOW))
    }

    @Test
    fun `an emptied note keeps the gap and nothing else`() {
        val note = Note.parse(text)
        val document = documentFor(note)
        document.bufferAt(document.firstProseIndex).setTextAndPlaceCursorAtEnd("")
        assertTrue(document.isEmpty)
        assertEquals("\n", document.body())
    }
}
