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
     * Put [prefix] at the start of the line [at] falls on, or take it off again.
     *
     * Used for headings. The prefix goes before any text on the line but after nothing else — a
     * heading marker is only a heading marker at the very start of a line.
     */
    fun prefixLine(text: String, at: Int, prefix: String): Formatted {
        val caret = at.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0))
            .let { if (it < 0 || caret == 0) 0 else it + 1 }

        return if (text.startsWith(prefix, lineStart)) {
            Formatted(
                text = text.removeRange(lineStart, lineStart + prefix.length),
                selectionStart = (caret - prefix.length).coerceAtLeast(lineStart),
                selectionEnd = (caret - prefix.length).coerceAtLeast(lineStart),
            )
        } else {
            Formatted(
                text = text.substring(0, lineStart) + prefix + text.substring(lineStart),
                selectionStart = caret + prefix.length,
                selectionEnd = caret + prefix.length,
            )
        }
    }
}
