// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Moving tags out of bodies and into frontmatter, one note at a time.
 *
 * The cases here are the archive's own shapes plus the ones a folder from another editor will bring:
 * a note with no frontmatter at all, a note whose block has no `tags:` key. The archive proves the
 * first half and cannot prove the second, so those are written for the purpose — see `CorpusTest`
 * for the same operation run over all 168 real notes.
 *
 * The hashtag grammar is tested here and nowhere else, because [Migration] is the only thing in the
 * app that reads a `#` out of a body. Its three clauses are each one note away from a mistake that
 * shows up as a wrong tag or a missing line and never as an error — see the class comment there.
 */
class MigrationTest {

    private fun note(text: String) = Note.parse(text)

    private fun moved(text: String): Migration.Outcome.Move =
        Migration.plan(note(text)) as Migration.Outcome.Move

    private val head = """
        |---
        |title: "Atlantik"
        |created: 2025-04-25T16:56:15.332Z
        |updated: 2025-04-26T15:04:16.978Z
        |tags:
        |  - "lyrics/snippet"
        |---
        |
        |#lyrics/snippet
        |
        |Du erreichst mich nicht
        |
    """.trimMargin()

    @Test
    fun `a tag line under the frontmatter goes, and takes the blank line below it`() {
        // The archive's commonest shape: 66 notes open this way. The blank line *above* stays — it
        // is the gap under the frontmatter that every note in the folder has, and losing it would
        // make this note the one file `diff` complains about.
        assertEquals("\nDu erreichst mich nicht\n", moved(head).note.body)
    }

    @Test
    fun `a tag line at the foot goes, and takes the blank line above it`() {
        val text = """
            |---
            |title: "Müde"
            |tags:
            |  - "released"
            |---
            |
            |Ich bin müde
            |
            |#released
            |
        """.trimMargin()
        assertEquals("\nIch bin müde\n", moved(text).note.body)
    }

    @Test
    fun `a note that is nothing but a tag comes out as the blank line it started with`() {
        // 39 notes in the archive are exactly this: a blank line and `#lyrics/titel`, 15 bytes. A
        // title filed under a tag is a normal kind of note here, not an edge case, and it stays one
        // — the body is emptied, not the note.
        val text = """
            |---
            |title: "Die Eule"
            |tags:
            |  - "lyrics/titel"
            |---
            |
            |#lyrics/titel
            |
        """.trimMargin()
        assertEquals("\n", moved(text).note.body)
    }

    @Test
    fun `a tag line pressed against the words loses only itself`() {
        // No blank line to take, so none is invented and none of the words move.
        val text = "---\ntags:\n  - \"song\"\n---\nIch singe\n#song\nnoch immer\n"
        assertEquals("Ich singe\nnoch immer\n", moved(text).note.body)
    }

    @Test
    fun `tag lines in more than one place all go`() {
        // 12 notes carry them at the head and at the foot both. One left behind would show up again
        // the next time the folder was read, and the offer would never stop being made.
        val text = """
            |---
            |title: "Radio"
            |tags:
            |  - "radio"
            |  - "song"
            |---
            |
            |#radio
            |
            |Es spielt
            |
            |#song
            |
        """.trimMargin()
        assertEquals("\nEs spielt\n", moved(text).note.body)
    }

    @Test
    fun `several tags on one line all come across`() {
        val text = "---\ntitle: \"X\"\ntags: []\n---\n\n#chords #radio #busch\n\nAkkorde\n"
        val move = moved(text)
        assertEquals(listOf("chords", "radio", "busch"), move.filed)
        assertEquals(listOf("chords", "radio", "busch"), move.note.tags)
    }

    // --- the grammar, and the three notes that make each clause load-bearing -------------------

    @Test
    fun `a sharp is a sharp`() {
        // `Radio (Song Notes).md` reads "Tarantino für zwei in F# Moll" — the only sharp in 168
        // notes, and one note away from a tag called `#` appearing in the drawer.
        val text = "---\ntitle: \"Radio\"\ntags: []\n---\n\nTarantino für zwei in F# Moll\n"
        assertEquals(Migration.Outcome.Untouched, Migration.plan(note(text)))
    }

    @Test
    fun `a heading is a heading`() {
        // Five notes use `##`. A migration that read `## Strophe` as a tag would file a tag called
        // `Strophe` and delete the heading in the same move.
        val text = "---\ntitle: \"X\"\ntags: []\n---\n\n## Strophe\n\nEine Zeile\n"
        assertEquals(Migration.Outcome.Untouched, Migration.plan(note(text)))
        assertFalse(Migration.isTagLine("## Strophe"))
    }

