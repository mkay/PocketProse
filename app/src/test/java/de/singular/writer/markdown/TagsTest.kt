// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.Instant
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
    fun `renaming a tag takes its children with it`() {
        // `album` -> `record` has to move `album/debut` too. The tree is built from path segments,
        // so a parent renamed alone would leave its children hanging under a name nothing carries —
        // one branch of the drawer becoming two.
        assertEquals(
            listOf("record", "record/debut", "released"),
            Tags.rename(listOf("album", "album/debut", "released"), "album", "record"),
        )
    }

    @Test
    fun `renaming matches on a path boundary, not on a prefix`() {
        // `albumcover` is not under `album`, and a naive startsWith would have renamed it.
        assertEquals(listOf("albumcover"), Tags.rename(listOf("albumcover"), "album", "record"))
        assertEquals(listOf("record"), Tags.rename(listOf("album"), "album", "record"))
    }

    @Test
    fun `renaming onto an existing tag merges, leaving one`() {
        // The outcome renaming back does not undo, which is why the dialog says so before it runs.
        assertEquals(
            listOf("released"),
            Tags.rename(listOf("album", "released"), "album", "released"),
        )
    }

    @Test
    fun `a renamed tag keeps its place in the note's list`() {
        // The `tags:` block keeps the shape the author gave it, so the rename is a one-line diff
        // rather than a reordering of every entry.
        assertEquals(
            listOf("busch", "record", "radio"),
            Tags.rename(listOf("busch", "album", "radio"), "album", "record"),
        )
    }

    @Test
    fun `renaming a child leaves its parent and its siblings alone`() {
        assertEquals(
            listOf("album", "album/first", "album/bossanova"),
            Tags.rename(listOf("album", "album/debut", "album/bossanova"), "album/debut", "album/first"),
        )
    }

    @Test
    fun `renaming is idempotent, which is what makes running it twice safe`() {
        // The recovery offered after a partial rename. The second pass finds nothing under the old
        // name and changes nothing — see RenameResult.Partial.
        val once = Tags.rename(listOf("album", "album/debut"), "album", "record")
        assertEquals(once, Tags.rename(once, "album", "record"))
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

    // --- folding a field while it is being typed in -----------------------------------------------

    @Test
    fun `a field is folded as it is typed, so the rule is visible when it applies`() {
        // It ran only on commit until 2026-09-10: you typed a capital, saw your capital, saved, and
        // found it lowered afterwards. Most of those capitals were the keyboard's doing rather than
        // the user's, which made it read as the app having its own ideas about your filing.
        assertEquals("lyrics", Tags.typed("Lyrics"))
        assertEquals("lyrics/snippet", Tags.typed("Lyrics/Snippet"))
    }

    @Test
    fun `a hash typed out of habit never appears`() {
        assertEquals("busch", Tags.typed("#busch"))
        assertEquals("busch", Tags.typed("  #Busch"))
    }

    @Test
    fun `a space can still be typed into the middle of a tag`() {
        // The whole reason this is not just `normalize`. Trimming the end on every keystroke eats
        // the space before the second word can be typed, and a tag with a space in it becomes
        // unwriteable — a restriction nobody asked for.
        assertEquals("song ", Tags.typed("Song "))
        assertEquals("song ideas", Tags.typed("Song Ideas"))
        // And the trailing space still goes when it is committed.
        assertEquals("song ideas", Tags.normalize(Tags.typed("Song Ideas ")))
    }

    @Test
    fun `typing then committing agrees with committing alone`() {
        // The two must not drift: whatever the field shows is what gets stored.
        for (raw in listOf("Lyrics", "#Busch", "  Album/Debut  ", "100", "RADIO/Website")) {
            assertEquals(Tags.normalize(raw), Tags.normalize(Tags.typed(raw)))
        }
    }

    // --- what the app accepts as a new name ------------------------------------------------------

    @Test
    fun `a tag with a space in it is refused as new input`() {
        // Not a style rule. `CLAUDE.md`'s planned export writes the frontmatter tags back into
        // bodies as hashtags and is the whole of the app's undo story — and `#man go` is not a tag
        // called "man go", it is the tag `man` followed by the word `go`. A tag the app can store
        // and never give back is a worse promise than one it refuses at the door.
        assertFalse(Tags.accepts("man go"))
        assertFalse(Tags.accepts("man#go"))
        assertFalse(Tags.accepts(""))
    }

    @Test
    fun `every shape the archive actually uses is accepted`() {
        for (tag in listOf("lyrics", "lyrics/snippet", "album/debut", "meta-info", "100", "50")) {
            assertTrue(tag, Tags.accepts(tag))
        }
    }

    @Test
    fun `a tag that is already in a file is not refused, only re-entry is`() {
        // The rule governs the door, not the archive. A note carrying `man go` keeps it: listed,
        // filtered by, counted, written back as found. Rewriting somebody's frontmatter to enforce
        // a rule the app invented afterwards is the prime directive's first prohibition.
        val note = Note.parse("---\ntags:\n  - \"man go\"\n---\n\nDu\n")
        assertEquals(listOf("man go"), note.tags)
        assertEquals(listOf("man go"), Tags.tree(listOf(note.tags)).map { it.path })
        // And an unrelated edit writes it back untouched.
        val saved = note.withBody("Du erreichst mich nicht\n", Instant.parse("2026-09-10T00:00:00Z"))!!
        assertTrue(saved.render().contains("  - \"man go\""))
    }
}
