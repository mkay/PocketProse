// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/** What a run of marked-up text is. */
enum class Mark { BOLD, ITALIC, BOLD_ITALIC, CODE, HEADING, RULE, QUOTE }

/**
 * One piece of markup found in the text, in **original** coordinates.
 *
 * [open] and [close] are the marker runs — `**`, `_`, `` ` ``, or a heading's `## ` — and [content]
 * is what sits between them. A heading has an empty [close] at the end of its line. A quote's
 * [open] is the `> ` and its [close] is the newline ending the line, for the reason given at
 * [Live.of]. A rule is all marker: its [open] is the whole line, and [content] and [close] are
 * empty.
 */
data class Span(
    val mark: Mark,
    val open: IntRange,
    val content: IntRange,
    val close: IntRange,
    /** For [Mark.HEADING] only: 1 to 6. */
    val level: Int = 0,
)

/** A range of original text the editor does not display. */
data class Hidden(val start: Int, val end: Int)

/** A styled range, in **transformed** coordinates — what the reader actually sees. */
data class Styled(val start: Int, val end: Int, val mark: Mark, val level: Int, val isMarker: Boolean)

/**
 * A horizontal rule, in **transformed** coordinates: [offset] is somewhere on its line, and
 * [revealed] says whether the cursor is on it and the dashes are showing.
 */
data class Rule(val offset: Int, val revealed: Boolean)

/**
 * The whole answer for one pass: what to hide, how to paint what is left, and where the rules are.
 *
 * [rules] exists because a rule is the one mark that is not painted onto its characters but drawn
 * across the page in their place. `- - -` is hidden like any other marker, which leaves an empty
 * line, and the field draws a line through that empty line — the divider every other reader would
 * show for it, at the full width of the writing. The cursor arriving on the line brings the dashes
 * back, dimmed, exactly as it brings `**` back, so what is in the file is never out of reach.
 */
data class LiveText(val hide: List<Hidden>, val styles: List<Styled>, val rules: List<Rule> = emptyList())

/**
 * Turns the raw text of a note into "hide these characters, paint those ranges" — which is the whole
 * of the editor's live styling, decided here rather than in a composable.
 *
 * **The point of this file is that it has no Android in it.** Hiding characters means the text on
 * screen is shorter than the text in the file, and every offset the editor deals with afterwards —
 * where the cursor goes, what a tap selects, which characters a swipe deletes — depends on getting
 * that arithmetic right. Compose maintains the cursor mapping for us (see the note on the
 * transformation that calls this), but *what* to hide, and where the styles land once things are
 * hidden, is ours. An off-by-one here is a crash on somebody's song, so it is a unit test.
 *
 * ## Markers appear when the cursor is inside them
 *
 * A span whose markers the cursor sits within is **revealed**: its `**` stay on screen, dimmed, so
 * that editing them is possible and so that it is obvious what is being edited. Every other span is
 * hidden and simply looks bold. This is what lets someone who has never heard of Markdown write in
 * it without ever meeting it — and lets someone who has, fix it.
 */
object Live {

    /**
     * Emphasis, in the four spellings this app supports.
     *
     * Ordered longest-first so `**` is tried before `*`; a shorter marker matching first would turn
     * `**bold**` into an italic containing a stray asterisk.
     *
     * Content must not be empty and must not begin or end with whitespace — `* ` at the start of a
     * line is a bullet, and `a * b * c` is arithmetic, not emphasis. Neither may it span a blank
     * line: emphasis does not run across a paragraph break, and without that guard a single stray
     * asterisk would italicise the rest of the note.
     */
    private val EMPHASIS = listOf(
        "***" to Mark.BOLD_ITALIC,
        "___" to Mark.BOLD_ITALIC,
        "**" to Mark.BOLD,
        "__" to Mark.BOLD,
        "*" to Mark.ITALIC,
        "_" to Mark.ITALIC,
        "`" to Mark.CODE,
    )

    /** `## Strophe` — the hashes and the space after them are the marker. */
    private val HEADING = Regex("""^(#{1,6})[ \t]+(?=\S)""", RegexOption.MULTILINE)

    /**
     * `> ` at the start of a line with words after it — the marker of an indented block, see
     * `Block.Quote`. The content is the rest of the line; the marker hides like a heading's hashes
     * and comes back under the cursor. A `>` alone on a line is left as the character it is: a
     * paragraph with nothing in it has nothing to indent, and hiding all of it would make the line
     * vanish from the screen.
     */
    private val QUOTE = Regex("""^ {0,3}>[ \t]?(?=\S)""", RegexOption.MULTILINE)

    /**
     * A line that is a horizontal rule, which must never be read as emphasis.
     *
     * `***` is three asterisks and also a rule, and 20 notes in the archive use `- - -`. Without
     * this the `***` spelling would eat a rule and everything after it.
     */
    private val RULE_LINE = Regex("""^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$""", RegexOption.MULTILINE)

