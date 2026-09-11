// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/** Text after a formatting action, and where the selection should end up. */
data class Formatted(val text: String, val selectionStart: Int, val selectionEnd: Int)

/**
 * What the format bar's buttons do to the text.
 *
 * Pure functions, deliberately, and lifted out of the composable that used to hold them. These are
 * the only code in the app besides the save path that changes a note's bytes on the user's behalf,
 * and until this file existed they had no tests at all — which is precisely the wrong combination.
 *
 * Every action toggles. Without that the Bold button is a one-way door, and the way back is to find
 * and delete asterisks the editor is deliberately hiding.
 */
object FormatActions {

    /**
     * Put [marker] on both sides of the selection, or take it off again if it is already there.
     *
     * Whitespace at the edges of the selection is left **outside** the markers. Selecting a line by
     * dragging past its end catches the trailing space, and `**bold **` is both ugly and, in
     * CommonMark, not bold at all — a closing marker preceded by a space does not close. So the
     * range is tightened before the markers go on, and an all-whitespace selection is left alone.
     */
    fun wrap(text: String, start: Int, end: Int, marker: String): Formatted {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(0, text.length)
        if (from >= to) return Formatted(text, from, to)

        var a = from
        var b = to
        while (a < b && text[a].isWhitespace()) a++
        while (b > a && text[b - 1].isWhitespace()) b--
        if (a >= b) return Formatted(text, from, to)

        val n = marker.length
        val wrapped = a >= n && b + n <= text.length &&
            text.regionMatches(a - n, marker, 0, n) &&
            text.regionMatches(b, marker, 0, n)

        return if (wrapped) {
            Formatted(
                text = text.substring(0, a - n) + text.substring(a, b) + text.substring(b + n),
                selectionStart = a - n,
                selectionEnd = b - n,
            )
        } else {
            Formatted(
                text = text.substring(0, a) + marker + text.substring(a, b) + marker + text.substring(b),
                selectionStart = a + n,
                selectionEnd = b + n,
            )
        }
    }

    /**
     * Put a horizontal rule on a line of its own after the line the cursor is in.
     *
     * Spelled `- - -`, which is what the archive uses: 20 notes carry that form against 2 with a
     * bare `---`. Both parse identically here, so the choice is only about which one the folder
     * already looks like — and the app has no business introducing a second spelling of something
     * the author has already settled.
     *
     * A blank line is kept on each side, which is what every rule in the archive has and what stops
     * the line above from becoming a setext heading in any stricter reader.
     *
     * The caret lands on the blank line **after** the rule, because a rule is something you put
     * between two things and the second one is usually next.
     */
    fun rule(text: String, at: Int): Formatted {
        val cursor = at.coerceIn(0, text.length)
        val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }

        // The newline that ends the current line is still in the tail, so what goes in front of the
        // rule is one newline plus a blank line, and what goes behind it is a blank line only when
        // the next line is not already one. At the end of the note it is the newline every note in
        // the archive ends with.
        val before = if (endsBlank(text, lineEnd)) "\n" else "\n\n"
        val atEnd = lineEnd >= text.length
        val after = if (!atEnd && startsBlank(text, lineEnd)) "" else "\n"
        val inserted = before + RULE + after

        val result = text.substring(0, lineEnd) + inserted + text.substring(lineEnd)
        val caret = lineEnd + inserted.length
        return Formatted(result, caret, caret)
    }

    /** The archive's spelling of a rule. 20 notes use it; 2 use a bare `---`. */
    private const val RULE = "- - -"

    private fun endsBlank(text: String, lineEnd: Int): Boolean {
        val lineStart = text.lastIndexOf('\n', (lineEnd - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        return lineEnd <= lineStart || text.substring(lineStart, lineEnd).isBlank()
    }

    private fun startsBlank(text: String, lineEnd: Int): Boolean {
        if (lineEnd >= text.length) return true
        val next = text.indexOf('\n', lineEnd + 1).let { if (it < 0) text.length else it }
        return text.substring((lineEnd + 1).coerceAtMost(text.length), next).isBlank()
    }
}
