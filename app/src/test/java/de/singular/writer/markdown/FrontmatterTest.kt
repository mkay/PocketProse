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
        val text = "---\ntitle: \"x\"\nbear_id: 'ABC-123'\nweird:    spaced out\ntags: []\n---\nbody\n"
        val changed = Note.parse(text).let {
            it.copy(frontmatter = it.frontmatter.withKey("title", "y"))
        }
        assertTrue(changed.render().contains("bear_id: 'ABC-123'"))
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