    /**
     * Scan [text] for everything the editor styles.
     *
     * Deliberately a small vocabulary — emphasis, code, headings, quotes and rules. Links and images are
     * the segment splitter's business. Anything not recognised is left as the characters the user
     * typed, which is always the safe answer.
     */
    fun scan(text: String): List<Span> {
        val spans = ArrayList<Span>()
        val ruleLines = RULE_LINE.findAll(text).map { it.range }.toList()
        fun inRule(i: Int) = ruleLines.any { i in it }

        for (r in ruleLines) {
            spans += Span(mark = Mark.RULE, open = r, content = r.last + 1 until r.last + 1, close = r.last + 1 until r.last + 1)
        }

        for (m in QUOTE.findAll(text)) {
            if (inRule(m.range.first)) continue
            val lineEnd = text.indexOf('\n', m.range.last + 1).let { if (it < 0) text.length else it }
            spans += Span(
                mark = Mark.QUOTE,
                open = m.range.first until m.range.last + 1,
                content = m.range.last + 1 until lineEnd,
                // The line's newline is not the close: which newlines go is decided per block in
                // `quoteParagraphs`, not per line.
                close = lineEnd until lineEnd,
            )
        }

        for (m in HEADING.findAll(text)) {
            val lineEnd = text.indexOf('\n', m.range.last).let { if (it < 0) text.length else it }
            spans += Span(
                mark = Mark.HEADING,
                open = m.range.first until m.range.last + 1,
                content = m.range.last + 1 until lineEnd,
                close = lineEnd until lineEnd,
                level = m.groupValues[1].length,
            )
        }

        var i = 0
        outer@ while (i < text.length) {
            if (inRule(i)) { i++; continue }
            for ((marker, mark) in EMPHASIS) {
                if (!text.startsWith(marker, i)) continue
                val contentStart = i + marker.length
                if (contentStart >= text.length || text[contentStart].isWhitespace()) continue
                var j = contentStart
                while (j < text.length) {
                    // Emphasis never runs across a paragraph break.
                    if (text.startsWith("\n\n", j)) break
                    if (text.startsWith(marker, j) && !text[j - 1].isWhitespace() && !inRule(j)) {
                        spans += Span(mark, i until contentStart, contentStart until j, j until j + marker.length)
                        i = j + marker.length
                        continue@outer
                    }
                    j++
                }
            }
            i++
        }
        return spans.sortedBy { it.open.first }
    }

    /**
     * Decide what to hide and how to paint, given where the cursor is.
     *
     * [selection] is in original coordinates and may be empty (a caret). A span is **revealed** —
     * markers kept, dimmed — when the selection touches it at all, including sitting exactly at an
     * edge, so that arriving at a word from either side shows what it is made of.
     *
     * Styles come back in **transformed** coordinates, already shifted for everything hidden before
     * them, because that is the space the text field paints in. The shift is computed by walking the
     * hidden ranges in order and accumulating their lengths — the one piece of offset arithmetic
     * this app does itself.
     *
     * ## Indents are paragraphs, and paragraphs eat newlines
     *
     * An indented line is pushed right with a paragraph indent, which is the only way a text field
     * indents a wrapped line's continuation as well as its first. Compose makes every range that
     * carries a paragraph style a paragraph of its own, stacked under the one before it — which is
     * a line break — so a newline sitting at a paragraph boundary would be a *second* break, and an
     * empty row. Exactly one newline per boundary is therefore hidden, and which one is decided in
     * [quoteParagraphs] so that every line of the file is one row on screen: the paragraph of an
     * indented line runs on over the blank lines after it when another indented line follows, so
     * those blank lines are rows inside it and only the last newline before the next block goes.
     * The first version hid the newline after every indented line and the one before every block,
     * which for one blank line between two blocks was both of that line's newlines — and the gap
     * came out doubled.
     *
     * The `> ` itself reveals under the cursor like any marker, and the [Mark.QUOTE] style over the
     * paragraph — marker included when it is showing — is what the transformation turns into the
     * paragraph indent.
     */
    fun of(text: String, selection: IntRange): LiveText {
        val spans = scan(text)
        val hide = ArrayList<Hidden>()
        val marked = ArrayList<Triple<IntRange, Mark, Pair<Int, Boolean>>>()
        val rules = ArrayList<Pair<Int, Boolean>>()

        for (s in spans) {
            if (s.mark == Mark.QUOTE) {
                // The cursor anywhere on the line, its end included, reveals the `> `; the start of
                // the next line does not.
                val touched = selection.first <= s.content.last + 1 && selection.last >= s.open.first
                if (touched) marked += Triple(s.open, s.mark, s.level to true)
                else hide += Hidden(s.open.first, s.open.last + 1)
                continue
            }
            val touched = selection.first <= s.close.last + 1 && selection.last >= s.open.first
            if (s.mark == Mark.RULE) rules += s.open.first to touched
            if (touched) {
                if (!s.open.isEmpty()) marked += Triple(s.open, s.mark, s.level to true)
                if (!s.close.isEmpty()) marked += Triple(s.close, s.mark, s.level to true)
            } else {
                if (!s.open.isEmpty()) hide += Hidden(s.open.first, s.open.last + 1)
                if (!s.close.isEmpty()) hide += Hidden(s.close.first, s.close.last + 1)
            }
            if (!s.content.isEmpty()) marked += Triple(s.content, s.mark, s.level to false)
        }

        for ((start, end) in quoteParagraphs(text, spans.filter { it.mark == Mark.QUOTE }, hide)) {
            // The paragraph: marker and words together, so a revealed `> ` sits in the indent.
            if (end > start) marked += Triple(start until end, Mark.QUOTE, 0 to false)
        }

        hide.sortBy { it.start }
        val styles = marked
            .map { (range, mark, meta) ->
                Styled(
                    start = shift(range.first, hide),
                    end = shift(range.last + 1, hide),
                    mark = mark,
                    level = meta.first,
                    isMarker = meta.second,
                )
            }
            .filter { it.end > it.start }
            .sortedBy { it.start }
        return LiveText(
            hide = hide,
            styles = styles,
            rules = rules.map { (at, revealed) -> Rule(shift(at, hide), revealed) },
        )
    }

