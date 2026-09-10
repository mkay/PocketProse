// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The round-trip is the app's central promise, so it is the first thing tested and the thing tested
 * hardest. Everything else in this file is a way for the round-trip to be quietly broken.
 */
class FrontmatterTest {

    private val note = """
        ---
        title: "Atlantik"
        created: 2025-04-25T16:56:15.332Z
        updated: 2025-04-26T15:04:16.978Z
        tags:
          - "lyrics/snippet"
        ---

        #lyrics/snippet

        Du erreichst mich nicht
    """.trimIndent() + "\n"

    @Test
    fun `an untouched note renders back byte for byte`() {
        assertEquals(note, Note.parse(note).render())
    }

    @Test
    fun `values are read unquoted`() {
        val fm = Note.parse(note).frontmatter
        assertEquals("Atlantik", fm.title)
        assertEquals("2025-04-25T16:56:15.332Z", fm.created)
        assertEquals(listOf("lyrics/snippet"), fm.tags)
    }

    @Test
    fun `a title ending in a question mark survives, since no filename could carry it`() {
        val text = "---\ntitle: \"Wer geht vor?\"\ntags: []\n---\n\nbody\n"
        assertEquals("Wer geht vor?", Note.parse(text).frontmatter.title)
    }

    @Test
    fun `an unknown key is preserved byte for byte, including its quoting`() {
        val text = "---\ntitle: \"x\"\nlegacy_id: 'ABC-123'\nweird:    spaced out\ntags: []\n---\nbody\n"
        val changed = Note.parse(text).let {
            it.copy(frontmatter = it.frontmatter.withKey("title", "y"))
        }
        assertTrue(changed.render().contains("legacy_id: 'ABC-123'"))
        assertTrue(changed.render().contains("weird:    spaced out"))
    }

    @Test
    fun `changing one value leaves every other line and the key order alone`() {
        val changed = Note.parse(note).let {
            it.copy(frontmatter = it.frontmatter.withKey("updated", "2026-01-01T00:00:00.000Z"))
        }
        assertEquals(
            listOf("title", "created", "updated", "tags"),
            changed.frontmatter.raw.lines().filter { it.contains(':') && !it.startsWith(" ") }
                .map { it.substringBefore(':') },
        )
        assertEquals("2026-01-01T00:00:00.000Z", changed.frontmatter.updated)
        assertEquals("Atlantik", changed.frontmatter.title)
        // The body is untouched by a frontmatter edit.
        assertEquals(Note.parse(note).body, changed.body)
    }

    @Test
    fun `a value is requoted the way it was already quoted`() {
        val single = "---\ntitle: 'x'\n---\nbody\n"
        val changed = Frontmatter.split(single).first.withKey("title", "y")
        assertTrue(changed.raw.contains("title: 'y'"))

        val bare = "---\ntitle: x\n---\nbody\n"
        assertTrue(Frontmatter.split(bare).first.withKey("title", "y").raw.contains("title: y"))
    }

    @Test
    fun `frontmatter is only ever the block at the very top`() {
        // Five notes use --- as a horizontal rule; two of them directly after a line of text.
        val text = "---\ntitle: \"x\"\ntags: []\n---\n\nfirst verse\n---\nsecond verse\n"
        val (fm, body) = Frontmatter.split(text)
        assertEquals("x", fm.title)
        assertEquals("\nfirst verse\n---\nsecond verse\n", body)
        assertEquals(text, fm.raw + body)
    }

    @Test
    fun `a note with no frontmatter is left entirely alone`() {
        val text = "just some words\nand more\n"
        val (fm, body) = Frontmatter.split(text)
        assertFalse(fm.present)
        assertNull(fm.title)
        assertEquals(text, body)
        assertEquals(text, fm.raw + body)
    }

    @Test
    fun `an opening delimiter with no closing one is a rule, not an unterminated block`() {
        val text = "---\nthis note starts with a rule and never closes\n"
        val (fm, body) = Frontmatter.split(text)
        assertFalse(fm.present)
        assertEquals(text, body)
    }

