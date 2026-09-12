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
     * Indent the lines the selection covers by putting `> ` in front of each, or take the indent off
     * again if every one of them already has it.
     *
     * Line-based, like a heading and unlike Bold: a collapsed cursor indents the line it sits on,
     * and a selection indents every line it touches, however little of each. A block is the unit
     * the author thinks in, and dragging exactly from the first character to the last is not
     * something anyone does on a phone.
     *
     * Blank lines inside a selection are left blank both ways. CommonMark ends a quote at a blank
     * line, so two verses indented together become two quotes rather than one — which this app
     * draws identically, and which spares the file the `>` alone on a line that keeping them one
     * block would need. A bare cursor on a blank line is the exception: that is somebody about to
     * start an indented block, and the `> ` goes on so what they type next is in it.
     *
     * One button, toggling, as the rest of the bar: all covered lines indented means take it off,
     * anything else means put it on where it is missing. The selection is kept over the same lines
     * afterwards so a second tap undoes the first.
     */
    fun quote(text: String, start: Int, end: Int): Formatted = prefixLines(text, start, end, QUOTE, QUOTE_MARK)

    /**
     * Mark the lines the selection covers as scratch by putting `+` in front of each, or take the
     * mark off again if every one of them has it — see `Scratch` for the convention. The same
     * line-based toggle as [quote], for the same reason: hitting exactly the first character of a
     * line is not something anyone does on a phone, and that is the one place the `+` can go.
     */
    fun scratch(text: String, start: Int, end: Int): Formatted = prefixLines(text, start, end, SCRATCH, SCRATCH_MARK)

    /**
     * The toggle behind [quote] and [scratch]: [mark] goes in front of every covered line that
     * [marked] does not already match, or comes off every line if all of them match.
     */
    private fun prefixLines(text: String, start: Int, end: Int, mark: String, marked: Regex): Formatted {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val firstLine = lineStart(text, from)
        val lastLineEnd = text.indexOf('\n', to).let { if (it < 0) text.length else it }

        val lines = text.substring(firstLine, lastLineEnd).split('\n')
        val allMarked = lines.filter { it.isNotBlank() }.let { it.isNotEmpty() && it.all(marked::containsMatchIn) }
        val starting = from == to && lines.size == 1 && lines.single().isBlank()

        var newFrom = from
        var newTo = to
        var at = firstLine
        val rebuilt = lines.joinToString("\n") { line ->
            val out = when {
                starting -> mark + line
                line.isBlank() -> line
                allMarked -> marked.replace(line, "")
                marked.containsMatchIn(line) -> line
                else -> "$mark$line"
            }
            val delta = out.length - line.length
            // A change on this line moves every selection edge on it — the start edge even when it
            // sits exactly at the line's start, so a cursor there lands after the mark and the next
            // character typed goes into the block rather than in front of it — but never off the
            // line's own start.
            if (from >= at) newFrom = (newFrom + delta).coerceAtLeast(at)
            if (to > at) newTo = (newTo + delta).coerceAtLeast(at)
            at += line.length + 1
            out
        }
        val result = text.substring(0, firstLine) + rebuilt + text.substring(lastLineEnd)
        return Formatted(result, newFrom, newTo.coerceAtLeast(newFrom))
    }

    /** What the indent writes. */
    private const val QUOTE = "> "

    /** `> ` at the start of a line, the space optional, as `Blocks` reads it. */
    private val QUOTE_MARK = Regex("""^ {0,3}>[ \t]?""")

    /** What the scratch button writes: the `+` alone, glued to the line — `+ ` would be a bullet. */
    private const val SCRATCH = "+"

    /**
     * A scratch line's mark, as `Scratch` reads it — plus a `+` alone on a line, which the toggle
     * left there on an empty line and should be able to take back.
     */
    private val SCRATCH_MARK = Regex("""^\+(?!\s)""")

    /** Where the line holding [at] begins. */
    private fun lineStart(text: String, at: Int): Int =
        text.lastIndexOf('\n', (at - 1).coerceAtLeast(0)).let { if (it < 0 || at == 0) 0 else it + 1 }

    /**
     * Copy the lines the selection covers and put the copy directly under them.
     *
     * A lyric repeats — a refrain sung twice, a chorus that comes back — and the way to write that
     * with a keyboard is select, copy, go to the end, Enter, paste, four steps for one intention.
     * Line-based like [quote]: a bare cursor copies its line, a selection every line it touches.
     *
     * The selection lands on the copy, so a second tap makes a third, which is what "3x" wants.
     * The lines' own newlines are kept as they are; a last line without one gets a newline put in
     * front of its copy instead, and the copy is then the line without one.
     */
    fun duplicate(text: String, start: Int, end: Int): Formatted {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val firstLine = lineStart(text, from)
        val lastLineEnd = text.indexOf('\n', to).let { if (it < 0) text.length else it }
        val block = text.substring(firstLine, lastLineEnd)

        val inserted = "\n$block"
        val result = text.substring(0, lastLineEnd) + inserted + text.substring(lastLineEnd)
        val shift = lastLineEnd + 1 - firstLine
        return Formatted(result, from + shift, to + shift)
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