    @Test
    fun `a tag may begin with a digit`() {
        // `100`, `50` and `75` — see tools/rename-percent-tags.py. They were `100%` until the
        // rename, which is a name no hashtag can hold, and the whole point of dropping the `%` was
        // that they became ordinary.
        val text = "---\ntitle: \"X\"\ntags: []\n---\n\n#100\n\nFertig\n"
        assertEquals(listOf("100"), moved(text).filed)
    }

    @Test
    fun `a hashtag inside a sentence is part of the sentence`() {
        // Not moved, and not filed either. The app cannot tell a tag somebody wrote mid-line from a
        // word somebody wrote a `#` in front of, and guessing wrong in either direction edits a
        // lyric. Being strict costs nothing: all 179 hashtags in the archive sit on their own line.
        val text = "---\ntitle: \"X\"\ntags: []\n---\n\nEin #Traum von einem Tag\n"
        assertEquals(Migration.Outcome.Untouched, Migration.plan(note(text)))
    }

    @Test
    fun `tags are folded to lowercase on the way in`() {
        // The archive was deliberately case-folded once; a `Lyrics` beside the existing `lyrics`
        // would split a tag in the drawer and look like nothing but a bug.
        val text = "---\ntitle: \"X\"\ntags: []\n---\n\n#Lyrics/Snippet\n"
        assertEquals(listOf("lyrics/snippet"), moved(text).filed)
    }

    // --- what happens to the frontmatter --------------------------------------------------------

    @Test
    fun `a tag the list already carries is unwritten from the body and nothing else`() {
        // Every one of the archive's 165 tagged notes is this case: the body says what the list
        // already says. Rewriting the `tags:` block to say it again would be touching a file the
        // app was not asked to change, so the block comes through byte-identical.
        val move = moved(head)
        assertEquals(emptyList<String>(), move.filed)
        assertEquals(listOf("lyrics/snippet"), move.refiled)
        assertEquals(note(head).frontmatter.raw, move.note.frontmatter.raw)
    }

    @Test
    fun `a new tag is appended to the list, keeping the order the note had`() {
        val text = """
            |---
            |title: "X"
            |tags:
            |  - "released"
            |---
            |
            |#released #busch
            |
            |Wörter
            |
        """.trimMargin()
        val move = moved(text)
        assertEquals(listOf("busch"), move.filed)
        assertEquals(listOf("released", "busch"), move.note.tags)
        // The existing entry keeps its own line, quoting and indentation; the new one copies them.
        assertTrue(move.note.frontmatter.raw.contains("  - \"released\"\n  - \"busch\"\n"))
    }

    @Test
    fun `updated does not move, because filing is not writing`() {
        // The body changes and the timestamp does not. `CLAUDE.md`'s rule, and the reason the
        // archive is worth keeping: stamping here would set 165 notes to today and destroy the
        // 2015–2025 span in one confirmation.
        val text = """
            |---
            |title: "X"
            |created: 2015-03-02T09:00:00.000Z
            |updated: 2016-07-11T12:30:00.000Z
            |tags:
            |  - "released"
            |---
            |
            |#released #busch
            |
            |Wörter
            |
        """.trimMargin()
        val move = moved(text)
        assertEquals("2016-07-11T12:30:00.000Z", move.note.frontmatter.updated)
        assertEquals("2015-03-02T09:00:00.000Z", move.note.frontmatter.created)
        // And the stamp Note.withBody would have applied is nowhere in the file.
        assertFalse(Note.stamp(Instant.parse("2026-09-09T10:00:00Z")) in move.note.render())
    }

    @Test
    fun `an empty list becomes a real one`() {
        val text = "---\ntitle: \"X\"\ntags: []\n---\n\n#song\n\nWörter\n"
        assertEquals("---\ntitle: \"X\"\ntags:\n  - \"song\"\n---\n", moved(text).note.frontmatter.raw)
    }

    @Test
    fun `a block with no tags key gets one, after everything already there`() {
        // Not a shape the archive has — every note carries the key — but the shape a folder from
        // another editor arrives in. Appending is the only placement that cannot disturb the order
        // of what is already in the block.
        val text = "---\ntitle: \"X\"\ncreated: 2015-03-02T09:00:00.000Z\n---\n\n#song\n\nWörter\n"
        val move = moved(text)
        assertEquals(
            "---\ntitle: \"X\"\ncreated: 2015-03-02T09:00:00.000Z\ntags:\n  - \"song\"\n---\n",
            move.note.frontmatter.raw,
        )
        assertFalse(move.addedBlock)
    }

    @Test
    fun `a note with no frontmatter at all gets a block with nothing but its tags in it`() {
        // The case this whole file exists for: a note from an editor that had no frontmatter, where
        // the hashtag was the only filing there was. Nothing app-owned goes into the block it gets —
        // no id, no created, no updated.
        val move = moved("#song #released\n\nIch singe\n")
        assertTrue(move.addedBlock)
        assertEquals(listOf("song", "released"), move.note.tags)
        assertEquals("---\ntags:\n  - \"song\"\n  - \"released\"\n---\n\nIch singe\n", move.note.render())
    }

