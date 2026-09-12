// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The format bar's buttons, which along with the save path are the only code that changes a note's
 * bytes on the user's behalf. They shipped once with no tests; this is that debt paid.
 */
class FormatActionsTest {

    private fun wrap(text: String, start: Int, end: Int, marker: String = "**") =
        FormatActions.wrap(text, start, end, marker)

    @Test
    fun `wrapping puts a marker on each side and keeps the selection on the words`() {
        val r = wrap("und das ist gut", 4, 7)
        assertEquals("und **das** ist gut", r.text)
        assertEquals("das", r.text.substring(r.selectionStart, r.selectionEnd))
    }

    @Test
    fun `wrapping again unwraps, so the button is not a one-way door`() {
        val once = wrap("und das ist gut", 4, 7)
        val twice = FormatActions.wrap(once.text, once.selectionStart, once.selectionEnd, "**")
        assertEquals("und das ist gut", twice.text)
        assertEquals("das", twice.text.substring(twice.selectionStart, twice.selectionEnd))
    }

    @Test
    fun `trailing whitespace in the selection stays outside the markers`() {
        // Dragging to the end of a line catches its trailing space. `**bold **` is not bold in
        // CommonMark at all — a closing marker preceded by a space does not close — so the markers
        // would be left as literal text in the note.
        val r = wrap("Oh es reitet dich \nWeil es", 0, 18)
        assertEquals("**Oh es reitet dich** \nWeil es", r.text)
    }

    @Test
    fun `a selection that is only whitespace is left alone`() {
        val r = wrap("a   b", 1, 4)
        assertEquals("a   b", r.text)
    }

    @Test
    fun `a collapsed selection changes nothing`() {
        assertEquals("und das", wrap("und das", 3, 3).text)
    }

    @Test
    fun `single-character markers work the same way`() {
        val r = wrap("und das ist", 4, 7, "*")
        assertEquals("und *das* ist", r.text)
        val back = FormatActions.wrap(r.text, r.selectionStart, r.selectionEnd, "*")
        assertEquals("und das ist", back.text)
    }

    @Test
    fun `wrapping never loses a character`() {
        val text = "Du erreichst mich nicht\nund das ist gut so"
        for (start in text.indices) {
            for (end in start..text.length) {
                val r = wrap(text, start, end)
                assertEquals(
                    "stripping the markers must give the original back ($start..$end)",
                    text,
                    r.text.replace("**", ""),
                )
            }
        }
    }

    @Test
    fun `quotes go on and come off like any other marker`() {
        val on = FormatActions.wrap("sag nie nie", 4, 7, "\"")
        assertEquals("sag \"nie\" nie", on.text)
        val off = FormatActions.wrap(on.text, on.selectionStart, on.selectionEnd, "\"")
        assertEquals("sag nie nie", off.text)
    }

    // ===== the indent =====

    @Test
    fun `a cursor indents its own line and a second tap takes it off`() {
        val on = FormatActions.quote("eins\nzwei\ndrei\n", 6, 6)
        assertEquals("eins\n> zwei\ndrei\n", on.text)
        assertEquals(8, on.selectionStart)
        val off = FormatActions.quote(on.text, on.selectionStart, on.selectionEnd)
        assertEquals("eins\nzwei\ndrei\n", off.text)
        assertEquals(6, off.selectionStart)
    }

    @Test
    fun `a selection indents every line it touches and stays over them`() {
        val text = "eins\nzwei\ndrei\nvier\n"
        val on = FormatActions.quote(text, 3, 12)
        assertEquals("> eins\n> zwei\n> drei\nvier\n", on.text)
        assertEquals(5, on.selectionStart)
        assertEquals(18, on.selectionEnd)
        assertEquals(text, FormatActions.quote(on.text, on.selectionStart, on.selectionEnd).text)
    }

    @Test
    fun `a partly indented block is completed, not cleared`() {
        val text = "> eins\nzwei\n"
        assertEquals("> eins\n> zwei\n", FormatActions.quote(text, 0, text.length - 1).text)
    }

