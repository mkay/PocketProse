// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offset arithmetic behind the editor's live styling.
 *
 * Hiding `**` makes the text on screen shorter than the text in the file, and everything the editor
 * does afterwards depends on the shift being exact. This is the file where an off-by-one is caught,
 * rather than as a crash on somebody's song.
 *
 * [shown] reconstructs what the reader would actually see, which is the clearest way to assert on a
 * transformation whose whole job is what disappears.
 */
class LiveTextTest {

    /** [text] with everything [Live.of] hides removed — what ends up on screen. */
    private fun shown(text: String, cursor: Int = -1): String {
        val sel = if (cursor < 0) IntRange(-5, -5) else cursor..cursor
        val live = Live.of(text, sel)
        val sb = StringBuilder(text)
        live.hide.sortedByDescending { it.start }.forEach { sb.delete(it.start, it.end) }
        return sb.toString()
    }

    @Test
    fun `markers are hidden when the cursor is elsewhere`() {
        assertEquals("und das ist gut so", shown("und **das** ist gut so"))
        assertEquals("und das ist gut so", shown("und *das* ist gut so"))
        assertEquals("und das ist gut so", shown("und `das` ist gut so"))
        assertEquals("und das ist gut so", shown("und ___das___ ist gut so"))
    }

    @Test
    fun `markers come back when the cursor is inside the word`() {
        val text = "und **das** ist gut so"
        assertEquals(text, shown(text, cursor = 7))
        // …and at either edge, so arriving from either side shows what the word is made of.
        assertEquals(text, shown(text, cursor = 4))
        assertEquals(text, shown(text, cursor = 10))
        // One character past the closing marker is outside again.
        assertEquals("und das ist gut so", shown(text, cursor = 12))
    }

    @Test
    fun `only the span the cursor is in reveals itself`() {
        val text = "**eins** und **zwei**"
        assertEquals("**eins** und zwei", shown(text, cursor = 3))
        assertEquals("eins und **zwei**", shown(text, cursor = 16))
        assertEquals("eins und zwei", shown(text))
    }

    @Test
    fun `styles land in transformed coordinates`() {
        val live = Live.of("und **das** ist", IntRange(-5, -5))
        val visible = shown("und **das** ist")
        assertEquals("und das ist", visible)
        val bold = live.styles.single { !it.isMarker }
        assertEquals(Mark.BOLD, bold.mark)
        // "das" sits at 4..7 once the four marker characters are gone.
        assertEquals(4, bold.start)
        assertEquals(7, bold.end)
        assertEquals("das", visible.substring(bold.start, bold.end))
    }

    @Test
    fun `a heading loses its hashes and keeps its words`() {
        assertEquals("Strophe", shown("## Strophe"))
        val live = Live.of("## Strophe", IntRange(-5, -5))
        val h = live.styles.single { !it.isMarker }
        assertEquals(Mark.HEADING, h.mark)
        assertEquals(2, h.level)
        assertEquals(0, h.start)
        assertEquals(7, h.end)
    }

    @Test
    fun `a rule is never eaten as emphasis`() {
        // *** is three asterisks and also a horizontal rule. Without the guard the *** spelling
        // would swallow the rule and everything after it.
        assertEquals("***\nDu erreichst mich nicht", shown("***\nDu erreichst mich nicht"))
        assertEquals("- - -\nnoch eine Zeile", shown("- - -\nnoch eine Zeile"))
        assertEquals("---\ntext", shown("---\ntext"))
    }

    @Test
    fun `emphasis does not run across a blank line`() {
        // A single stray asterisk must not italicise the rest of the note.
        val text = "eine *Zeile\n\nund noch *eine"
        assertEquals(text, shown(text))
    }

    @Test
    fun `a bullet is not emphasis`() {
        assertEquals("* eins\n* zwei", shown("* eins\n* zwei"))
        assertEquals("- eins\n- zwei", shown("- eins\n- zwei"))
    }

    @Test
    fun `arithmetic and stray marks survive untouched`() {
        assertEquals("a * b * c", shown("a * b * c"))
        assertEquals("2 * 3", shown("2 * 3"))
    }

    @Test
    fun `the archive's awkward strings are left alone`() {
        // A tag, and the one sharp in 168 notes: neither is markup, so neither loses a character.
        assertEquals("#100", shown("#100"))
        assertEquals("Tarantino für zwei in F# Moll.", shown("Tarantino für zwei in F# Moll."))
        assertEquals("#lyrics/snippet", shown("#lyrics/snippet"))
    }

    @Test
    fun `hidden ranges never overlap and stay inside the text`() {
        val text = "## Titel\n\n**fett** und *kursiv* und `code`\n\n- - -\n\nEnde"
        for (cursor in -1 until text.length) {
            val live = Live.of(text, if (cursor < 0) IntRange(-5, -5) else cursor..cursor)
            var last = 0
            for (h in live.hide) {
                assertTrue("hidden range out of order at cursor $cursor", h.start >= last)
                assertTrue("hidden range past the end", h.end <= text.length)
                assertTrue("empty or reversed hidden range", h.end > h.start)
                last = h.end
            }
            // Every style must land inside the text that survives.
            val visible = text.length - live.hide.sumOf { it.end - it.start }
            for (s in live.styles) {
                assertTrue("style starts before 0 at cursor $cursor", s.start >= 0)
                assertTrue("style ends past the visible text at cursor $cursor", s.end <= visible)
            }
        }
    }
}
