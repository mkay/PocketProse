// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/**
 * How much writing is in a note: words, characters, lines, paragraphs.
 *
 * **Counted over the text as it stands, with nothing excused.** A leftover `#lyrics/snippet` at the
 * head of a note is a line and a word, because `CLAUDE.md` settled that a `#` in a body is text —
 * 165 notes carry one of those lines and the app shows them everywhere else. A counter that quietly
 * skipped them would be the hiding machinery growing back in the one screen nobody would check it
 * in, and it would also be lying: the line *is* in the file. The same goes for `**` and `##`. What
 * is counted is what the file holds.
 *
 * The frontmatter is not part of it. That block is filing rather than writing, and a note whose
 * word count moved because a tag was added would be reporting the wrong thing.
 *
 * Pure Kotlin, no Android: four small rules that are easier to get wrong than they look, and a
 * JVM test is the cheapest place to pin them.
 */
data class Stats(val words: Int, val characters: Int, val lines: Int, val paragraphs: Int) {

    companion object {

        /**
         * The counts for one body.
         *
         * - **Words** are runs of non-whitespace. `**bold**` is one word and so is `F#`; an em dash
         *   standing alone is one too, which is the price of not building a dictionary.
         * - **Characters** are every character in the body, spaces and newlines included. The
         *   alternative — "characters, not counting spaces" — is a second number that answers a
         *   question nobody asked, and picking silently between the two would make the figure mean
         *   whatever the reader assumed.
         * - **Lines** are the lines that have writing on them. A song is read in lines, and the
         *   blank ones between its verses are spacing rather than content; counting them would make
         *   the number a measure of how airy the note is. This is the one count that is a judgement
         *   rather than an arithmetic, and it is made here so both callers cannot disagree.
         * - **Paragraphs** are the blocks a blank line separates, which in a lyric are its verses.
         *   Named for the text and not for the song on purpose: across the archive's 168 notes this
         *   comes to 818 blocks, but 66 of them are the leftover hashtag line standing alone at the
         *   head of a note, and 12 notes have a `---` rule or an image line sitting as a block of its
         *   own. "Paragraphs" is a claim about the text and is true; "verses" would be a claim about
         *   the song and would be one out for 39% of the archive. The gap closes itself when those
         *   hashtag lines are swept out of the files — which is the author's to do, never this app's.
         *
         * A body of nothing but whitespace is four zeros rather than one empty paragraph.
         */
        fun of(body: String): Stats = Stats(
            words = body.split(WHITESPACE).count { it.isNotEmpty() },
            characters = body.length,
            lines = body.lineSequence().count { it.isNotBlank() },
            paragraphs = body.split(BLANK_LINES).count { it.isNotBlank() },
        )

        private val WHITESPACE = Regex("\\s+")

        /**
         * A run of one or more blank lines — the separator between blocks.
         *
         * Lines that hold only spaces or tabs count as blank, because a stray space on the empty
         * line between two verses is invisible and must not split one verse into two.
         */
        private val BLANK_LINES = Regex("\\n[ \\t]*\\n(?:[ \\t]*\\n)*")
    }
}
