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
     * A line that is nothing but hashtags.
     *
     * Its own kind of block because the app hoists tags out of the body and shows them as chips, so
     * these lines are never rendered as text. Their placement is worth knowing about: measured
     * across the archive, 85 notes end with one, 60 begin with one, one has it in the middle, and 8
     * carry them in more than one place. Writing a tag change back therefore means editing whichever
     * of these lines the note actually has, in place — not appending a new one.
     *
     * A line mixing hashtags with prose is a [Paragraph], not one of these. Only a line that is
     * entirely tags is hidden, because hiding words the user wrote would be unforgivable.
     */
    data class TagLine(val tags: List<String>) : Block
}

/**
 * Splits a note's body into [Block]s.
 *
 * Deliberately small. The supported vocabulary is headings, rules, bullets, tag lines and
 * paragraphs, and that is the whole of it — this is a lyrics app, and a Markdown feature nobody in
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

    fun parse(body: String): List<Block> {
        val out = ArrayList<Block>()
        val paragraph = StringBuilder()
        val bullets = ArrayList<String>()

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

        fun flush() {
            flushParagraph()
            flushBullets()
        }

        for (line in body.lines()) {
            when {
                line.isBlank() -> flush()

                // Before the bullet check: `- - -` matches both, and it is a rule.
                RULE.matches(line) -> {
                    flush()
                    out += Block.Rule
                }

                isTagLine(line) -> {
                    flush()
                    out += Block.TagLine(Tags.inBody(line))
                }

                HEADING.matches(line) -> {
                    flush()
                    val m = HEADING.find(line)!!
                    out += Block.Heading(m.groupValues[1].length, m.groupValues[2].trim())
                }

                BULLET.matches(line) -> {
                    flushParagraph()
                    bullets += BULLET.find(line)!!.groupValues[1]
                }

                else -> {
                    flushBullets()
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

    /**
     * Whether a line consists only of hashtags and whitespace.
     *
     * Note the double test: every whitespace-separated word must be a hashtag *and* the line must
     * contain at least one. `#chords #radio` is a tag line; `#100%#` is not, because it is not a
     * hashtag by [Tags]' rule, so that line stays visible text exactly as the author left it.
     */
    private fun isTagLine(line: String): Boolean {
        val words = line.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        return words.all { Tags.inBody(it).size == 1 && Tags.inBody(it)[0].length == it.length - 1 }
    }
}