    @Test
    fun `scratch goes on every covered line, glued, and a second tap takes it off`() {
        val text = "eins\nzwei\n\ndrei\n"
        val on = FormatActions.scratch(text, 2, 12)
        assertEquals("+eins\n+zwei\n\n+drei\n", on.text)
        assertEquals("ns\n+zwei\n\n+d", on.text.substring(on.selectionStart, on.selectionEnd))
        assertEquals(text, FormatActions.scratch(on.text, on.selectionStart, on.selectionEnd).text)
        // A partly marked block is completed; a bullet is not a scratch line, so it gets its own.
        assertEquals("+eins\n++ zwei", FormatActions.scratch("+eins\n+ zwei", 0, 12).text)
    }

    @Test
    fun `a cursor on an empty line starts an indented block there`() {
        val on = FormatActions.quote("eins\n\nzwei\n", 5, 5)
        assertEquals("eins\n> \nzwei\n", on.text)
        assertEquals(7, on.selectionStart)
        // And a second tap takes it off again.
        assertEquals("eins\n\nzwei\n", FormatActions.quote(on.text, 7, 7).text)
        // At the end of a note too, where there is no newline after the cursor.
        assertEquals("eins\n> ", FormatActions.quote("eins\n", 5, 5).text)
    }

    @Test
    fun `blank lines stay blank and do not count`() {
        val text = "eins\n\nzwei\n"
        val on = FormatActions.quote(text, 0, text.length - 1)
        assertEquals("> eins\n\n> zwei\n", on.text)
        assertEquals(text, FormatActions.quote(on.text, on.selectionStart, on.selectionEnd).text)
    }

    @Test
    fun `what the indent writes is what the parser reads back`() {
        val text = FormatActions.quote("eins\nzwei\n", 0, 9).text
        assertEquals(listOf(Block.Quote(listOf("eins", "zwei"))), Blocks.parse(text))
    }

    // ===== duplicating =====

    @Test
    fun `a cursor duplicates its line under itself and moves to the copy`() {
        val r = FormatActions.duplicate("eins\nzwei\ndrei\n", 6, 6)
        assertEquals("eins\nzwei\nzwei\ndrei\n", r.text)
        assertEquals(11, r.selectionStart)
    }

    @Test
    fun `a selection duplicates every line it touches and stays over the copy`() {
        val r = FormatActions.duplicate("eins\nzwei\ndrei\n", 2, 7)
        assertEquals("eins\nzwei\neins\nzwei\ndrei\n", r.text)
        assertEquals(12 to 17, r.selectionStart to r.selectionEnd)
        // Again, from the copy: a third.
        assertEquals("eins\nzwei\neins\nzwei\neins\nzwei\ndrei\n", FormatActions.duplicate(r.text, r.selectionStart, r.selectionEnd).text)
    }

    @Test
    fun `a last line without a newline is duplicated on a new line`() {
        assertEquals("eins\nzwei\nzwei", FormatActions.duplicate("eins\nzwei", 7, 7).text)
    }

    // ===== the rule =====

    @Test
    fun `a rule goes on its own line, in the spelling the archive uses`() {
        val result = FormatActions.rule("Erste Zeile\nZweite Zeile\n", at = 3)
        assertEquals("Erste Zeile\n\n- - -\n\nZweite Zeile\n", result.text)
        // The caret lands on the blank line the rule left behind it, not at the start of the line
        // that was already there: a rule separates two things and the second one is usually typed
        // next, and typing at the head of an existing line prepends to somebody else's sentence.
        assertEquals("Erste Zeile\n\n- - -\n".length, result.selectionStart)
        assertTrue(result.selectionStart == result.selectionEnd)
    }

    @Test
    fun `a rule does not stack blank lines that are already there`() {
        assertEquals(
            "Erste Zeile\n\n- - -\n\nZweite\n",
            FormatActions.rule("Erste Zeile\n\nZweite\n", at = 3).text,
        )
    }

    @Test
    fun `a rule at the end of a note needs nothing after it`() {
        assertEquals("Letzte Zeile\n\n- - -\n", FormatActions.rule("Letzte Zeile", at = 4).text)
    }

    @Test
    fun `a rule the parser reads back as a rule`() {
        val text = FormatActions.rule("Erste Zeile\nZweite\n", at = 3).text
        assertTrue(Blocks.parse(text).any { it is Block.Rule })
        // And the line above it is still a line of the song, not a heading.
        assertTrue(Blocks.parse(text).any { it is Block.Paragraph && it.text == "Erste Zeile" })
    }
}
