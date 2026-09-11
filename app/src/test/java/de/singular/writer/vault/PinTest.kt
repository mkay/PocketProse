// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pin: notes under one tag moved to the front of an already ordered list.
 *
 * Against [Filterable], like [FilterTest], because the tags are all it reads.
 */
class PinTest {

    private data class Row(
        override val title: String,
        override val tags: List<String> = emptyList(),
        override val body: String = "",
        override val hasAttachments: Boolean = false,
    ) : Filterable

    private fun pinned(rows: List<Row>, tag: String) =
        NoteIndex(emptyList()).pinnedFirst(rows, tag).map { it.title }

    @Test
    fun `pinned notes come first, each half in the order it arrived`() {
        val rows = listOf(
            Row("neu"),
            Row("set", tags = listOf("pinned")),
            Row("alt"),
            Row("chords", tags = listOf("chords", "pinned")),
        )
        assertEquals(listOf("set", "chords", "neu", "alt"), pinned(rows, "pinned"))
    }

    @Test
    fun `a parent tag pins its children`() {
        val rows = listOf(Row("a"), Row("b", tags = listOf("pinned/gig")))
        assertEquals(listOf("b", "a"), pinned(rows, "pinned"))
    }

    @Test
    fun `a tag nobody carries changes nothing`() {
        val rows = listOf(Row("a"), Row("b"))
        assertEquals(listOf("a", "b"), pinned(rows, "pinned"))
        assertEquals(listOf("a", "b"), pinned(rows, ""))
    }
}
