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
     * Inline hashtags are folded in behind the frontmatter for the case of a note edited on a
     * desktop where a hashtag was typed into the body and the frontmatter not updated — which does
     * not occur in the archive today for ordinary tags, and costs one `distinct()` to be right
     * about anyway.
     *
     * **Percent tags are indexed from the frontmatter only, and that is a decision.** 21 of them are
     * written in a body whose frontmatter does not list them — `Müde.md` has `#100%` in its text and
     * no `100%` in its keys — so this deliberately under-counts: the drawer shows `100%` on the 11
     * notes that declare it, not on the 30-odd that mention it. The author chose this on 2026-09-07,
     * over indexing both, because the alternative either leaves the drawer disagreeing with the
     * files or tempts a later version into writing frontmatter into 21 notes nobody edited.
     *
     * The visible consequence, which is real: a body-only `#100%` sits on a line the app hides as a
     * tag line, and is not shown as a chip either, so it disappears from the screen. The text is
     * untouched on disk and reappears the moment the note is opened in anything else. If that proves
     * annoying, the fix is to chip percent tags without indexing them — not to start writing files.
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
