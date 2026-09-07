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

    // ===== clearing =====

    @Test
    fun `clearing takes the emphasis off and leaves the words`() {
        val text = "Du **erreichst** mich *nicht*\n"
        val result = FormatActions.clear(text, 0, text.length - 1)
        assertEquals("Du erreichst mich nicht\n", result.text)
    }

    @Test
    fun `clearing takes a heading and a bullet off their lines`() {
        assertEquals("Strophe\n", FormatActions.clear("## Strophe\n", 0, 10).text)
        assertEquals("eine Zeile\n", FormatActions.clear("- eine Zeile\n", 0, 12).text)
    }

    @Test
    fun `clearing reaches a prefix that starts before the selection`() {
        // Selecting the words of a heading without dragging over the hashes is the normal way to
        // select a heading, and the mark still has to come off.
        val result = FormatActions.clear("## Strophe\n", 3, 10)
        assertEquals("Strophe\n", result.text)
    }

    @Test
    fun `clearing removes marker characters, load-bearing or not`() {
        // `zwei * drei` is arithmetic and Live will not style it — but clearing takes the asterisk
        // anyway, and deliberately. Deciding which asterisks are marks needs the whole note, and a
        // button whose result depends on that is a button nobody can predict. What it touches is
        // exactly what was selected.
        assertEquals("zwei  drei\n", FormatActions.clear("zwei * drei\n", 0, 12).text)
    }

    @Test
    fun `clearing takes the longest marker first`() {
        assertEquals("beides\n", FormatActions.clear("***beides***\n", 0, 13).text)
    }

    @Test
    fun `clearing touches nothing outside the selection`() {
        val result = FormatActions.clear("**vorher** und **nachher**\n", 11, 15)
        assertEquals("**vorher** und **nachher**\n", result.text)
    }
}
