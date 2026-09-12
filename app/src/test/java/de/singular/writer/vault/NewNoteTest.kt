// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

import de.singular.writer.markdown.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The bytes a new note is made of.
 *
 * The one place the app writes a file from nothing, so it is the one place it could invent a
 * convention — an id, a marker, a key of its own — which `CLAUDE.md` forbids outright and which is
 * how every editor the author tried before this one failed.
 *
 * The subtler requirement is that the result must survive the app's own parser: `Vault.save` refuses
 * any note it cannot reproduce byte-for-byte, so a template that is slightly off would make every
 * new note permanently read-only, and it would look like a mystery rather than like a bug.
 */
class NewNoteTest {

    private val now = Instant.parse("2026-09-07T21:15:30.250Z")

    @Test
    fun `a new note is the archive's own shape, and nothing else`() {
        assertEquals(
            "---\n" +
                "title: \"Atlantik\"\n" +
                "created: 2026-09-07T21:15:30.250Z\n" +
                "updated: 2026-09-07T21:15:30.250Z\n" +
                "tags: []\n" +
                "---\n\n",
            Vault.newNoteText("Atlantik", now),
        )
    }

    @Test
    fun `a new note survives the parser that will refuse to save it otherwise`() {
        val text = Vault.newNoteText("Atlantik", now)
        val note = Note.parse(text)
        assertEquals(text, note.render())
        assertEquals("Atlantik", note.title)
        assertEquals(emptyList<String>(), note.tags)
        // `tags: []` and not a missing key, which is what the three untagged notes in the archive
        // carry — so adding the first tag has a list to extend rather than a key to invent.
        assertTrue("tags: []" in text)
    }

    @Test
    fun `shared text goes in whole, after the gap, and round-trips`() {
        val text = Vault.newNoteText("Marie", now, "Marie, nie\r\nDu erreichst mich nicht")
        assertTrue(text.endsWith("---\n\nMarie, nie\nDu erreichst mich nicht\n"))
        val note = Note.parse(text)
        assertEquals(text, note.render())
        assertEquals("Marie", note.title)
    }

    @Test
    fun `a shared body opening on a rule does not become frontmatter`() {
        val text = Vault.newNoteText("x", now, "---\nnoch was\n")
        val note = Note.parse(text)
        assertEquals(text, note.render())
        assertEquals("x", note.title)
        assertEquals("---\nnoch was\n", note.body.removePrefix("\n"))
    }

    @Test
    fun `the proposed title is the subject, else the first line with words on it`() {
        assertEquals("Betreff", Vault.proposedTitle("erste Zeile\n", "Betreff"))
        assertEquals("erste Zeile", Vault.proposedTitle("\n  \nerste Zeile\nzweite", "  "))
        assertEquals("", Vault.proposedTitle("\n", null))
    }

    @Test
    fun `the title keeps what a filename could not`() {
        // 11 notes in the archive are titled with a trailing `?`, which no filename can hold. A new
        // one may be too: the title is authoritative and is never derived from the name.
        val note = Note.parse(Vault.newNoteText("Wer geht vor?", now))
        assertEquals("Wer geht vor?", note.title)
    }

    @Test
    fun `a quote in a title is escaped rather than breaking the block`() {
        val text = Vault.newNoteText("Sag \"nein\"", now)
        assertEquals(text, Note.parse(text).render())
        assertEquals("Sag \"nein\"", Note.parse(text).title)
    }

    @Test
    fun `created and updated start out equal, and are the archive's spelling`() {
        val note = Note.parse(Vault.newNoteText("Atlantik", now))
        assertEquals(note.frontmatter.created, note.frontmatter.updated)
        assertEquals("2026-09-07T21:15:30.250Z", note.frontmatter.created)
    }

    @Test
    fun `a new note is empty, and empty is a kind of note here`() {
        // 40 of the 168 have no body at all. A new one starts as one of those, and the library row
        // and the editor both already know what to do with it.
        val note = Note.parse(Vault.newNoteText("Die Eule", now))
        assertEquals("\n", note.body)
    }
}
