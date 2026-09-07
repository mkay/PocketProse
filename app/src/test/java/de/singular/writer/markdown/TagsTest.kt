// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `CLAUDE.md` calls the hashtag rule load-bearing and says to test it explicitly. Each case below
 * corresponds to something that actually exists in the archive.
 */
class TagsTest {

    @Test
    fun `a sharp in a chord is not a tag`() {
        // Radio (Song Notes).md: "Tarantino für zwei in F# Moll." — the only sharp in 168 notes.
        assertEquals(emptyList<String>(), Tags.inBody("Tarantino für zwei in F# Moll."))
        // The same rule covers the chord sheet spellings, should any ever be typed as text.
        assertEquals(emptyList<String>(), Tags.inBody("F#m C# G#7"))
    }

    @Test
    fun `a heading is not a tag`() {
        assertEquals(emptyList<String>(), Tags.inBody("## Strophe"))
        assertEquals(emptyList<String>(), Tags.inBody("# Title"))
    }

    @Test
    fun `Bear's wrapped percent tags stay as text`() {
        // 11 notes carry #100%# in the body; the tag itself lives in the frontmatter only.
        assertEquals(emptyList<String>(), Tags.inBody("#100%#"))
        assertEquals(emptyList<String>(), Tags.inBody("#50%#"))
        assertFalse(Tags.isInlineWritable("100%"))
        assertFalse(Tags.isInlineWritable("50%"))
        assertTrue(Tags.isInlineWritable("lyrics/snippet"))
        assertTrue(Tags.isInlineWritable("meta-info"))
    }

    @Test
    fun `a hashtag must start a word`() {
        assertEquals(emptyList<String>(), Tags.inBody("nothash#tag"))
        assertEquals(listOf("tag"), Tags.inBody("start #tag"))
        assertEquals(listOf("tag"), Tags.inBody("#tag"))
        assertEquals(listOf("tag"), Tags.inBody("line one\n#tag"))
    }

    @Test
    fun `nesting and the punctuation the archive uses are accepted`() {
        assertEquals(
            listOf("lyrics/snippet", "radio/meta-titles", "album/bossanova"),
            Tags.inBody("#lyrics/snippet #radio/meta-titles #album/bossanova"),
        )
    }

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
