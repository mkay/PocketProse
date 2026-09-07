// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/**
 * Inline markup inside a line: emphasis, links, images, code.
 *
 * Only [strip] so far, which is what the library list needs — the editor's live styling in phase 4
 * needs the *spans* rather than the plain text, and will grow this file into a real inline scanner.
 * The two must agree when it does, so keep the vocabulary here and add to it in one place.
 */
object Inline {

    /** `![alt](path)` — dropped entirely from an excerpt, since a picture has no first line. */
    private val IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")

    /** `[text](url)` — the text survives, the target does not. */
    private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")

    /** `**bold**`, `*italic*`, `__bold__`, `_italic_`, `` `code` ``. */
    private val EMPHASIS = Regex("""(\*{1,2}|_{1,2}|`)(.+?)\1""")

    /**
     * [text] with its markup removed, leaving what a reader would actually read.
     *
     * Images go first and completely: an excerpt of a chord sheet should be its words, not the word
     * "attachments" eight times. Links keep their label. Emphasis keeps its content.
     *
     * This is for display in places too small for styling — a list row. It is never used to write a
     * file, and nothing here ever reaches disk.
     */
    fun strip(text: String): String = text
        .replace(IMAGE, "")
        .replace(LINK) { it.groupValues[1] }
        .replace(EMPHASIS) { it.groupValues[2] }
        .trim()
}
