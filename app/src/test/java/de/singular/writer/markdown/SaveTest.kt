// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * What a save does to a note's bytes.
 *
 * The rule this file exists to defend: **the app must not touch a file it did not need to write.**
 * The archive's value is its dates, and an editor that stamps `updated` whenever a note is opened
 * and closed would rewrite all 168 within a week of ordinary reading.
 */
class SaveTest {

    private val now = Instant.parse("2026-09-07T14:30:00.123Z")

    private val text = """
        ---
        title: "Atlantik"
        created: 2025-04-25T16:56:15.332Z
        updated: 2025-04-26T15:04:16.978Z
        tags:
          - "lyrics/snippet"
        ---

        Du erreichst mich nicht
    """.trimIndent() + "\n"

    @Test
    fun `a hashtag left in a body is words, not a tag, and no edit touches it`() {
        // This note used to be the hard case: two representations, disagreeing, and the app forbidden
        // to reconcile them behind the author's back. Since 2026-09-09 there is only one
        // representation and the question dissolves — `#busch` in a body is a word in a song. It is
        // still worth a test, because 165 notes in the archive carry such a line and the app must
        // leave every one of them exactly as it found it.
        val text = """
            ---
            title: "Atlantik"
            created: 2025-04-25T16:56:15.332Z
            updated: 2025-04-26T15:04:16.978Z
            tags:
              - "lyrics/snippet"
            ---

            Du erreichst mich nicht

            #lyrics/snippet #busch
        """.trimIndent() + "\n"
        val note = Note.parse(text)

        // The frontmatter is the whole answer. `busch` is not a tag on this note.
        assertEquals(listOf("lyrics/snippet"), note.tags)

        val saved = note.withTags(note.body, note.tags + "radio", now)!!
        // The tag asked for reaches the frontmatter...
        assertTrue("radio" in saved.frontmatter.tags)
        // ...and the body is not touched at all — not the leftover line, not a byte of it.
        assertEquals(note.body, saved.body)
        assertTrue("#lyrics/snippet #busch" in saved.body)
    }

    @Test
    fun `an unchanged body is not a save at all`() {
        val note = Note.parse(text)
        assertNull(note.withBody(note.body, now))
    }

    @Test
    fun `a real edit moves updated and nothing else`() {
        val note = Note.parse(text)
        val saved = note.withBody("\nGanz andere Worte\n", now)
        assertNotNull(saved)
        saved!!

        assertEquals("2026-09-07T14:30:00.123Z", saved.frontmatter.updated)
        // created is never touched — it is the archive's whole value.
        assertEquals("2025-04-25T16:56:15.332Z", saved.frontmatter.created)
        assertEquals("Atlantik", saved.frontmatter.title)
        assertEquals(listOf("lyrics/snippet"), saved.frontmatter.tags)
        assertEquals("\nGanz andere Worte\n", saved.body)
    }

    @Test
    fun `a note keeps the final newline it arrived with`() {
        // All 168 notes end with one. The editor drops it the moment the cursor sits at the very
        // end and someone types, and the note becomes the only file in the folder that diff
        // complains about. Found on the device, on the first note ever written by this app.
        val note = Note.parse(text)
        val saved = note.withBody("\nDu erreichst mich nicht\nok", now)!!
        assertTrue(saved.body.endsWith("\n"))
        assertTrue(saved.render().endsWith("ok\n"))
    }

    @Test
    fun `deleting only the final newline is not a change worth writing`() {
        val note = Note.parse(text)
        assertNull(note.withBody(note.body.dropLast(1), now))
    }

    @Test
    fun `a note that arrived without a final newline does not gain one`() {
        val noNewline = "---\ntitle: \"x\"\nupdated: 2020-01-01T00:00:00.000Z\ntags: []\n---\nold"
        val saved = Note.parse(noNewline).withBody("new", now)!!
        assertTrue(!saved.render().endsWith("\n"))
        assertEquals("new", saved.body)
    }

