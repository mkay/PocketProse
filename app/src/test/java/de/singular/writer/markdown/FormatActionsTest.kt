// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
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
    fun `a heading prefix goes on and comes off`() {
        val on = FormatActions.prefixLine("Strophe\nund weiter", 3, "## ")
        assertEquals("## Strophe\nund weiter", on.text)
        val off = FormatActions.prefixLine(on.text, on.selectionStart, "## ")
        assertEquals("Strophe\nund weiter", off.text)
    }

    @Test
    fun `the prefix lands on the line the caret is on, not the first line`() {
        val r = FormatActions.prefixLine("erste\nzweite\ndritte", 8, "## ")
        assertEquals("erste\n## zweite\ndritte", r.text)
    }

    @Test
    fun `a prefix at the very start of the text works`() {
        assertEquals("## erste\nzweite", FormatActions.prefixLine("erste\nzweite", 0, "## ").text)
    }
}