    @Test
    fun `the three untagged notes carry a literal empty list`() {
        val text = "---\ntitle: \"x\"\ntags: []\n---\nbody\n"
        assertEquals(emptyList<String>(), Frontmatter.split(text).first.tags)
    }

    @Test
    fun `tags keep their block shape, indentation and quoting when rewritten`() {
        val changed = Note.parse(note).frontmatter.withTags(listOf("lyrics/titel", "released"))
        assertTrue(changed.raw.endsWith("tags:\n  - \"lyrics/titel\"\n  - \"released\"\n---\n"))
        assertEquals(listOf("lyrics/titel", "released"), changed.tags)
    }

    @Test
    fun `emptying the tags collapses to the form the archive already uses`() {
        val changed = Note.parse(note).frontmatter.withTags(emptyList())
        assertTrue(changed.raw.contains("tags: []"))
        assertEquals(emptyList<String>(), changed.tags)
    }

    // A folder this app points at is opened by other editors too, and by hand. None of the shapes
    // below occur in the archive; all of them are legal YAML that something else will write, and all
    // of them read as *no tags* until 2026-09-10 — which then made [withTags] write over what it had
    // never seen. These are the read-tolerance cases, not a licence to start writing these shapes.

    @Test
    fun `a single tag written as a bare scalar is one tag, not none`() {
        val text = "---\ntitle: Adlerohr\ntags: lyrics/snippet\n---\nbody\n"
        assertEquals(listOf("lyrics/snippet"), Frontmatter.split(text).first.tags)
    }

    @Test
    fun `a quoted scalar tag is unquoted like any other value`() {
        val text = "---\ntags: \"lyrics/snippet\"\n---\nbody\n"
        assertEquals(listOf("lyrics/snippet"), Frontmatter.split(text).first.tags)
    }

    @Test
    fun `a scalar tag is not lost when the list is rewritten`() {
        val text = "---\ntitle: Adlerohr\ntags: released\n---\nbody\n"
        val fm = Frontmatter.split(text).first
        val changed = fm.withTags(fm.tags + "radio")
        assertEquals(listOf("released", "radio"), changed.tags)
        // Bare in, bare out: the scalar's own quoting is what the new entries copy.
        assertEquals("---\ntitle: Adlerohr\ntags:\n  - released\n  - radio\n---\n", changed.raw)
    }

    @Test
    fun `a comment on the tags line is a comment, not a tag`() {
        val text = "---\ntags: # the tags\n  - \"released\"\n---\nbody\n"
        assertEquals(listOf("released"), Frontmatter.split(text).first.tags)
    }

    @Test
    fun `block list items at column zero are read`() {
        val text = "---\ntags:\n- released\n- radio\ntitle: x\n---\nbody\n"
        val fm = Frontmatter.split(text).first
        assertEquals(listOf("released", "radio"), fm.tags)
        // The key after the list must not have been eaten by the list.
        assertEquals("x", fm.title)
    }

    @Test
    fun `rewriting a column-zero list replaces it rather than doubling it`() {
        val text = "---\ntags:\n- released\n- radio\ntitle: x\n---\nbody\n"
        val changed = Frontmatter.split(text).first.withTags(listOf("busch"))
        assertEquals(listOf("busch"), changed.tags)
        assertEquals("---\ntags:\n- busch\ntitle: x\n---\n", changed.raw)
    }

    @Test
    fun `a flow list keeps its brackets and its separator when rewritten`() {
        val text = "---\ntags: [released, radio]\ntitle: x\n---\nbody\n"
        val fm = Frontmatter.split(text).first
        assertEquals(listOf("released", "radio"), fm.tags)
        val changed = fm.withTags(fm.tags + "busch")
        assertEquals("---\ntags: [released, radio, busch]\ntitle: x\n---\n", changed.raw)
    }

    @Test
    fun `a flow list written without spaces keeps it that way`() {
        val text = "---\ntags: [\"released\",\"radio\"]\n---\nbody\n"
        val changed = Frontmatter.split(text).first.withTags(listOf("busch", "radio"))
        assertEquals("---\ntags: [\"busch\",\"radio\"]\n---\n", changed.raw)
    }

