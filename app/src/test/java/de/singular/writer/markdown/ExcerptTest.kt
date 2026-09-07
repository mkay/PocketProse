// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcerptTest {

    @Test
    fun `a note that is only a tag line has no excerpt, and that is a normal answer`() {
        // Die Eule.md, and 35 others: 15 bytes of body, all of it the tag.
        val note = Note.parse("---\ntitle: \"Die Eule\"\ntags:\n  - \"lyrics/titel\"\n---\n\n#lyrics/titel\n")
        assertEquals("", Excerpt.of(note))
    }

    @Test
    fun `tags and rules are dropped but headings are kept`() {
        val note = Note.parse("---\ntitle: \"x\"\ntags: []\n---\n\n#chords\n\n## Strophe\n\n- - -\n\nDu erreichst mich nicht\n")
        assertEquals("Strophe Du erreichst mich nicht", Excerpt.of(note))
    }

    @Test
    fun `images contribute nothing, so a chord sheet excerpts as its words`() {
        val body = "## Strophe\n![](attachments/casablanca-chords-01.png)![](attachments/casablanca-chords-02.png)\n"
        val note = Note.parse("---\ntitle: \"x\"\ntags: []\n---\n\n$body")
        assertEquals("Strophe", Excerpt.of(note))
    }

    @Test
    fun `emphasis and links are unwrapped to what a reader reads`() {
        val note = Note.parse("---\ntitle: \"x\"\ntags: []\n---\n\nund **das** ist [gut](http://x) so\n")
        assertEquals("und das ist gut so", Excerpt.of(note))
    }

    @Test
    fun `a long note is cut on a word boundary and marked`() {
        val body = "wort ".repeat(80)
        val note = Note.parse("---\ntitle: \"x\"\ntags: []\n---\n\n$body\n")
        val excerpt = Excerpt.of(note, length = 40)
        assertTrue(excerpt.endsWith("…"))
        assertTrue(excerpt.length <= 41)
        assertTrue(excerpt.dropLast(1).endsWith("wort"))
    }
}
