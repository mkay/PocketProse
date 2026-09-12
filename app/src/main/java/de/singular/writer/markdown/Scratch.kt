// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/**
 * Scratch lines: a line beginning with `+`, glued to its first character, is additional material
 * — an alternative line, a comment, a keyword — and not part of the draft.
 *
 * **`+` and no space.** `+ Marie` with a space is a bullet in CommonMark, and in `Blocks`, and in
 * every other editor; `+Marie` is text everywhere. So the archive's convention is the glued form,
 * which is also the one keystroke it costs to type, and which reads as what it means without a
 * legend: plus, additional. A `+` inside a line is arithmetic.
 *
 * **The file is never changed on its account.** The app writes no `+` of its own and removes none.
 * While editing, a scratch line is on screen, dimmed like a marker, so what is in the file is
 * never out of sight; the draft view — see `EditorScreen` — is a read-only look at the note with
 * these lines and their newlines left out, and nothing else in the app omits them. The library's
 * excerpt in particular quotes them like any other line, by its own rule of skipping nothing. This
 * is the one line-hiding rule the app has, and it is opt-in, per line, by a mark the writer put
 * there — not a guess at what a line looks like, which is the machinery `CLAUDE.md` records
 * deleting for the hashtag lines.
 */
object Scratch {

    /** A scratch line, from its `+` to its end. */
    val LINE = Regex("""^\+(?=\S).*$""", RegexOption.MULTILINE)

    /**
     * [text] without its scratch lines, each taken out with the newline that ended it — so a
     * verse with a scratch line in the middle closes up rather than keeping an empty row. A scratch
     * line the note ends on takes the newline before it instead; the one the note ends with stays,
     * as it does in every other view.
     */
    fun strip(text: String): String {
        val lines = text.split('\n')
        val kept = lines.filterNot(::isScratch)
        if (kept.size == lines.size) return text
        return kept.joinToString("\n")
    }

    fun isScratch(line: String): Boolean = line.length >= 2 && line[0] == '+' && !line[1].isWhitespace()
}
