// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The export's rendering, and the property that makes it the migration's inverse.
 *
 * Two corpus folders, the same as `CorpusTest` reads: the archive, whose 168 notes are all titled
 * and still carry their tag lines, and `Lyrics_Inline`, the same notes as the other editor's export
 * left them. On the second the tag half is an exact inverse — migrate, export inline, migrate again,
 * and the tags are the tags — and the body is the body that went in for every note that had one
 * tag line, at the head. On the first the test is that nothing is doubled.
 */
class ExportTest {

    private val archive = File("src/test/corpus/Lyrics")
    private val inline = File("src/test/corpus/Lyrics_Inline")

    private fun folder(dir: File): Map<String, String> {
        assumeTrue("${dir.name} not present — see CorpusTest", dir.isDirectory)
        return dir.listFiles { f: File -> f.name.endsWith(".md") }.orEmpty().associate { it.name to it.readText() }
    }

    private fun note(title: String?, tags: List<String>, body: String): Note {
        val list = if (tags.isEmpty()) "tags: []\n" else "tags:\n" + tags.joinToString("") { "  - \"$it\"\n" }
        val head = if (title == null) "" else "title: \"$title\"\n"
        return Note.parse("---\n${head}created: 2020-01-01T00:00:00.000Z\n$list---\n$body")
    }

    @Test
    fun `off, the bytes are the bytes`() {
        val n = note("Atlantik", listOf("lyrics/snippet"), "\n#lyrics/snippet\n\nDu erreichst mich nicht\n")
        assertEquals(n.render(), Export.render(n, inline = false))
    }

    @Test
    fun `a heading goes under the opening blank line, and a tag the body carries is not written twice`() {
        val n = note("Atlantik", listOf("lyrics/snippet"), "\n#lyrics/snippet\n\nDu erreichst mich nicht\n")
        assertEquals("\n# Atlantik\n\n#lyrics/snippet\n\nDu erreichst mich nicht\n", Export.inlined(n))
    }

    @Test
    fun `heading and tags together, in the shape the original export wrote`() {
        val n = note("Atlantik", listOf("lyrics/snippet", "radio"), "\nDu erreichst mich nicht\n")
        assertEquals("\n# Atlantik\n\n#lyrics/snippet #radio\n\nDu erreichst mich nicht\n", Export.inlined(n))
    }

    @Test
    fun `a note that opens with any heading gets none, whatever it says`() {
        val n = note("Atlantik", listOf("radio"), "\n# Something else\n\nWords\n")
        // Only the tag is missing, and it goes under the heading it found, not above it.
        assertEquals("\n# Something else\n\n#radio\n\nWords\n", Export.inlined(n))
    }

    @Test
    fun `only the missing tags are written`() {
        // Lyrics_Check's state after a rename: the body says radio, the list says radiox.
        val n = note("X", listOf("radiox", "busch"), "\n#radio #busch\n\nWords\n")
        assertEquals("\n# X\n\n#radiox\n\n#radio #busch\n\nWords\n", Export.inlined(n))
    }

    @Test
    fun `a title-and-tag note ends on its tag line, as the archive's own copy does`() {
        val n = note("Die Eule", listOf("lyrics/titel"), "\n#lyrics/titel\n")
        assertEquals("\n# Die Eule\n\n#lyrics/titel\n", Export.inlined(n))
        val bare = note("Adlerohr", listOf("lyrics/titel"), "")
        assertEquals("# Adlerohr\n\n#lyrics/titel\n", Export.inlined(bare))
        val empty = note("Es ist kein Verdienst", emptyList(), "\n\n")
        assertEquals("\n# Es ist kein Verdienst\n", Export.inlined(empty))
    }

    @Test
    fun `nothing is derived from a file name, and nothing to add is nothing written`() {
        assertNull(Export.inlined(note(null, emptyList(), "\nWords\n")))
        assertNull(Export.inlined(note("X", listOf("a"), "\n# X\n\n#a\n\nWords\n")))
    }

    @Test
    fun `over the archive, off is byte-identical and on doubles nothing`() {
        val tagLine = Regex("""^#[\p{L}\d][\w/-]*(\s+#[\p{L}\d][\w/-]*)*\s*$""", RegexOption.MULTILINE)
        for ((name, text) in folder(archive)) {
            val n = Note.parse(text)
            assertEquals(name, text, Export.render(n, inline = false))
            val out = Note.parse(Export.render(n, inline = true))
            assertEquals(name, n.frontmatter.raw, out.frontmatter.raw)
            // The heading once, at the top. The tag lines exactly as many as the file had, plus at
            // most one for tags the body did not carry.
            assertEquals(name, 1, Regex("""^# """, RegexOption.MULTILINE).findAll(out.body).count())
            val before = tagLine.findAll(n.body).count()
            val after = tagLine.findAll(out.body).count()
            assert(after == before || after == before + 1) { "$name: $before tag lines became $after" }
            // Every tag in the list is now somewhere in the body, once as a line.
            for (tag in n.tags) assert(Migration.carried(out.body).tags.contains(Tags.normalize(tag))) { "$name lost $tag" }
        }
    }

    @Test
    fun `over the inline folder, export undoes the migration and the migration undoes the export`() {
        var exact = 0
        for ((name, text) in folder(inline)) {
            val original = Note.parse(text)
            val moved = (Migration.plan(original) as Migration.Outcome.Move).note
            val back = Note.parse(Export.render(moved, inline = true))
            assertEquals(name, moved.frontmatter.raw, back.frontmatter.raw)

            // Migrating the export gives back the migrated note's tags exactly — nothing doubled,
            // nothing lost — and its words under the heading the export wrote. The heading stays,
            // because the migration leaves a titled note's heading alone by its own rule: that half
            // is one-way, and the test says so rather than pretending otherwise.
            // Untouched for the three notes with no tags: only a heading was written, and a
            // titled note's heading is not the migration's to move.
            val again = when (val outcome = Migration.plan(back)) {
                is Migration.Outcome.Move -> outcome.note
                Migration.Outcome.Untouched -> back
                is Migration.Outcome.Blocked -> error("$name blocked")
            }
            assertEquals(name, moved.frontmatter.raw, again.frontmatter.raw)
            val title = moved.title!!
            assertEquals(
                name,
                moved.body.trimStart('\n'),
                again.body.trimStart('\n').removePrefix("# $title").trimStart('\n'),
            )

            // And on the side the other editor keeps, the body is the body that went in for the
            // 54 notes whose one tag line sat at the head. The rest come back with the line at
            // the head — 99 had it at the foot — or with two lines' worth of tags on one, or with
            // a trailing tab gone: three things an inverse cannot know, none of them a tag lost.
            // The migration put the archive's blank line under the block it made — except on a
            // note left with no words at all, where it added nothing.
            val expected = if (moved.body.startsWith("\n")) "\n" + original.body else original.body
            if (expected == back.body) exact++
        }
        assertEquals(54, exact)
    }
}
