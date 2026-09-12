// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcerptTest {

    @Test
    fun `a leftover hashtag line is quoted as the text it is`() {
        // Die Eule.md, and 39 others: 15 bytes of body, all of it the old inline tag. It excerpted
        // to nothing while the app hid such lines; since tags left the body on 2026-09-09 it is a
        // line of text like any other and the row shows it. The archive's hygiene is the author's,
        // not the app's — see the Tag rules in `CLAUDE.md`.
        val note = Note.parse("---\ntitle: \"Die Eule\"\ntags:\n  - \"lyrics/titel\"\n---\n\n#lyrics/titel\n")
        assertEquals("#lyrics/titel", Excerpt.of(note))
    }

    @Test
    fun `rules are dropped, headings are kept, and a hashtag is just a word`() {
        val note = Note.parse("---\ntitle: \"x\"\ntags: []\n---\n\n#chords\n\n## Strophe\n\n- - -\n\nDu erreichst mich nicht\n")
        assertEquals("#chords Strophe Du erreichst mich nicht", Excerpt.of(note))
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
    fun `the export's embed comments do not reach the reader`() {
        // 24 of these across three notes, all beside a PDF link. Invisible in any renderer, and
        // stripped for display only — never removed from the file.
        val body = "[Pasted Graphic 12.pdf](Wer%20geht%20vor/x.pdf)<!-- {\"embed\":\"true\"} -->x3\n"
        val note = Note.parse("---\ntitle: \"x\"\ntags: []\n---\n\n$body")
        assertEquals("Pasted Graphic 12.pdfx3", Excerpt.of(note))
    }

    @Test
    fun `a note opening on a leftover hashtag line leads with it`() {
        // Müde.md's shape, and 59 others open this way. The excerpt is the first words of the body,
        // and while that line is still in the file those are the first words.
        val note = Note.parse(
            "---\ntitle: \"Müde\"\ntags:\n  - \"busch\"\n---\n\n#album/debut #100 #busch\n\nDu wirst nicht zurück kommen\n",
        )
        assertEquals("#album/debut #100 #busch Du wirst nicht zurück kommen", Excerpt.of(note))
    }

    @Test
    fun `scratch lines are left out, a spaced plus is a bullet and stays`() {
        // The row quotes the draft, as the editor's draft view does. `+Marie` is set aside;
        // `+ Marie` is a CommonMark bullet and is text like any other.
        val note = Note.parse("---\ntitle: \"x\"\ntags: []\n---\n\n+Marie, nie\nDu erreichst mich nicht\n+ oder doch\n")
        assertEquals("Du erreichst mich nicht oder doch", Excerpt.of(note))
    }

    @Test
    fun `a note that is nothing but scratch lines excerpts to nothing`() {
        val note = Note.parse("---\ntitle: \"x\"\ntags: []\n---\n\n+Marie\n+nie\n")
        assertEquals("", Excerpt.of(note))
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
