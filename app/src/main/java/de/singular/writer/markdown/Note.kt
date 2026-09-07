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
     * Since the `%` names were renamed on 2026-09-07 there is no tag the two representations cannot
     * both hold, so this and [editableTags] differ only when a note is edited elsewhere and left
     * disagreeing with itself. See `Tags`.
     */
    val tags: List<String>
        get() = (frontmatter.tags + Tags.inBody(body)).map(Tags::normalize).distinct()

    /** The body as blocks, for rendering. */
    val blocks: List<Block> get() = Blocks.parse(body)

    /** The exact bytes this note was read from, when nothing has been changed. */
    fun render(): String = frontmatter.raw + body

    /**
     * This note with a new body and a fresh `updated` stamp, its tags left as they are.
     *
     * [withTags] with the tags it already has — one implementation, so the two cannot drift over
     * what counts as a change worth writing.
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
    fun withBody(newBody: String, now: Instant): Note? = withTags(newBody, tags, now)

    /**
     * This note with a different tag set, written to both places at once.
     *
     * The two representations move together or not at all — that is the whole of the two-way sync,
     * and it is why this exists rather than the caller writing frontmatter and body separately and
     * hoping. `updated` is stamped once, for both.
     *
     * **Only the difference is written, and the difference is measured against [tags].** [newTags] is
     * what the note has after the edit; what it had before is everything on it by either spelling.
     * So a tag the user did not touch is not rewritten, reordered or requoted — and in particular a
     * tag written in the body but absent from the `tags:` list is left in exactly that state, being
     * neither added nor removed, rather than quietly filed on the author's behalf. Touch it and it
     * moves in both places, which is what touching it means.
     *
     * The frontmatter keeps its own order, new tags going on the end. Sorting it would rewrite every
     * line of a list the user only added to.
     *
     * Returns `null` when neither the body nor the tags actually changed, so an open-and-close still
     * writes nothing. See [withBody] for why that rule is worth this much care.
     */
    fun withTags(newBody: String, newTags: List<String>, now: Instant): Note? {
        val wanted = newTags.map(Tags::normalize).distinct()
        val current = tags
        val added = wanted - current.toSet()
        val removed = current - wanted.toSet()

        val kept = keepTrailingNewline(TagEdit.apply(keepTrailingNewline(newBody), added, removed))
        if (kept == body && added.isEmpty() && removed.isEmpty()) return null

        val declared = frontmatter.tags.map(Tags::normalize).distinct()
        val ordered = declared.filterNot { it in removed } + added
        val block = if (added.isEmpty() && removed.isEmpty()) frontmatter else frontmatter.withTags(ordered)
        return copy(frontmatter = block.withKey("updated", stamp(now)), body = kept)
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