    /**
     * The paragraph each indented line becomes, as `start until end` in original coordinates, with
     * the newlines that have to go for the rows to add up put into [hide].
     *
     * The arithmetic: a paragraph shows one row per newline in it plus one, and a paragraph boundary
     * is itself a row break. So for the screen to have exactly one row per line of the file, exactly
     * one newline must vanish at every boundary — and never a newline that is a row of its own.
     * Line by line, for an indented line:
     *
     * - **Forward.** If the next non-blank line is indented too, this paragraph runs on over the
     *   blank lines between (each a row inside it) and the last newline before that line is the
     *   boundary, hidden. If the next non-blank line is ordinary text, this line's own newline is
     *   the boundary and the blank lines belong to the text's paragraph, which starts with them.
     *   If only blank lines follow to the end of the note, nothing is hidden and the paragraph runs
     *   to the end: the newline the note ends with stays a row, the one the cursor sits on.
     * - **Backward.** The line above being indented is that line's forward case. Otherwise the
     *   newline ending the line above — text or the last of the blanks under text — is the
     *   boundary, hidden; the text's paragraph keeps its own newline and so its rows. Blank lines
     *   only, from the top of the note: the boundary newline goes when two or more of them keep a
     *   paragraph of their own, and a single one is taken into this paragraph instead, since hiding
     *   its newline would leave nothing to be a row.
     */
    private fun quoteParagraphs(text: String, quotes: List<Span>, hide: MutableList<Hidden>): List<Pair<Int, Int>> {
        if (quotes.isEmpty()) return emptyList()
        val starts = ArrayList<Int>()
        var i = 0
        while (true) {
            starts += i
            val nl = text.indexOf('\n', i)
            if (nl < 0) break
            i = nl + 1
        }
        fun endOf(line: Int) = if (line + 1 < starts.size) starts[line + 1] - 1 else text.length
        fun blank(line: Int) = text.substring(starts[line], endOf(line)).isBlank()
        val quoted = quotes.map { it.open.first }.toSet()
        fun isQuote(line: Int) = starts[line] in quoted
        fun hideNewline(at: Int) {
            if (at < text.length && text[at] == '\n' && hide.none { it.start == at }) hide += Hidden(at, at + 1)
        }

        val out = ArrayList<Pair<Int, Int>>()
        for (q in quotes) {
            val line = starts.indexOf(q.open.first)
            var start = starts[line]
            var end = endOf(line)

            var next = line + 1
            while (next < starts.size && blank(next)) next++
            when {
                next >= starts.size -> end = text.length
                isQuote(next) -> {
                    end = endOf(next - 1)
                    hideNewline(end)
                }
                else -> hideNewline(end)
            }

            if (line > 0 && !isQuote(line - 1)) {
                var above = line - 1
                while (above >= 0 && blank(above)) above--
                when {
                    above >= 0 && isQuote(above) -> Unit
                    above >= 0 -> hideNewline(endOf(line - 1))
                    line >= 2 -> hideNewline(endOf(line - 1))
                    else -> start = 0
                }
            }
            out += start to end
        }
        return out
    }

    /** [offset] in original coordinates, moved to where it lands once [hide] has been removed. */
    private fun shift(offset: Int, hide: List<Hidden>): Int {
        var out = offset
        for (h in hide) {
            if (h.end <= offset) out -= (h.end - h.start)
            else if (h.start < offset) out -= (offset - h.start)
        }
        return out
    }
}
