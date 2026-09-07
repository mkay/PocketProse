// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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

    /**
     * This note with a new body and a fresh `updated` stamp.
     *
     * **Returns `null` when [newBody] is identical to the current one.** That is the whole point:
     * `CLAUDE.md` forbids touching a file the app did not need to write, and an editor that stamps
     * `updated` every time a note is opened and closed would rewrite the entire archive within a
     * week of use — destroying exactly the history the archive is kept for. A no-op edit must be a
     * no-op on disk, and the only reliable way to know is to compare the bytes.
     *
     * `created` is never touched. Only `updated` moves, and only here.
     *
     * The stamp is ISO 8601 UTC with milliseconds and a literal `Z`, matching what every note in the
     * archive already carries — see the file format contract. It is passed in rather than read from
     * the clock so this stays a pure function and the test can pin it.
     */
    fun withBody(newBody: String, now: Instant): Note? {
        val kept = keepTrailingNewline(newBody)
        if (kept == body) return null
        return copy(frontmatter = frontmatter.withKey("updated", stamp(now)), body = kept)
    }

    /**
     * Give [newBody] back its final newline, if the note had one.
     *
     * All 168 notes in the archive end with a newline, as text files have since Unix. The editor
     * loses it the moment someone puts the cursor at the very end and types — the last line is then
     * simply the last line, with nothing after it — and the file quietly becomes the one note in the
     * folder that `diff` complains about.
     *
     * This restores a property the file already had rather than imposing one: a note that arrived
     * without a trailing newline keeps arriving and leaving without one. Note that the comparison in
     * [withBody] happens *after* this, so deleting the final newline and nothing else is correctly
     * seen as no change at all, and writes nothing.
     */
    private fun keepTrailingNewline(newBody: String): String =
        if (body.endsWith("\n") && newBody.isNotEmpty() && !newBody.endsWith("\n")) newBody + "\n" else newBody

    companion object {
        private val STAMP: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC)

        /** [now] in the archive's own timestamp spelling. */
        fun stamp(now: Instant): String = STAMP.format(now)

        fun parse(text: String): Note {
            val (frontmatter, body) = Frontmatter.split(text)
            return Note(frontmatter, body)
        }
    }


}
