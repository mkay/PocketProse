// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What is left of tags once the frontmatter is the only place they live: folding names, and building
 * the tree the drawer shows.
 *
 * The hashtag grammar that used to be tested here — `F#` in a chord, `## Strophe` as a heading, a
 * tag opening with a digit, a `#` that must start a word — went with the inline representation on
 * 2026-09-09. None of it needs a guard now: nothing reads a `#` out of a body at all, so the failure
 * those cases protected against (a tag in the drawer that is really a chord name) cannot occur. See
 * `Tags` and the Tag rules in `CLAUDE.md`. `BlocksTest` asserts the other half — that such a line is
 * now ordinary prose rather than something hidden.
 */
class TagsTest {

    @Test
    fun `input is folded to lowercase so the archive cannot re-split`() {
        assertEquals("lyrics", Tags.normalize("Lyrics"))
        assertEquals("lyrics/snippet", Tags.normalize("#Lyrics/Snippet"))
        assertEquals("released", Tags.normalize("  released  "))
    }

    @Test
    fun `the tree is built from path segments, not from parents that exist`() {
        // No note is tagged with a bare `album`, but its three children must still nest under it.
        val tree = Tags.tree(
            listOf(
                listOf("album/debut"),
                listOf("album/bossanova"),
                listOf("album/bossanova"),
                listOf("released"),
            ),
        )
        val album = tree.first { it.path == "album" }
        assertEquals(0, album.count)
        assertEquals(3, album.total)
        assertEquals(listOf("bossanova", "debut"), album.children.map { it.segment })
        assertEquals(2, album.children.first { it.segment == "bossanova" }.count)
    }

    @Test
    fun `a parent counts notes, not tags, so two children on one note count once`() {
        // Summing the children gave `lyrics` 185 against an archive of 168 notes.
        val tree = Tags.tree(
            listOf(
                listOf("lyrics/snippet", "lyrics/titel"),
                listOf("lyrics/snippet"),
            ),
        )
        val lyrics = tree.first { it.path == "lyrics" }
        assertEquals(2, lyrics.total)
        assertEquals(0, lyrics.count)
        assertEquals(2, lyrics.children.first { it.segment == "snippet" }.total)
    }

    @Test
    fun `the tree is alphabetical, so a tag stays where it was last seen`() {
        // Frequency order would put lyrics first here. It is deliberately not used: a tag matching
        // most of the archive is the least useful filter in the app, and the whole tree fits on one
        // screen anyway. See Tags.tree.
        val tree = Tags.tree(
            List(131) { listOf("lyrics/snippet") } +
                List(32) { listOf("lyrics/titel") } +
                List(1) { listOf("english") },
        )
        assertEquals(listOf("english", "lyrics"), tree.map { it.segment })
        assertEquals(163, tree.first { it.path == "lyrics" }.total)
        assertEquals(0, tree.first { it.path == "lyrics" }.count)
        assertEquals(listOf("snippet", "titel"), tree.first { it.path == "lyrics" }.children.map { it.segment })
    }
}
