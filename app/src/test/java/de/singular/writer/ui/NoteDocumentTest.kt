// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import de.singular.writer.markdown.Note
import de.singular.writer.markdown.Segment
import org.junit.Assert.assertEquals
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
        assertTrue("#lyrics/snippet #100" in saved.body)
    }

    @Test
    fun `a tag is taken off both places, and only that tag`() {
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
    fun `a tag written in the body but not declared is an ordinary tag on the note`() {
        // It shows as on, because to a reader it is on: it is written in the note. What the app must
        // not do is *file* it — see the paired test in SaveTest, which proves an unrelated edit
        // leaves it in the body and out of the `tags:` list.
        val strayText = text.replace("#lyrics/snippet\n", "#lyrics/snippet #busch\n")
        val document = documentFor(Note.parse(strayText))
        assertEquals(listOf("lyrics/snippet", "busch"), document.tags)
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
        // 66 tag runs in the archive sit at the head of their note, so segment 0 is a run of tags
        // with no buffer behind it. Anything reaching for "the first buffer" by index rather than by
        // kind crashes on those, which the format bar once did.
        val document = documentFor(Note.parse(text))
        assertTrue(document.segments.first() is Segment.Tags)
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
}
