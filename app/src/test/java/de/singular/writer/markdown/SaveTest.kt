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
    fun `a tag a body carries and the frontmatter does not is neither promoted nor deleted`() {
        // The archive has no note in this state — the percent rename was what emptied the category —
        // so it has to be built on purpose. It is the case that decides whether the app is allowed
        // to reconcile the two representations behind the author's back, and the answer is no: it
        // may only add the tag just added and remove the tag just removed.
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

        // Both representations are one list to a reader, and that is what the sheet shows.
        assertEquals(listOf("lyrics/snippet", "busch"), note.tags)
        // The `tags:` list is the narrower thing, and stays narrower unless the user says otherwise.
        assertEquals(listOf("lyrics/snippet"), note.frontmatter.tags)

        val saved = note.withTags(note.body, note.tags + "radio", now)!!
        // Not promoted into the `tags:` list the author never wrote it into...
        assertTrue("busch" !in saved.frontmatter.tags)
        // ...and not struck from the text the author did write it into.
        assertTrue("#busch" in saved.body)
        // The tag actually asked for reaches both places.
        assertTrue("radio" in saved.frontmatter.tags)
        assertTrue("#radio" in saved.body)
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
}