    @Test
    fun `a created block keeps the body's own opening blank line rather than adding a second`() {
        val move = moved("#song\n\nIch singe\n")
        assertEquals("---\ntags:\n  - \"song\"\n---\n\nIch singe\n", move.note.render())
    }

    @Test
    fun `every note that loses lines has had its tags land first`() {
        // Outcome.Blocked has no reachable trigger against the shapes this app can parse — a block
        // with no `tags:` key gets one, a note with no block at all gets a block. It is kept anyway,
        // because it is the check that makes stripping safe rather than a case that has come up: the
        // strip runs only after the tags have been read back out of the frontmatter that was just
        // built. This asserts the property that check exists to hold.
        val texts = listOf(
            head,
            "#song\n\nIch singe\n",
            "---\ntitle: \"X\"\ncreated: 2015-03-02T09:00:00.000Z\n---\n\n#song\n",
            "---\ntitle: \"X\"\ntags: []\n---\n\n#busch #radio\n\nWörter\n",
        )
        for (text in texts) {
            val move = moved(text)
            val before = Migration.plan(note(text))
            assertTrue("$text was planned twice differently", before is Migration.Outcome.Move)
            assertTrue(
                "a line went without its tag landing: $text",
                (move.filed + move.refiled).all { it in move.note.tags },
            )
        }
    }

    // --- the shape of the offer -----------------------------------------------------------------

    @Test
    fun `a note with no hashtags is untouched, and says so`() {
        val text = "---\ntitle: \"X\"\ntags:\n  - \"song\"\n---\n\nIch singe\n"
        assertEquals(Migration.Outcome.Untouched, Migration.plan(note(text)))
    }

    @Test
    fun `the survey counts what the dialog has to say out loud`() {
        val notes = listOf(
            note(head),
            note("---\ntitle: \"X\"\ntags: []\n---\n\n#busch #radio\n\nWörter\n"),
            note("---\ntitle: \"Y\"\ntags:\n  - \"song\"\n---\n\nNur Wörter\n"),
        )
        val survey = Migration.survey(notes)
        assertEquals(3, survey.scanned)
        assertEquals(2, survey.notes)
        assertEquals(2, survey.lines)
        assertEquals(listOf("busch", "lyrics/snippet", "radio"), survey.tags)
        // `lyrics/snippet` is already declared by the note that carries it, so the drawer does not
        // gain it — it gains the two that live only in a body today.
        assertEquals(listOf("busch", "radio"), survey.gained)
        assertEquals(0, survey.blocked)
        assertTrue(survey.worthOffering)
    }

    @Test
    fun `a folder with nothing to move is not worth offering`() {
        val notes = listOf(note("---\ntitle: \"X\"\ntags:\n  - \"song\"\n---\n\nIch singe\n"))
        assertFalse(Migration.survey(notes).worthOffering)
    }

    // --- the bytes ------------------------------------------------------------------------------

    @Test
    fun `CRLF survives the move`() {
        // A note that arrived from elsewhere with CRLF must come back out with CRLF; converting a
        // whole file's line endings is exactly the gratuitous rewrite the prime directive forbids.
        val text = "---\r\ntitle: \"X\"\r\ntags: []\r\n---\r\n\r\n#song\r\n\r\nIch singe\r\n"
        val move = moved(text)
        assertEquals("\r\nIch singe\r\n", move.note.body)
        assertTrue(move.note.frontmatter.raw.endsWith("---\r\n"))
    }

    @Test
    fun `a file that ended without a newline keeps the one that separated its last two lines`() {
        // The tag line goes and the line above it keeps its own terminator, which is the byte that
        // was already there. The body now ends with a newline where it did not before — that is the
        // removal of a line, not the addition of anything, and trimming it would be deleting a byte
        // the author wrote.
        assertEquals("Ich singe\n", moved("---\ntitle: \"X\"\ntags: []\n---\nIch singe\n#song").note.body)
    }

    @Test
    fun `planning twice is planning once`() {
        // The offer can be made again after a sync brings in more notes, so running it over a folder
        // that has already been migrated must be a no-op rather than a second edit.
        val once = moved(head).note
        assertEquals(Migration.Outcome.Untouched, Migration.plan(once))
    }

    @Test
    fun `the note comes back whole, so the bytes that were checked are the bytes that get saved`() {
        val move = moved(head)
        assertEquals(move.note.frontmatter.raw + move.note.body, move.note.render())
        assertNull("nothing here stamps a note", move.note.frontmatter.value("markleaf_id"))
    }
}
