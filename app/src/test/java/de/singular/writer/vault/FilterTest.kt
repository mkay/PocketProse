// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The library's combined filter.
 *
 * Written against [Filterable] for the same reason [SortTest] is written against `Sortable`: an
 * `IndexedNote` cannot be built off a device, its `NoteFile` needing a real `Uri`. What is under test
 * is a predicate over four properties, and naming those four is what makes it a JVM test.
 */
class FilterTest {

    private data class Row(
        override val title: String,
        override val body: String = "",
        override val tags: List<String> = emptyList(),
        override val hasAttachments: Boolean = false,
    ) : Filterable

    private val notes = listOf(
        Row("Atlantik", body = "Du erreichst mich nicht", tags = listOf("lyrics/snippet")),
        Row("Casablanca", body = "chords", tags = listOf("chords", "radio"), hasAttachments = true),
        Row("Wer geht vor?", body = "Anhänge", tags = listOf("released"), hasAttachments = true),
        Row("Adlerohr", tags = listOf("album/debut")),
    )

    private fun match(filters: Filters) =
        NoteIndex(emptyList()).matching(notes, filters).map { it.title }

    @Test
    fun `no filter is the whole folder`() {
        assertTrue(Filters().isEmpty)
        assertEquals(notes.map { it.title }, match(Filters()))
    }

    @Test
    fun `text matches the title`() {
        assertEquals(listOf("Atlantik"), match(Filters(text = "atlan")))
    }

    @Test
    fun `text matches the writing`() {
        assertEquals(listOf("Atlantik"), match(Filters(text = "erreichst")))
    }

    @Test
    fun `text matches a tag name, which having a tag picker does not make redundant`() {
        // Typing `radio` is how somebody who has never opened the tag tree looks for notes tagged
        // radio. It has always worked and taking it away would be a loss dressed as tidiness.
        assertEquals(listOf("Casablanca"), match(Filters(text = "radio")))
    }

    @Test
    fun `a tag brings its children with it`() {
        // `album/debut` is a child of `album` even though no note carries a bare `album` — the tree
        // is built from path segments, so filtering by the parent has to find the children.
        assertEquals(listOf("Adlerohr"), match(Filters(tag = "album")))
        assertEquals(listOf("Adlerohr"), match(Filters(tag = "album/debut")))
    }

    @Test
    fun `with and without partition the folder between them`() {
        val with = match(Filters(attachments = AttachmentFilter.WITH))
        val without = match(Filters(attachments = AttachmentFilter.WITHOUT))
        assertEquals(listOf("Casablanca", "Wer geht vor?"), with)
        assertEquals(listOf("Atlantik", "Adlerohr"), without)
        assertEquals(notes.size, with.size + without.size)
    }

    @Test
    fun `the three combine as and, not as or`() {
        // Casablanca is the only note that is all three at once.
        assertEquals(
            listOf("Casablanca"),
            match(Filters(text = "chord", tag = "radio", attachments = AttachmentFilter.WITH)),
        )
        // Each criterion on its own would have let something else through.
        assertTrue(match(Filters(text = "chord")).size >= 1)
        assertEquals(2, match(Filters(attachments = AttachmentFilter.WITH)).size)
    }

    @Test
    fun `a search inside a tag stays inside the tag`() {
        // The behaviour the app has always had, now visible rather than silent: the dialog shows the
        // tag while the text narrows within it.
        assertEquals(emptyList<String>(), match(Filters(text = "erreichst", tag = "radio")))
        assertEquals(listOf("Atlantik"), match(Filters(text = "erreichst", tag = "lyrics")))
    }

    @Test
    fun `whitespace is not a search`() {
        assertEquals(notes.map { it.title }, match(Filters(text = "   ")))
    }
}
