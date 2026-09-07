// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * The parser against the real archive: 168 notes, every one of them read, and the bytes compared.
 *
 * This is `CLAUDE.md`'s eighth acceptance test — "after opening every note and editing none, md5sum
 * over the folder is unchanged" — brought forward to where it is cheap to run. On a phone that check
 * costs an install and a walk through the app; here it is a second, and it runs on every build.
 *
 * **The archive is not in this repository.** It is 168 unreleased song lyrics, which is personal
 * writing rather than sample data and no part of a GPL source release. Populate it locally with
 *
 *     adb pull /sdcard/Recordings/Lyrics app/src/test/corpus/
 *
 * and these tests wake up. Without it they skip, so a fresh clone still builds green — the tests in
 * the sibling files cover the same rules against fixtures written for the purpose, and it is those
 * that a contributor is expected to run.
 *
 * The numbers asserted below were measured on 2026-09-07. If one of them fails after the archive
 * has been edited, the archive is right and the number here is stale — check which before assuming
 * a parser bug.
 */
class CorpusTest {

    companion object {
        private val folder = File("src/test/corpus/Lyrics")
        private lateinit var notes: Map<String, String>

        @BeforeClass
        @JvmStatic
        fun load() {
            if (!folder.isDirectory) return
            notes = folder.listFiles { f: File -> f.name.endsWith(".md") }
                .orEmpty()
                .associate { it.name to it.readText() }
        }
    }

    private fun corpus(): Map<String, String> {
        assumeTrue("archive not present — see the class comment", folder.isDirectory)
        return notes
    }

    @Test
    fun `every note in the archive renders back byte for byte`() {
        val corpus = corpus()
        val broken = corpus.filterNot { (_, text) -> Note.parse(text).render() == text }.keys
        assertEquals("notes that did not survive a round trip", emptySet<String>(), broken)
        assertEquals(168, corpus.size)
    }

    @Test
    fun `every note has a title, and it is not the filename`() {
        val corpus = corpus()
        assertTrue(corpus.values.all { Note.parse(it).title != null })
        // Wer geht vor.md is titled "Wer geht vor?" — the filename cannot carry the question mark.
        val differing = corpus.count { (name, text) ->
            Note.parse(text).title != name.removeSuffix(".md")
        }
        assertTrue("titles that differ from their filename: $differing", differing >= 11)
    }

    @Test
    fun `duplicate titles stay separate notes`() {
        val corpus = corpus()
        val byTitle = corpus.values.groupBy { Note.parse(it).title }
        assertEquals(4, byTitle["Wer geht vor?"]?.size)
        assertEquals(3, byTitle["Wer nicht will"]?.size)
        assertEquals(2, byTitle["Lieblos"]?.size)
        assertEquals(2, byTitle["Sieger sehen anders aus"]?.size)
    }

    @Test
    fun `the tag distribution is what it was measured to be`() {
        val corpus = corpus()
        val all = corpus.values.map { Note.parse(it).tags }
        val counts = all.flatten().groupingBy { it }.eachCount()

        assertEquals(24, counts.size)
        assertEquals(131, counts["lyrics/snippet"])
        assertEquals(32, counts["lyrics/titel"])
        assertEquals(25, counts["busch"])
        assertEquals(25, counts["released"])
        assertEquals(19, counts["radio"])
        assertEquals(11, counts["100%"])
        assertEquals(1, counts["50%"])
        assertEquals(3, all.count { it.isEmpty() })
        assertEquals(5, all.maxOf { it.size })

        val tree = Tags.tree(all)
        // 14 top-level nodes for 13 top-level tags: `album` is synthesised. No note carries a
        // bare `album`, but its three children need a parent to hang from — which is exactly the
        // behaviour Tags.tree exists to have, so the extra node is the point rather than an
        // off-by-one.
        assertEquals(14, tree.size)
        assertEquals(setOf("album", "lyrics", "radio"), tree.filter { it.children.isNotEmpty() }.map { it.path }.toSet())
        assertEquals(11, tree.sumOf { node -> node.children.size })
    }

    @Test
    fun `frontmatter and inline hashtags agree across the whole archive`() {
        // Measured: they agree in all 168 notes today, once the two tags that cannot be written
        // inline are set aside. The app's job is to keep it that way — see Tags.
        val corpus = corpus()
        val disagreeing = corpus.filter { (_, text) ->
            val note = Note.parse(text)
            val writable = note.frontmatter.tags.filter(Tags::isInlineWritable).toSet()
            Tags.inBody(note.body).toSet() != writable
        }.keys
        assertEquals(emptySet<String>(), disagreeing)
    }

    @Test
    fun `exactly one note contains a sharp, and it produces no tag`() {
        val corpus = corpus()
        val withSharp = corpus.filterValues { it.contains("F#") }
        assertEquals(setOf("Radio (Song Notes).md"), withSharp.keys)
        val note = Note.parse(withSharp.values.first())
        assertTrue("F#" !in note.tags.joinToString())
        assertTrue(note.tags.none { it.isEmpty() })
    }

    @Test
    fun `no note contains an ATX heading, and five use a second-level one`() {
        val corpus = corpus()
        val blocks = corpus.values.map { Note.parse(it).blocks }
        assertEquals(0, blocks.count { bs -> bs.any { it is Block.Heading && it.level == 1 } })
        assertEquals(5, blocks.count { bs -> bs.any { it is Block.Heading && it.level == 2 } })
    }

    @Test
    fun `the notes that are nothing but a tag line excerpt to nothing`() {
        // 40 of 168 — very nearly a quarter of the archive. This is why the library row carries a
        // placeholder rather than leaving an empty gap, and it is the single fact that most shapes
        // the main screen.
        //
        // An earlier count said 36. That was reached by grouping byte-identical bodies, which finds
        // only the notes whose tag line is shared with another note and misses the four whose tag
        // combination is unique. Counting the excerpts themselves is the measure that matches what
        // the screen will actually show.
        val corpus = corpus()
        val empty = corpus.filterValues { Excerpt.of(Note.parse(it)).isEmpty() }
        assertEquals(40, empty.size)
        assertTrue("Die Eule.md" in empty.keys)
        // Every one of them is a title filed under a tag, with no prose at all — not a note whose
        // text happened to be images or rules.
        assertTrue(empty.values.all { Note.parse(it).blocks.all { b -> b is Block.TagLine } })
    }

    @Test
    fun `rules in bodies are read as rules and never as headings`() {
        val corpus = corpus()
        val withRule = corpus.filterValues { text ->
            Note.parse(text).blocks.any { it is Block.Rule }
        }
        // 20 notes use `- - -` and 2 use a bare `---`; one uses both, so 21 notes carry a rule.
        assertEquals(21, withRule.size)

        // Sieger sehen anders aus.md writes two rules on consecutive lines. Nothing in the archive
        // actually puts a bare `---` directly under a line of prose, so the setext ambiguity is
        // latent here rather than live — the synthetic case in BlocksTest is what pins the
        // behaviour down, and this only checks that the real note stays two rules and no heading.
        val consecutive = Note.parse(corpus.getValue("Sieger sehen anders aus.md")).blocks
        // Six rule lines in this one note: two consecutive bare ones, one with a trailing space,
        // two spaced `- - -`, and a closing one.
        assertEquals(6, consecutive.count { it is Block.Rule })
        assertTrue(consecutive.none { it is Block.Heading })
    }
}
