// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

        /**
         * The same 168 notes as the export left them: no frontmatter at all, a `# Heading` opening
         * every one, the tags written into the words, and no `created` or `updated` anywhere.
         *
         * The archive proves the migration harms a folder that does not need it. This folder is the
         * one that does, and it is the only place the title half can be tested against real writing
         * rather than against fixtures written to pass. Populate it the same way:
         *
         *     adb pull /sdcard/Recordings/Lyrics_Inline app/src/test/corpus/
         */
        private val inlineFolder = File("src/test/corpus/Lyrics_Inline")
        private lateinit var inlineNotes: Map<String, String>

        @BeforeClass
        @JvmStatic
        fun load() {
            if (inlineFolder.isDirectory) {
                inlineNotes = inlineFolder.listFiles { f: File -> f.name.endsWith(".md") }
                    .orEmpty()
                    .associate { it.name to it.readText() }
            }
            if (!folder.isDirectory) return
            notes = folder.listFiles { f: File -> f.name.endsWith(".md") }
                .orEmpty()
                .associate { it.name to it.readText() }
        }
    }

    /**
     * The hashtags still written in the archive's bodies.
     *
     * The rule the app used to index by, kept here and only here. The app no longer reads a `#` out
     * of a body at all — see the Tag rules in `CLAUDE.md` — but the leftover lines are a fact about
     * the files, and a couple of tests below are about that fact rather than about the app.
     */
    private fun hashtagsIn(body: String): Set<String> =
        Regex("""(?<=^|\s)#([\p{L}\d][\w/-]*)""", RegexOption.MULTILINE)
            .findAll(body)
            .map { it.groupValues[1] }
            .toSet()

    private fun corpus(): Map<String, String> {
        assumeTrue("archive not present — see the class comment", folder.isDirectory)
        return notes
    }

    private fun inlineCorpus(): Map<String, String> {
        assumeTrue("inline folder not present — see the class comment", inlineFolder.isDirectory)
        return inlineNotes
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
    fun `the paragraph count over the archive is what it was measured to be`() {
        val corpus = corpus()
        val stats = corpus.values.map { Stats.of(Note.parse(it).body) }

        // 818 blocks over 168 notes, measured on 2026-09-10. The figure is here so that a change to
        // the block rule has to be argued for against the archive rather than against a fixture.
        assertEquals(818, stats.sumOf { it.paragraphs })

        // 66 of those blocks are a leftover hashtag line standing alone at the head of a note, which
        // is why the dialog says "Paragraphs" and never "Verses" — for these notes the count is one
        // more than the song has verses. The app does not correct that; the files do, when the
        // author sweeps them.
        // A line of nothing but hashtags — several notes carry two on it, so "no space in the line"
        // is not the test; every word on it beginning with `#` is.
        val leadWithATag = corpus.values.count { text ->
            val first = Note.parse(text).body.trim().lineSequence().firstOrNull()?.trim().orEmpty()
            first.isNotEmpty() && first.split(' ').all { it.isNotEmpty() && it.startsWith("#") }
        }
        assertEquals(66, leadWithATag)

        // The 39 notes whose entire body is the tag line: one block, and every word on it a hashtag.
        // Their paragraph count is 1 and none of that 1 is the song, which is the sharpest case for
        // the name — "1 verse" would be false, "1 paragraph" is exactly what the file holds.
        val tagOnly = corpus.values.count { text ->
            val body = Note.parse(text).body
            val words = body.split(Regex("\\s+")).filter { it.isNotEmpty() }
            Stats.of(body).paragraphs == 1 && words.isNotEmpty() && words.all { it.startsWith("#") }
        }
        assertEquals(39, tagOnly)
    }

    @Test
    fun `every leftover body hashtag has a frontmatter entry, so clearing the bodies loses nothing`() {
        // The app stopped reading body hashtags on 2026-09-09 — see the Tag rules in `CLAUDE.md`.
        // The bodies still hold them, and the author intends to clear them out by script one day.
        // This is the property that makes that safe: every hashtag written in a body is also in that
        // note's `tags:` list, so deleting the lines deletes no tag the app would then be missing.
        // The reverse does not have to hold, and this says nothing about it.
        val corpus = corpus()
        val orphaned = corpus.filter { (_, text) ->
            val note = Note.parse(text)
            val declared = note.frontmatter.tags.map(Tags::normalize).toSet()
            (hashtagsIn(note.body) - declared).isNotEmpty()
        }.keys
        assertEquals(emptySet<String>(), orphaned)
    }

    @Test
    fun `the whole archive migrates, gains no tag, and loses no timestamp`() {
        // What the first-run offer would do to this folder, run over all 168 real notes. The archive
        // does not need it — its tags are already in the frontmatter — which is exactly what makes
        // it the right test bed: the move must come out as a pure strip, changing bodies and nothing
        // else, and any tag it claims to gain would be a tag it invented.
        val corpus = corpus()
        val notes = corpus.mapValues { (_, text) -> Note.parse(text) }
        val survey = Migration.survey(notes.values)

        assertEquals(168, survey.scanned)
        assertEquals(165, survey.notes)
        assertEquals(179, survey.lines)
        assertEquals(25, survey.tags.size)
        // Every hashtag in the archive is a tag the frontmatter already declares, so the drawer
        // gains nothing and no `tags:` block is rewritten. CorpusTest's sibling test asserts the
        // same property from the other side.
        assertEquals(emptyList<String>(), survey.gained)
        assertEquals(0, survey.blocked)

        for ((name, note) in notes) {
            val outcome = Migration.plan(note)
            if (outcome is Migration.Outcome.Untouched) continue
            val move = outcome as? Migration.Outcome.Move
                ?: throw AssertionError("$name could not be migrated: $outcome")
            assertEquals("$name had its frontmatter rewritten", note.frontmatter.raw, move.note.frontmatter.raw)
            assertEquals("$name lost its created", note.frontmatter.created, move.note.frontmatter.created)
            // Filing is not writing — this is `CLAUDE.md`'s ninth acceptance test, one operation over.
            assertEquals("$name had its updated moved", note.frontmatter.updated, move.note.frontmatter.updated)
            assertEquals("$name lost a tag", note.tags, move.note.tags)
            assertTrue("$name gained bytes", move.note.body.length < note.body.length)
            assertEquals("$name kept a hashtag", emptySet<String>(), hashtagsIn(move.note.body))
        }
    }

    @Test
    fun `the folder from the export migrates whole, in one pass`() {
        // The other side of the archive test above, on the folder the migration actually exists for.
        // Nothing here is in the frontmatter yet: no note has a title, so the library falls back to
        // the file name for all 168 and the editor's title field is empty for all 168, and no note
        // has a tag, so the drawer is empty while a hashtag sits in the words of 165 of them.
        val corpus = inlineCorpus()
        val notes = corpus.mapValues { (_, text) -> Note.parse(text) }
        assertEquals(168, notes.size)
        assertEquals("a note already carried a title", emptyList<String>(), notes.filterValues { it.title != null }.keys.toList())
        assertEquals("a note already carried a tag", emptyList<String>(), notes.filterValues { it.tags.isNotEmpty() }.keys.toList())

        val survey = Migration.survey(notes.values)
        assertEquals(168, survey.scanned)
        assertEquals(168, survey.notes)
        assertEquals(168, survey.titled)
        // Both halves at once, which is the case the offer's wording has to cover: 165 notes carry a
        // tag line, all 168 carry a heading, and the sentence has to say "tags and titles".
        assertEquals(165, survey.tagged)
        // Every tag in the drawer is one this move puts there, because there is no other source.
        assertEquals(25, survey.tags.size)
        assertEquals(25, survey.gained.size)
        assertEquals(0, survey.blocked)

        for ((name, note) in notes) {
            val move = Migration.plan(note) as? Migration.Outcome.Move
                ?: throw AssertionError("$name could not be migrated: ${Migration.plan(note)}")
            val title = move.note.title ?: throw AssertionError("$name gained no title")
            // The heading is gone from the body and its words are in the frontmatter — the move,
            // not a copy. A note that kept both would show its name twice, which is what sent the
            // author looking at this in the first place.
            assertEquals(
                "$name kept its heading",
                emptyList<Block>(),
                Blocks.parse(move.note.body).filterIsInstance<Block.Heading>().filter { it.level == 1 },
            )
            assertEquals("$name lost its title's words", title, (note.blocks.first() as Block.Heading).text)
            // What was in the words is now in the list, and nothing is left behind in either place.
            assertEquals("$name kept a hashtag", emptySet<String>(), hashtagsIn(move.note.body))
            assertEquals("$name lost a tag", hashtagsIn(note.body).map(Tags::normalize).toSet(), move.note.tags.toSet())
            // Nothing app-owned goes into a block the app created — no id, no timestamps. The export
            // carried no dates and the migration does not invent any.
            assertNull("$name was given a created", move.note.frontmatter.created)
            assertNull("$name was given an updated", move.note.frontmatter.updated)
        }

        // The 24 whose heading their file name cannot spell — the reason the title half is worth
        // making rather than leaving the library's file-name fallback to stand in. Measured
        // 2026-09-09.
        val differing = notes.count { (name, note) ->
            (Migration.plan(note) as Migration.Outcome.Move).note.title != name.removeSuffix(".md")
        }
        assertEquals(24, differing)
    }

    @Test
    fun `migrating the export twice changes nothing the second time`() {
        // The offer can be made again after a sync brings notes in, and the settings row can be
        // tapped by somebody who has already run it. A second pass over a migrated folder must be a
        // no-op rather than a second edit.
        val corpus = inlineCorpus()
        val once = corpus.values.map { Migration.plan(Note.parse(it)) }
            .filterIsInstance<Migration.Outcome.Move>()
            .map { it.note }
        assertEquals(168, once.size)
        assertFalse(Migration.survey(once).worthOffering)
    }

    @Test
    fun `the folder from the export renders back byte for byte before anything is moved`() {
        // The precheck the migration refuses on. A folder the app has just met is where an
        // unfamiliar note shape is likeliest, and this is the folder it has just met.
        val corpus = inlineCorpus()
        val broken = corpus.filterNot { (_, text) -> Note.parse(text).render() == text }.keys
        assertEquals("notes that did not survive a round trip", emptySet<String>(), broken)
    }

    @Test
    fun `the migration takes only tag lines, never a word out of a song`() {
        // Every one of the archive's 179 hashtags sits on a line of its own, so a strict rule — a
        // line must be nothing but tags — costs nothing here and is what keeps `Ein #Traum von einem
        // Tag` intact if somebody ever writes one. Measured 2026-09-09.
        val corpus = corpus()
        val loose = corpus.filter { (_, text) ->
            val body = Note.parse(text).body
            val prose = body.lines().filterNot(Migration::isTagLine).joinToString("\n")
            hashtagsIn(prose).isNotEmpty()
        }.keys
        assertEquals(emptySet<String>(), loose)
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
    fun `the notes with no prose are 39 hashtag lines and one blank body`() {
        // `CLAUDE.md` has said 40 since the corpus was measured, and 40 is the number of notes whose
        // excerpt is empty — but it is two facts, not one, and the old assertion could not tell them
        // apart: it checked that every block was a tag line, which is vacuously true of a note with
        // no blocks at all. `Es ist kein Verdienst, sich hier gut einzufinden.md` is that note, two
        // newlines and nothing else.
        //
        // The other 39 are a title filed under a tag. Their bodies are the hashtag line the old
        // inline representation wrote, which the app no longer reads as tags — so the excerpt now
        // quotes it as the text it is, and these rows lead with `#lyrics/titel` until the author
        // clears the lines from the files. That is the archive's hygiene, not the app's.
        val corpus = corpus()
        val hashtagOnly = corpus.filterValues { text ->
            val body = Note.parse(text).body
            body.isNotBlank() && body.lines().filter(String::isNotBlank).all { line ->
                line.trim().split(Regex("""\s+""")).all { word -> word.startsWith("#") }
            }
        }
        assertEquals(39, hashtagOnly.size)
        assertTrue("Die Eule.md" in hashtagOnly.keys)
        assertEquals("#lyrics/titel", Excerpt.of(Note.parse(hashtagOnly.getValue("Die Eule.md"))))
        assertTrue(hashtagOnly.values.all { Note.parse(it).blocks.all { b -> b is Block.Paragraph } })

        val blank = corpus.filterValues { Note.parse(it).body.isBlank() }
        assertEquals(setOf("Es ist kein Verdienst, sich hier gut einzufinden.md"), blank.keys)
        assertEquals("", Excerpt.of(Note.parse(blank.values.single())))
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
    fun `a tag edit writes the frontmatter, leaves the body alone, and does not stamp updated`() {
        val corpus = corpus()
        val now = java.time.Instant.parse("2026-09-07T12:00:00Z")
        for ((name, text) in corpus) {
            val note = Note.parse(text)
            val edited = note.withTags(note.body, note.tags + "probe", now)!!
            assertTrue(name, "probe" in edited.frontmatter.tags)
            // Not one byte of the writing moves for a re-filing.
            assertEquals(name, note.body, edited.body)
            // And neither does the date. `updated` tracks the writing, not the filing — see the file
            // format contract in `CLAUDE.md`. Stamping here would have restamped the whole archive
            // the first time a tag was renamed across it.
            assertEquals(name, note.frontmatter.updated, edited.frontmatter.updated)
            // created is the archive's value and is never the app's to move.
            assertEquals(name, note.frontmatter.created, edited.frontmatter.created)
            // And the tags the user did not touch keep the order and the quoting the file had.
            assertEquals(name, note.tags, edited.frontmatter.tags.dropLast(1).map(Tags::normalize))
        }
    }

    @Test
    fun `renaming the archive's biggest tag moves not one date`() {
        // `CLAUDE.md`'s ninth definition-of-done item, over the real archive rather than a fixture.
        // `lyrics/snippet` is on 131 of the 168 notes, so this is the rename that would do the most
        // damage if `updated` moved with it — the 2015-2025 span is the thing this archive is kept
        // for, and restamping 131 notes would flatten most of it to one afternoon.
        val corpus = corpus()
        val now = java.time.Instant.parse("2026-09-09T12:00:00Z")
        var touched = 0
        for ((name, text) in corpus) {
            val note = Note.parse(text)
            if (note.tags.none { Tags.isUnder(it, "lyrics/snippet") }) continue
            val renamed = note.withTags(note.body, Tags.rename(note.tags, "lyrics/snippet", "songs/idee"), now)!!
            touched++
            assertEquals(name, note.frontmatter.updated, renamed.frontmatter.updated)
            assertEquals(name, note.frontmatter.created, renamed.frontmatter.created)
            // The words are not what is being renamed.
            assertEquals(name, note.body, renamed.body)
            assertTrue(name, "songs/idee" in renamed.frontmatter.tags)
            assertTrue(name, renamed.frontmatter.tags.none { it == "lyrics/snippet" })
        }
        assertEquals(131, touched)
    }

    @Test
    fun `a second rename over an already renamed archive writes nothing`() {
        // What `RenameResult.Partial` tells the user to do. If a rename stops half way, running it
        // again has to be safe on the notes it already did — and safe means writing nothing at all,
        // not writing the same bytes back.
        val corpus = corpus()
        val now = java.time.Instant.parse("2026-09-09T12:00:00Z")
        for ((name, text) in corpus) {
            val note = Note.parse(text)
            if (note.tags.none { Tags.isUnder(it, "busch") } ) continue
            val once = note.withTags(note.body, Tags.rename(note.tags, "busch", "wilhelm"), now)!!
            assertEquals(name, null, once.withTags(once.body, Tags.rename(once.tags, "busch", "wilhelm"), now))
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
