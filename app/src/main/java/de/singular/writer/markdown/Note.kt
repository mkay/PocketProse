// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/**
 * A note, parsed — and able to give back the exact bytes it was made from.
 *
 * [render] is [Frontmatter.raw] + [body] and nothing else. That is what makes `CLAUDE.md`'s
 * strictest acceptance test (open every note, edit none, `md5sum` unchanged) true by construction:
 * there is no reassembly step in which a key could be reordered or a quote style changed, because
 * the frontmatter was never taken apart in the first place. See [Frontmatter].
 *
 * Pure Kotlin, no Android.
 */
data class Note(
    val frontmatter: Frontmatter,
    val body: String,
) {

    /** The note's own title. Never the filename, never a heading — see the note in `CLAUDE.md`. */
    val title: String? get() = frontmatter.title

    /**
     * Every tag on this note, frontmatter first.
     *
     * The frontmatter is the index, because it is the only place `100%` and `50%` can be written.
     * Inline hashtags are folded in behind it for the case of a note edited on a desktop where a
     * hashtag was typed into the body and the frontmatter not updated — which does not occur in the
     * archive today, and costs one `distinct()` to be right about anyway.
     */
    val tags: List<String>
        get() = (frontmatter.tags + Tags.inBody(body)).map(Tags::normalize).distinct()

    /** The body as blocks, for rendering. */
    val blocks: List<Block> get() = Blocks.parse(body)

    /** The exact bytes this note was read from, when nothing has been changed. */
    fun render(): String = frontmatter.raw + body

    companion object {
        fun parse(text: String): Note {
            val (frontmatter, body) = Frontmatter.split(text)
            return Note(frontmatter, body)
        }
    }
}
