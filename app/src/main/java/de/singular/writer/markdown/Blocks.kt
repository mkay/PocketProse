// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/** One structural piece of a note's body. */
sealed interface Block {

    /** `## Strophe`. Five notes use these; none uses a single `#`. */
    data class Heading(val level: Int, val text: String) : Block

    /**
     * A horizontal rule — `---`, `- - -`, or the `*`/`_` spellings.
     *
     * **There is no setext heading in this app.** Strict CommonMark reads a `---` that directly
     * follows a line of text as an underline making that line an H2, and one note in the archive
     * (`Sieger sehen anders aus.md`) is written that way. The author means a rule there; every
     * other `---` in the archive means a rule; and a line of a song silently becoming a heading is
     * the kind of surprise this app exists to not produce.
     *
     * So the rule wins unconditionally and setext is simply not implemented. That is a deliberate
     * departure from the spec, not an oversight — do not "fix" it.
     */
    data object Rule : Block

    /** Ordinary prose. The overwhelming majority of every note. */
    data class Paragraph(val text: String) : Block

    /** A run of `- ` items. */
    data class Bullets(val items: List<String>) : Block

    /**
     * A run of `> ` lines.
     *
     * Used here as an **indent**, not a citation: the author sets blocks of a lyric apart from the
     * running text, and `>` is the one Markdown construct whose meaning is "set this apart" and
     * whose rendering, in every reader, is a block pushed right. The alternatives are worse — four
     * spaces is a code block, and non-breaking spaces are a convention nobody else reads. The
     * archive contains no `>` line as of 2026-09-11, so this is a clean slate rather than a
     * reinterpretation of anything.
     *
     * Every line carries its own `> `; there is no lazy continuation. That is how every editor
     * writes it, and it keeps each shown line one-to-one with a line in the file.
     */
    data class Quote(val lines: List<String>) : Block

}

/**
 * Splits a note's body into [Block]s.
 *
 * Deliberately small. The supported vocabulary is headings, rules, bullets and paragraphs, and that
 * is the whole of it — this is a lyrics app, and a Markdown feature nobody in
 * the archive uses is a feature that can only misfire on text that meant something else. Anything
 * unrecognised stays a paragraph and reaches the screen as the words the user typed.
 *
 * Pure Kotlin, no Android: see the note on [Frontmatter].
 */
object Blocks {

    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")

    /**
     * A rule: three or more of `-`, `*` or `_`, alone on a line, with spaces allowed between them.
     *
     * The spacing clause is what makes `- - -` a rule, and 20 notes in the archive write it that
     * way. Without it those lines parse as three empty bullets.
     */
    private val RULE = Regex("""^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$""")

    private val BULLET = Regex("""^ {0,3}[-*+]\s+(.*)$""")

    /** `> ` and the line after it. The space is optional, as CommonMark has it: `>text` is quoted too. */
    private val QUOTE = Regex("""^ {0,3}>[ \t]?(.*)$""")

    fun parse(body: String): List<Block> {
        val out = ArrayList<Block>()
        val paragraph = StringBuilder()
        val bullets = ArrayList<String>()
        val quote = ArrayList<String>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                out += Block.Paragraph(paragraph.toString())
                paragraph.setLength(0)
            }
        }

        fun flushBullets() {
            if (bullets.isNotEmpty()) {
                out += Block.Bullets(bullets.toList())
                bullets.clear()
            }
        }

        fun flushQuote() {
            if (quote.isNotEmpty()) {
                out += Block.Quote(quote.toList())
                quote.clear()
            }
        }

        fun flush() {
            flushParagraph()
            flushBullets()
            flushQuote()
        }

        for (line in body.lines()) {
            when {
                line.isBlank() -> flush()

                // Before the bullet check: `- - -` matches both, and it is a rule.
                RULE.matches(line) -> {
                    flush()
                    out += Block.Rule
                }

                HEADING.matches(line) -> {
                    flush()
                    val m = HEADING.find(line)!!
                    out += Block.Heading(m.groupValues[1].length, m.groupValues[2].trim())
                }

                BULLET.matches(line) -> {
                    flushParagraph()
                    flushQuote()
                    bullets += BULLET.find(line)!!.groupValues[1]
                }

                QUOTE.matches(line) -> {
                    flushParagraph()
                    flushBullets()
                    quote += QUOTE.find(line)!!.groupValues[1]
                }

                else -> {
                    flushBullets()
                    flushQuote()
                    // Line breaks inside a paragraph are kept. In prose they would be reflowed; in
                    // a verse they are the verse, and this archive is verses.
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(line)
                }
            }
        }
        flush()
        return out
    }
}