    @Test
    fun `the stamp matches the spelling every note in the archive already uses`() {
        // ISO 8601 UTC, milliseconds, literal Z. Not "close enough" — a different spelling in one
        // note is a difference the author would have to look at forever.
        assertEquals("2026-09-07T14:30:00.123Z", Note.stamp(now))
        assertTrue(Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$""").matches(Note.stamp(now)))
        assertEquals("2026-01-01T00:00:00.000Z", Note.stamp(Instant.parse("2026-01-01T00:00:00Z")))
    }

    @Test
    fun `only the updated line differs from the original bytes`() {
        val note = Note.parse(text)
        val saved = note.withBody("\nneu\n", now)!!
        val before = text.lines()
        val after = saved.render().lines()
        val changed = before.indices.filter { before.getOrNull(it) != after.getOrNull(it) }
        // Line 3 is `updated:`; everything after the frontmatter is the new body.
        assertEquals("updated", before[changed.first()].substringBefore(':'))
        assertTrue(changed.first() == 3)
    }

    @Test
    fun `a note with an unknown key keeps it through a save`() {
        val odd = "---\ntitle: \"x\"\nlegacy_id: 'ABC-123'\nupdated: 2020-01-01T00:00:00.000Z\ntags: []\n---\nold\n"
        val saved = Note.parse(odd).withBody("new\n", now)!!
        assertTrue(saved.render().contains("legacy_id: 'ABC-123'"))
        assertEquals("new\n", saved.body)
        assertEquals("2026-09-07T14:30:00.123Z", saved.frontmatter.updated)
    }

    @Test
    fun `a note with no frontmatter is saved without one being invented`() {
        val plain = "just some words\n"
        val saved = Note.parse(plain).withBody("more words\n", now)!!
        assertEquals("more words\n", saved.render())
        assertTrue("no frontmatter may be added", !saved.render().startsWith("---"))
    }
    @Test
    fun `retyping the same title writes nothing`() {
        val note = Note.parse(text)
        assertNull(note.withTags(note.body, note.tags, now, "Atlantik"))
    }

    @Test
    fun `a blank title is somebody halfway through typing, not an erasure`() {
        val note = Note.parse(text)
        // Every note in the archive has a title, and a note that lost its own would be a blank row
        // in the list with nothing to say which file it is.
        assertNull(note.withTags(note.body, note.tags, now, "   "))
        assertNull(note.withTags(note.body, note.tags, now, ""))
    }

    @Test
    fun `a new title leaves both dates alone`() {
        val note = Note.parse(text)
        val saved = note.withTags(note.body, note.tags, now, "Atlantik II")!!

        assertEquals("Atlantik II", saved.title)
        assertEquals(note.body, saved.body)
        assertTrue(saved.render().contains("created: 2025-04-25T16:56:15.332Z"))
        // `updated` tracks the writing, not the filing — see the file format contract in
        // `CLAUDE.md`. Retitling a note is not a claim that its words were rewritten today.
        assertTrue(saved.render().contains("updated: 2025-04-26T15:04:16.978Z"))
        // The tags are untouched: a title is not a reason to rewrite a list.
        assertTrue(saved.render().contains("  - \"lyrics/snippet\""))
    }

    @Test
    fun `the filename is not the app's business, so a title may say anything`() {
        val note = Note.parse(text)
        // 11 titles in the archive end in a `?` their filenames cannot hold. Nothing here knows
        // about files at all — which is the point: renaming one is forbidden.
        val saved = note.withTags(note.body, note.tags, now, "Wer geht vor?")!!
        assertEquals("Wer geht vor?", saved.title)
        assertEquals("title: \"Wer geht vor?\"", saved.render().lines()[1])
    }

    @Test
    fun `a title with a quote or a backslash survives being written and read again`() {
        val note = Note.parse(text)
        val awkward = """He said "no" \ twice"""
        val saved = note.withTags(note.body, note.tags, now, awkward)!!

        assertEquals(awkward, saved.title)
        // And again, from the bytes rather than from the object: the escaping has to be the exact
        // inverse of the unescaping, or a quote breeds a pair every time the note is saved.
        assertEquals(awkward, Note.parse(saved.render()).title)
        assertEquals(awkward, Note.parse(Note.parse(saved.render()).withTags(note.body, note.tags, now, awkward)?.render() ?: saved.render()).title)
    }

    @Test
    fun `an unquoted title line gains quotes only when the value needs them`() {
        val bare = """
            ---
            title: Atlantik
            created: 2025-04-25T16:56:15.332Z
            updated: 2025-04-26T15:04:16.978Z
            tags: []
            ---

            Du erreichst mich nicht
        """.trimIndent() + "\n"
        val note = Note.parse(bare)

        // Nothing to be afraid of: the line keeps the shape the file gave it.
        val plain = note.withTags(note.body, note.tags, now, "Nordsee")!!
        assertEquals("title: Nordsee", plain.render().lines()[1])

        // A colon would make YAML read a mapping, so the value is quoted despite the line's style.
        // No note in the archive is written this way; a file from somewhere else may well be.
        val risky = note.withTags(note.body, note.tags, now, "Atlantik: doch")!!
        assertEquals("""title: "Atlantik: doch"""", risky.render().lines()[1])
        assertEquals("Atlantik: doch", Note.parse(risky.render()).title)
    }

}
