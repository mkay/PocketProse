// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

import de.singular.writer.markdown.Note
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The bytes a merged note is made of.
 *
 * The second place the app writes a file from nothing, and the first where the contents come from
 * other files — so besides the parser round-trip that `NewNoteTest` pins, this pins what the merge
 * decides on the sources' behalf: the earliest date, the union of tags, the rule between parts, and
 * nothing added that nobody wrote.
 */
class MergeNoteTest {

    private val now = Instant.parse("2026-09-10T12:00:00.000Z")

    private fun note(
        title: String,
        created: String,
        tags: List<String>,
        body: String,
    ): Note {
        val list = if (tags.isEmpty()) "tags: []\n" else "tags:\n" + tags.joinToString("") { "  - \"$it\"\n" }
        return Note.parse(
            "---\ntitle: \"$title\"\ncreated: $created\nupdated: $created\n$list---\n$body",
        )
    }

    private val older = note(
        "Wer geht vor?", "2015-03-01T10:00:00.000Z", listOf("lyrics/snippet", "busch"),
        "\n#lyrics/snippet\n\nWer geht vor\nwer bleibt\n",
    )
    private val newer = note(
        "Wer geht vor?", "2021-06-15T08:30:00.000Z", listOf("lyrics/snippet", "radio"),
        "\nEine zweite Fassung\n\n\n",
    )

    @Test
    fun `the parts follow each other under one block, separated by a rule`() {
        assertEquals(
            "---\n" +
                "title: \"Wer geht vor?\"\n" +
                "created: 2015-03-01T10:00:00.000Z\n" +
                "updated: 2026-09-10T12:00:00.000Z\n" +
                "tags:\n" +
                "  - \"lyrics/snippet\"\n" +
                "  - \"busch\"\n" +
                "  - \"radio\"\n" +
                "---\n" +
                "\n" +
                "#lyrics/snippet\n" +
                "\n" +
                "Wer geht vor\n" +
                "wer bleibt\n" +
                "\n" +
                "---\n" +
                "\n" +
                "Eine zweite Fassung\n",
            Vault.mergedNoteText("Wer geht vor?", listOf(older, newer), now),
        )
    }

    @Test
    fun `the order given is the order written, and created is the earliest whichever comes first`() {
        val text = Vault.mergedNoteText("Wer geht vor?", listOf(newer, older), now)
        assertEquals("2015-03-01T10:00:00.000Z", Note.parse(text).frontmatter.created)
        assert(text.indexOf("Eine zweite Fassung") < text.indexOf("Wer geht vor\n"))
    }

    @Test
    fun `a merge survives the parser that will refuse to save it otherwise`() {
        val text = Vault.mergedNoteText("Wer geht vor?", listOf(older, newer), now)
        val parsed = Note.parse(text)
        assertEquals(text, parsed.render())
        assertEquals("Wer geht vor?", parsed.title)
        assertEquals(listOf("lyrics/snippet", "busch", "radio"), parsed.tags)
    }

    @Test
    fun `a part with no prose adds no rule`() {
        // 40 notes are a title and a tag. A rule over nothing would be the merge's own line.
        val empty = note("Die Eule", "2019-01-01T00:00:00.000Z", listOf("lyrics/titel"), "\n\n")
        val text = Vault.mergedNoteText("Wer geht vor?", listOf(older, empty, newer), now)
        assertEquals(1, Regex("^---$", RegexOption.MULTILINE).findAll(text).count() - 2)
        assertEquals(listOf("lyrics/snippet", "busch", "lyrics/titel", "radio"), Note.parse(text).tags)
    }

    @Test
    fun `no tags anywhere is the archive's own empty list`() {
        val a = note("A", "2020-01-01T00:00:00.000Z", emptyList(), "\nEins\n")
        val b = note("B", "2020-01-02T00:00:00.000Z", emptyList(), "\nZwei\n")
        val text = Vault.mergedNoteText("AB", listOf(a, b), now)
        assert("tags: []\n" in text)
        assertEquals(text, Note.parse(text).render())
    }
}
