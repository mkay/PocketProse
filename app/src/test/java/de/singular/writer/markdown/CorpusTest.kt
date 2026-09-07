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

        // 25 since the percent rename: `75` had no frontmatter entry anywhere before it, so it was
        // not a tag the app could see at all. See tools/rename-percent-tags.py.
        assertEquals(25, counts.size)
        assertEquals(131, counts["lyrics/snippet"])
        assertEquals(32, counts["lyrics/titel"])
        assertEquals(25, counts["busch"])
        assertEquals(25, counts["released"])
        assertEquals(19, counts["radio"])
        // 30 and 2, not the 11 and 1 the frontmatter used to declare — the rename gave the 21 notes
        // the export had skipped their entry back.
        assertEquals(30, counts["100"])
        assertEquals(2, counts["50"])
        assertEquals(1, counts["75"])
        assertEquals(3, all.count { it.isEmpty() })
        assertEquals(6, all.maxOf { it.size })

        val tree = Tags.tree(all)
        // 15 top-level nodes for 14 top-level tags: `album` is synthesised. No note carries a
        // bare `album`, but its three children need a parent to hang from — which is exactly the
        // behaviour Tags.tree exists to have, so the extra node is the point rather than an
        // off-by-one.
        assertEquals(15, tree.size)
        assertEquals(setOf("album", "lyrics", "radio"), tree.filter { it.children.isNotEmpty() }.map { it.path }.toSet())
        assertEquals(11, tree.sumOf { node -> node.children.size })
        // No parent may claim more notes than exist. `lyrics` summed to 185 before totals were
        // counted rather than added — see Tags.Node.total.
        assertTrue(tree.all { it.total <= all.size })
        // Exact totals, because "not more than 168" would still pass a subtler double count.
        // Summing the children instead gives 185, 26 and 26 — the first of which is more notes than
        // the archive has.
        assertEquals(154, tree.first { it.path == "lyrics" }.total)
        assertEquals(25, tree.first { it.path == "album" }.total)
        assertEquals(20, tree.first { it.path == "radio" }.total)
        assertEquals(131, tree.first { it.path == "lyrics" }.children.first { it.segment == "snippet" }.total)
    }

    @Test
    fun `frontmatter and inline hashtags agree, for the tags that can be written inline`() {
        // They agree in all 168 notes. Note the qualifier: this says nothing about percent tags,
        // which the parser cannot see as hashtags at all — that blind spot is what made an earlier
        // version of this test read as a clean bill of health for the whole archive. The percent
        // situation is measured separately below, and it is not clean.
        val corpus = corpus()
        val disagreeing = corpus.filter { (_, text) ->
            val note = Note.parse(text)
            val writable = note.frontmatter.tags.filter(Tags::isInlineWritable).toSet()
            Tags.inBody(note.body).toSet() != writable
        }.keys
        assertEquals(emptySet<String>(), disagreeing)
    }

    @Test
    fun `no tag anywhere still carries a percent sign`() {
        // `100%`, `50%` and `75%` were renamed to `100`, `50` and `75` on 2026-09-07 — see
        // tools/rename-percent-tags.py. `%` could not be written as a hashtag, which is what made
        // those three frontmatter-only and what made the export drop 21 of them.
        val corpus = corpus()
        val left = corpus.filterValues { text ->
            val note = Note.parse(text)
            note.frontmatter.tags.any { "%" in it } || Regex("""(?<=^|\s)#\d+%""").containsMatchIn(note.body)
        }.keys
        assertEquals(emptySet<String>(), left)
    }

    @Test
    fun `the renamed tags are ordinary tags now, in both places, on every note that has one`() {
        val corpus = corpus()
        val counts = corpus.values.map { Note.parse(it).tags }.flatten().groupingBy { it }.eachCount()
        assertEquals(30, counts["100"])
        assertEquals(2, counts["50"])
        assertEquals(1, counts["75"])

        // The point of the rename: no note is left declaring one representation and not the other.
        val numeric = { tags: List<String> -> tags.filter { tag -> tag.all(Char::isDigit) }.toSet() }
        val disagreeing = corpus.filter { (_, text) ->
            val note = Note.parse(text)
            numeric(note.frontmatter.tags) != numeric(Tags.inBody(note.body))
        }.keys
        assertEquals(emptySet<String>(), disagreeing)
    }

    @Test
    fun `a digit may open a tag, and nothing else in the archive is caught by that`() {
        // Widening the rule from "then a letter" to "then a letter or digit" is safe because the 33
        // renamed hashtags are the only `#digit` sequences in all 168 notes. If a note ever grew a
        // `#2` in prose this would say so.
        val corpus = corpus()
        val digitTags = corpus.values
            .flatMap { Tags.inBody(Note.parse(it).body) }
            .filter { it.first().isDigit() }
        assertEquals(setOf("100", "50", "75"), digitTags.toSet())
        assertEquals(33, digitTags.size)
    }

    @Test
    fun `no numeric tag is embedded in prose, so hiding its line hides no words`() {
        // All 33 sit on a line with other tags, or alone. If one were mid-sentence, the editor would
        // have to choose between showing a `#` and hiding a word.
        val corpus = corpus()
        val embedded = corpus.filterValues { text ->
            Note.parse(text).blocks.any { block ->
                block is Block.Paragraph && Tags.inBody(block.text).any { it.first().isDigit() }
            }
        }.keys
        assertEquals(emptySet<String>(), embedded)
    }

    @Test
    fun `the export's embed comments are three notes' worth and never reach a reader`() {
        val corpus = corpus()
        val withComments = corpus.filterValues { "<!--" in it }
        assertEquals(3, withComments.size)
        assertEquals(24, corpus.values.sumOf { Regex("<!--").findAll(it).count() })
        assertTrue(withComments.values.none { "<!--" in Excerpt.of(Note.parse(it)) })
    }

    @Test
    fun `the duplicate Wer geht vor notes link into a folder that does not exist`() {
        // Wer geht vor.md has a proper `## Anhänge` list into attachments/. Its three byte-identical
        // duplicates still point at `Wer geht vor/`, the un-migrated layout it came with, and that folder is
        // not in the archive — 24 dead links. Phase 5 must show such a link without pretending to
        // have the file behind it, and must never "repair" one.
        val corpus = corpus()
        val dead = corpus.filterValues { "Wer%20geht%20vor/" in it }
        assertEquals(setOf("Wer geht vor 2.md", "Wer geht vor 3.md", "Wer geht vor 4.md"), dead.keys)
        assertEquals(24, dead.values.sumOf { Regex("Wer%20geht%20vor/").findAll(it).count() })
        assertTrue("attachments/wer-geht-vor-pasted-graphic-10.pdf" in corpus.getValue("Wer geht vor.md"))
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
    fun `live styling never hides a character the author typed`() {
        // The editor hides markup, and only markup. Run every note past the scanner and check that
        // what disappears is exclusively marker characters — asterisks, underscores, backticks, and
        // a heading's hashes and the space after them. Anything else vanishing from the screen would
        // be a word the author wrote going missing while they looked at it.
        val corpus = corpus()
        val allowed = setOf('*', '_', '`', '#', ' ', '\t')
        for ((name, text) in corpus) {
            val body = Note.parse(text).body
            val live = Live.of(body, IntRange(-5, -5))
            for (h in live.hide) {
                val removed = body.substring(h.start, h.end)
                assertTrue(
                    "$name: live styling would hide \"$removed\"",
                    removed.all { it in allowed },
                )
            }
        }
    }

    @Test
    fun `live styling survives the cursor being anywhere in any note`() {
        // The invariant that matters is structural: hidden ranges ordered, non-overlapping, inside
        // the text, and every style landing inside what survives. Checked at every marker character
        // in the archive, which is where the edges actually are.
        val corpus = corpus()
        for ((name, text) in corpus) {
            val body = Note.parse(text).body
            val probes = body.indices.filter { body[it] in "*_`#" } + listOf(0, body.length)
            for (cursor in probes) {
                val live = Live.of(body, cursor..cursor)
                var last = 0
                for (h in live.hide) {
                    assertTrue("$name at $cursor: ranges out of order", h.start >= last)
                    assertTrue("$name at $cursor: range past end", h.end <= body.length)
                    assertTrue("$name at $cursor: empty range", h.end > h.start)
                    last = h.end
                }
                val visible = body.length - live.hide.sumOf { it.end - it.start }
                for (st in live.styles) {
                    assertTrue("$name at $cursor: style before 0", st.start >= 0)
                    assertTrue("$name at $cursor: style past visible end", st.end <= visible)
                }
            }
        }
    }

    @Test
    fun `splitting a note for the editor and rejoining it changes not one byte`() {
        // The editor cuts a body at its image lines so images can be drawn between text fields.
        // If that round trip lost or added a character, every note with an image would be rewritten
        // the moment it was opened. Checked on every note, not only the three with images.
        val corpus = corpus()
        for ((name, text) in corpus) {
            val body = Note.parse(text).body
            assertEquals(name, body, Segments.join(Segments.split(body)))
        }
    }

    @Test
    fun `no hashtag line is left in the editable text of any note`() {
        // The editor's half of "the user never sees a #". Every tag line in the archive has to leave
        // the text buffer, or the promise holds for the list and the drawer and breaks on the one
        // screen where the writing happens.
        val corpus = corpus()
        val leaked = corpus.filter { (_, text) ->
            Segments.split(Note.parse(text).body)
                .filterIsInstance<Segment.Prose>()
                .any { prose -> prose.raw.lines().any(Tags::isTagLine) }
        }.keys
        assertEquals(emptySet<String>(), leaked)
    }

    @Test
    fun `the notes with no tag line are the three with no tags`() {
        val corpus = corpus()
        val untagged = corpus.filterValues { text ->
            Segments.split(Note.parse(text).body).none { it is Segment.Tags }
        }
        assertEquals(3, untagged.size)
        assertTrue(untagged.values.all { Note.parse(it).tags.isEmpty() })
    }

    @Test
    fun `every tag the frontmatter declares reaches a chip`() {
        // The chip row is built from the runs, so a tag that ended up in no run would be a tag the
        // author cannot see or remove. Percent tags go the other way and are allowed to appear in a
        // run without a frontmatter entry — 21 of them do, the export having lost them.
        val corpus = corpus()
        for ((name, text) in corpus) {
            val note = Note.parse(text)
            val chipped = Segments.split(note.body).filterIsInstance<Segment.Tags>().flatMap { it.tags }
            val declared = note.frontmatter.tags.filter(Tags::isInlineWritable)
            assertEquals(name, emptyList<String>(), declared - chipped.toSet())
        }
    }

    @Test
    fun `adding a tag to every note and taking it away again changes not one byte`() {
        // The strictest thing a tag edit can be asked to promise, over the whole archive rather than
        // over fixtures chosen to be kind. If this fails, some note is being reflowed by a round trip
        // the user would think of as doing nothing at all.
        val corpus = corpus()
        val changed = corpus.filter { (_, text) ->
            val body = Note.parse(text).body
            val added = TagEdit.apply(body, listOf("probe"), emptyList())
            TagEdit.apply(added, emptyList(), listOf("probe")) != body
        }.keys
        assertEquals(emptySet<String>(), changed)
    }

    @Test
    fun `an added tag lands where the note already keeps its tags`() {
        val corpus = corpus()
        for ((name, text) in corpus) {
            val note = Note.parse(text)
            val added = TagEdit.apply(note.body, listOf("probe"), emptyList())
            // Written once, onto a line that is a tag line — never into the middle of a lyric.
            assertEquals(name, 1, Regex("""(?<=^|\s)#probe(?=\s|$)""", RegexOption.MULTILINE).findAll(added).count())
            val on = Segments.physicalLines(added).single { "#probe" in it }.removeSuffix("\n")
            assertTrue(name, Tags.isTagLine(on))
            // And every other line of the note is untouched.
            assertEquals(name, note.body, TagEdit.apply(added, emptyList(), listOf("probe")))
        }
    }

    @Test
    fun `removing every tag from a note leaves the words alone`() {
        // The most destructive edit the chip sheet can ask for. What must survive is the writing:
        // hashtag lines go, and nothing else does.
        val corpus = corpus()
        for ((name, text) in corpus) {
            val note = Note.parse(text)
            val stripped = TagEdit.apply(note.body, emptyList(), note.tags)
            val words = { body: String ->
                Segments.physicalLines(body).filterNot { Tags.isTagLine(it.removeSuffix("\n")) }
            }
            assertEquals(name, words(note.body).filterNot { it.isBlank() }, words(stripped).filterNot { it.isBlank() })
        }
    }

    @Test
    fun `a tag edit writes the frontmatter and the body together, and stamps updated once`() {
        val corpus = corpus()
        val now = java.time.Instant.parse("2026-09-07T12:00:00Z")
        for ((name, text) in corpus) {
            val note = Note.parse(text)
            val edited = note.withTags(note.body, note.tags + "probe", now)!!
            assertTrue(name, "probe" in edited.frontmatter.tags)
            assertTrue(name, "#probe" in edited.body)
            assertEquals(name, Note.stamp(now), edited.frontmatter.updated)
            // created is the archive's value and is never the app's to move.
            assertEquals(name, note.frontmatter.created, edited.frontmatter.created)
            // And the tags the user did not touch keep the order and the quoting the file had.
            assertEquals(name, note.tags, edited.frontmatter.tags.dropLast(1).map(Tags::normalize))
        }
    }

    @Test
    fun `an unchanged tag set writes nothing at all`() {
        val corpus = corpus()
        val now = java.time.Instant.parse("2026-09-07T12:00:00Z")
        for ((name, text) in corpus) {
            val note = Note.parse(text)
            assertEquals(name, null, note.withTags(note.body, note.tags, now))
        }
    }

    @Test
    fun `images sit on their own lines, which is what makes the editor possible`() {
        // 38 images across 3 chord sheets, and the most any line carries beside them is a bare 3x.
        // If an image ever appeared mid-sentence the segment approach would cut a paragraph in two.
        val corpus = corpus()
        val withImages = corpus.filterValues { Segments.imagesIn(Note.parse(it).body).isNotEmpty() }
        assertEquals(3, withImages.size)
        assertEquals(38, corpus.values.sumOf { Segments.imagesIn(Note.parse(it).body).size })

        val trailing = corpus.values
            .flatMap { Segments.split(Note.parse(it).body) }
            .filterIsInstance<Segment.Images>()
            .map { it.trailing }
            .filter { it.isNotEmpty() }
        assertEquals(listOf("3x"), trailing)
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
