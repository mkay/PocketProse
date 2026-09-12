// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

import de.singular.writer.SortBy
import de.singular.writer.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The library's sort orders.
 *
 * Pure comparisons, so they belong on the JVM rather than on a phone — and the null rule in
 * particular is the kind of thing that looks obviously right in a menu and is wrong in the one
 * direction nobody tries.
 *
 * Written against [Sortable] rather than against [IndexedNote], which cannot be built off a device:
 * its [NoteFile] needs a real `Uri`, and `Uri.EMPTY` is null in the stub android.jar. That is why
 * nothing in this package had a test of the index before. The interface is the sort's actual
 * contract — a name, a date, a length — and stating it cost less than a mocking framework standing
 * in for a field the sort never reads.
 */
class SortTest {

    private data class Row(
        override val title: String,
        override val updated: Instant?,
        override val words: Int = 1,
        override val created: Instant? = null,
    ) : Sortable

    private fun at(iso: String) = Instant.parse(iso)

    private fun order(rows: List<Row>, by: SortBy, dir: SortOrder) =
        NoteIndex(emptyList()).sorted(rows, by, dir).map { it.title }

    @Test
    fun `by date, newest first`() {
        val rows = listOf(
            Row("alt", at("2015-01-01T00:00:00Z")),
            Row("neu", at("2025-01-01T00:00:00Z")),
        )
        assertEquals(listOf("neu", "alt"), order(rows, SortBy.UPDATED, SortOrder.DESC))
        assertEquals(listOf("alt", "neu"), order(rows, SortBy.UPDATED, SortOrder.ASC))
    }

    @Test
    fun `by creation, the archive's timeline, with the same null rule`() {
        // A note begun in 2015 and edited last week is a 2015 note in this order — the sort reads
        // `created` and nothing about `updated` moves it.
        val rows = listOf(
            Row("ohne", at("2025-01-01T00:00:00Z"), created = null),
            Row("neu", at("2020-01-01T00:00:00Z"), created = at("2025-01-01T00:00:00Z")),
            Row("alt", at("2026-09-01T00:00:00Z"), created = at("2015-01-01T00:00:00Z")),
        )
        assertEquals(listOf("neu", "alt", "ohne"), order(rows, SortBy.CREATED, SortOrder.DESC))
        assertEquals(listOf("alt", "neu", "ohne"), order(rows, SortBy.CREATED, SortOrder.ASC))
    }

    @Test
    fun `a note without a date sorts last in both directions`() {
        // The whole reason this file exists. 40 notes in the archive carry no date, and an ascending
        // sort that floated them to the top would bury the oldest note — the thing that was asked
        // for — behind every note the app knows nothing about.
        val rows = listOf(
            Row("ohne", null),
            Row("alt", at("2015-01-01T00:00:00Z")),
            Row("neu", at("2025-01-01T00:00:00Z")),
        )
        assertEquals(listOf("neu", "alt", "ohne"), order(rows, SortBy.UPDATED, SortOrder.DESC))
        assertEquals(listOf("alt", "neu", "ohne"), order(rows, SortBy.UPDATED, SortOrder.ASC))
    }

    @Test
    fun `titles sort case-insensitively, as everywhere else in the app`() {
        val rows = listOf(Row("beta", null), Row("Alpha", null), Row("gamma", null))
        assertEquals(listOf("Alpha", "beta", "gamma"), order(rows, SortBy.TITLE, SortOrder.ASC))
        assertEquals(listOf("gamma", "beta", "Alpha"), order(rows, SortBy.TITLE, SortOrder.DESC))
    }

    @Test
    fun `words sort by length, and ties break on title the same way round either way`() {
        val rows = listOf(
            Row("zwei", null, words = 2),
            Row("lang", null, words = 9),
            Row("auch", null, words = 2),
        )
        // "auch" before "zwei" in both, though the block of two-word notes changes ends.
        assertEquals(listOf("auch", "zwei", "lang"), order(rows, SortBy.WORDS, SortOrder.ASC))
        assertEquals(listOf("lang", "auch", "zwei"), order(rows, SortBy.WORDS, SortOrder.DESC))
    }

    @Test
    fun `the default order matches the one the index has always had`() {
        // UPDATED descending is what NoteIndex.notes produces on its own. If the two ever disagree,
        // the list would jump the first time somebody opened the menu and changed nothing — so the
        // rule is written twice on purpose and pinned here.
        val rows = listOf(
            Row("ohne", null),
            Row("alt", at("2015-01-01T00:00:00Z")),
            Row("neu", at("2025-01-01T00:00:00Z")),
        )
        val canonical = rows.sortedWith(
            compareByDescending<Row> { it.updated != null }
                .thenByDescending { it.updated }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        ).map { it.title }
        assertEquals(canonical, order(rows, SortBy.UPDATED, SortOrder.DESC))
    }
}
