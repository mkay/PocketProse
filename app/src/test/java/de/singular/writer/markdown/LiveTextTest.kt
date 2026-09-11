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
        // would swallow the rule and everything after it. The rule itself is hidden — a line is
        // drawn in its place — but the text after it is untouched.
        assertEquals("\nDu erreichst mich nicht", shown("***\nDu erreichst mich nicht"))
        assertEquals("\nnoch eine Zeile", shown("- - -\nnoch eine Zeile"))
        assertEquals("\ntext", shown("---\ntext"))
        assertTrue(Live.of("***\n*nicht* kursiv", IntRange(-5, -5)).styles.any { it.mark == Mark.ITALIC })
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

    // ===== indents =====

    /** The shown text cut at the paragraph boundaries [Live] asks for — one row per entry. */
    private fun paragraphs(text: String, cursor: Int = -1): List<String> {
        val sel = if (cursor < 0) IntRange(-5, -5) else cursor..cursor
        val live = Live.of(text, sel)
        val visible = shown(text, cursor)
        val edges = live.styles.filter { it.mark == Mark.QUOTE && !it.isMarker }
            .flatMap { listOf(it.start, it.end) }
        val cuts = (listOf(0) + edges + visible.length).distinct().sorted()
        return cuts.zipWithNext { a, b -> visible.substring(a, b) }
    }

    @Test
    fun `an indented block is its own paragraphs and the rows come out as the file has them`() {
        // A, blank, B, C, blank, D — the case worked through in the note on `Live.of`.
        val text = "A\n\n> B\n> C\n\nD"
        assertEquals(listOf("A\n", "B", "C", "\nD"), paragraphs(text))
    }

    @Test
    fun `an indented line at either end of the note needs no newline of its own`() {
        assertEquals(listOf("B", "C"), paragraphs("> B\nC"))
        assertEquals(listOf("A", "B"), paragraphs("A\n> B"))
        assertEquals(listOf("B"), paragraphs("> B"))
    }

    @Test
    fun `the indent marker comes back under the cursor, inside its paragraph`() {
        val text = "> eins\n> zwei"
        assertEquals(listOf("eins", "zwei"), paragraphs(text))
        assertEquals(listOf("> eins", "zwei"), paragraphs(text, cursor = 3))
        assertEquals(listOf("eins", "> zwei"), paragraphs(text, cursor = 9))
        // The end of the line reveals it; the start of the next line does not.
        assertEquals(listOf("> eins", "zwei"), paragraphs(text, cursor = 6))
        assertEquals(listOf("eins", "> zwei"), paragraphs(text, cursor = 7))
    }

    @Test
    fun `emphasis inside an indented line still works`() {
        val live = Live.of("> **eins** zwei", IntRange(-5, -5))
        assertEquals(listOf("eins zwei"), paragraphs("> **eins** zwei"))
        val bold = live.styles.single { it.mark == Mark.BOLD }
        assertEquals(0 to 4, bold.start to bold.end)
    }

    @Test
    fun `a bare angle bracket is text`() {
        assertEquals(">", shown(">"))
        assertEquals("a > b", shown("a > b"))
    }

    // ===== rules =====

    @Test
    fun `a rule is hidden and reported where its empty line lands`() {
        val text = "**eins**\n\n- - -\n\nzwei"
        val live = Live.of(text, IntRange(-5, -5))
        assertEquals("eins\n\n\n\nzwei", shown(text))
        // Offset 6 in what is shown: "eins\n\n" is six characters, and the rule's line begins there.
        assertEquals(listOf(Rule(offset = 6, revealed = false)), live.rules)
    }

    @Test
    fun `a rule the cursor is on shows its dashes and says so`() {
        val text = "eins\n- - -\nzwei"
        assertEquals(text, shown(text, cursor = 7))
        assertEquals(listOf(Rule(offset = 5, revealed = true)), Live.of(text, 7..7).rules)
        // At either end of the line too, so arriving from above or below reveals it.
        assertEquals(text, shown(text, cursor = 5))
        assertEquals(text, shown(text, cursor = 10))
        // The line after it is somewhere else.
        assertEquals("eins\n\nzwei", shown(text, cursor = 11))
    }

    @Test
    fun `every spelling of a rule is one`() {
        for (rule in listOf("---", "- - -", "***", "___", "- - - -")) {
            assertEquals(rule, 1, Live.of("a\n$rule\nb", IntRange(-5, -5)).rules.size)
            assertEquals(rule, "a\n\nb", shown("a\n$rule\nb"))
        }
    }

    @Test
    fun `hidden ranges never overlap and stay inside the text`() {
        val text = "## Titel\n\n**fett** und *kursiv* und `code`\n\n- - -\n\n> ein\n> zwei\n\nEnde"
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