    @Test
    fun `emptying a flow list still collapses to the archive's empty form`() {
        val text = "---\ntags: [released]\n---\nbody\n"
        val changed = Frontmatter.split(text).first.withTags(emptyList())
        assertEquals("---\ntags: []\n---\n", changed.raw)
    }

    @Test
    fun `an empty list gaining its first tag becomes the block list the archive uses`() {
        // `[]` has no entry to copy a style from, so this is the one shape not carried over.
        val text = "---\ntitle: \"x\"\ntags: []\n---\nbody\n"
        val changed = Frontmatter.split(text).first.withTags(listOf("released"))
        assertEquals("---\ntitle: \"x\"\ntags:\n  - \"released\"\n---\n", changed.raw)
    }

    @Test
    fun `a tag holding a comma is quoted inside a flow list whatever its neighbours do`() {
        // Bare is what the neighbour asks for, and bare would end the entry at the comma.
        val text = "---\ntags: [released]\n---\nbody\n"
        val changed = Frontmatter.split(text).first.withTags(listOf("a,b"))
        assertEquals("---\ntags: [\"a,b\"]\n---\n", changed.raw)
        assertEquals(listOf("a,b"), changed.tags)
    }

    @Test
    fun `a flow list is split on the commas outside its quotes`() {
        val text = "---\ntags: [\"a,b\", 'c,d', plain]\n---\nbody\n"
        assertEquals(listOf("a,b", "c,d", "plain"), Frontmatter.split(text).first.tags)
    }

    @Test
    fun `a block written the way another editor writes it round-trips and stays unquoted`() {
        // What Obsidian left behind on 2026-09-10: no quotes anywhere, keys sorted alphabetically.
        // Reading it must work, and an edit must copy its style rather than restore the app's.
        val text = "---\ntags:\n  - lyrics/titel\n  - lyrics/snippet\ntitle: Adlerohr2\n---\n\n"
        val fm = Frontmatter.split(text).first
        assertEquals("Adlerohr2", fm.title)
        assertEquals(listOf("lyrics/titel", "lyrics/snippet"), fm.tags)
        assertEquals(text, fm.raw + Frontmatter.split(text).second)
        val changed = fm.withKey("title", "Adlerohr3").withTags(fm.tags + "released")
        assertTrue(changed.raw.contains("title: Adlerohr3"))
        assertTrue(changed.raw.contains("  - released"))
        assertTrue("\"" !in changed.raw)
        // And the order the other editor chose is the order that comes back.
        assertTrue(changed.raw.indexOf("tags:") < changed.raw.indexOf("title:"))
    }

    @Test
    fun `editing a CRLF note does not convert it to LF`() {
        val text = "---\r\ntitle: \"x\"\r\ntags: []\r\n---\r\nbody\r\n"
        val changed = Frontmatter.split(text).first.withKey("title", "y")
        assertEquals("---\r\ntitle: \"y\"\r\ntags: []\r\n---\r\n", changed.raw)
        assertTrue("\n" !in changed.raw.replace("\r\n", ""))
    }

    @Test
    fun `an edit does not duplicate the closing delimiter`() {
        // The closing --- was once landing inside the block's own line list, so any edit emitted it
        // twice and the body's first line became frontmatter. Byte-count it rather than eyeball it.
        val changed = Note.parse(note).let {
            it.copy(frontmatter = it.frontmatter.withKey("title", "Atlantik"))
        }
        assertEquals(2, changed.frontmatter.raw.lines().count { line -> line == "---" })
        assertEquals(note, changed.render())
    }

    @Test
    fun `CRLF frontmatter is recognised rather than swallowing the file`() {
        val text = "---\r\ntitle: \"x\"\r\n---\r\nbody\r\n"
        val (fm, body) = Frontmatter.split(text)
        assertTrue(fm.present)
        assertEquals("body\r\n", body)
        assertEquals(text, fm.raw + body)
    }
}
