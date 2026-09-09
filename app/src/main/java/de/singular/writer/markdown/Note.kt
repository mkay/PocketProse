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
     * Every tag on this note.
     *
     * The frontmatter list, and nothing else. Body hashtags were folded in behind it until
     * 2026-09-09, when the frontmatter became the single source of truth — see the Tag rules in
     * `CLAUDE.md` for why the two-way sync was right while the notes were shared with an editor
     * that read hashtags, and wrong once they were not.
     *
     * On the archive as it stands this is not a change of answer: all 168 notes agree on their tags
     * in both spellings, so the union and the frontmatter alone return the same list for every one
     * of them. What changes is what happens next — a hashtag typed into a body from here on is a
     * word in a song, not a filing instruction.
     */
    val tags: List<String>
        get() = frontmatter.tags.map(Tags::normalize).distinct()

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
     * This note with a different tag set.
     *
     * Tags and body change through one function because this is where "nothing changed, write
     * nothing" is decided, and two write paths would be two chances to get that wrong.
     *
     * **Only the difference is written, and the difference is measured against [tags].** [newTags] is
     * what the note has after the edit. So a tag the user did not touch is not rewritten, reordered
     * or requoted.
     *
     * Tags live in the frontmatter and only there. Bodies are never scanned for hashtags and never
     * written with them — see the Tag rules in `CLAUDE.md`. A `#` somebody types into a song stays
     * a `#` somebody typed into a song.
     *
     * The frontmatter keeps its own order, new tags going on the end. Sorting it would rewrite every
     * line of a list the user only added to.
     *
     * [newTitle] rides along here rather than in a `withTitle` of its own, because this is the one
     * place where a change becomes a write and where "nothing changed, write nothing" is decided.
     * Two write paths for one save would mean two places to get that rule right. A blank title is
     * ignored, and a title identical to the current one is not a change.
     *
     * Returns `null` when neither the body, the tags nor the title actually changed, so an
     * open-and-close still writes nothing. See [withBody] for why that rule is worth this much care.
     */
    fun withTags(
        newBody: String,
        newTags: List<String>,
        now: Instant,
        newTitle: String? = title,
    ): Note? {
        val wanted = newTags.map(Tags::normalize).distinct()
        val current = tags
        val added = wanted - current.toSet()
        val removed = current - wanted.toSet()

        // An emptied title is not a title. The field can be blank while somebody is retyping one,
        // and a note that saved in that moment would be a blank row in the list with nothing to say
        // which file it is — every note in the archive has a title. Blank means "leave it alone".
        val retitled = newTitle?.trim()?.takeIf { it.isNotBlank() && it != title }

        val kept = keepTrailingNewline(newBody)
        val rewritten = kept != body
        if (!rewritten && added.isEmpty() && removed.isEmpty() && retitled == null) return null

        val ordered = current.filterNot { it in removed } + added
        val refiled = added.isNotEmpty() || removed.isNotEmpty()
        val titled = if (frontmatter.present) {
            val tagged = if (refiled) frontmatter.withTags(ordered) else frontmatter
            if (retitled == null) tagged else tagged.withKey("title", retitled)
        } else {
            // **A note with no frontmatter block at all gets one — but only to hold what the user
            // just asked to store.**
            //
            // `Frontmatter.withTags` and `withKey` both return the block untouched when there is
            // none, which is right for them and was silently wrong here: a tag added in the editor
            // was dropped on the way to disk, and this function returned a note anyway, so the save
            // path wrote the file back unchanged and reported success. The chip was on screen until
            // the editor closed and the note was re-read. Reported 2026-09-09, on a folder in export
            // shape where *every* note is blockless — which is the worst place for it, since that is
            // exactly the folder somebody reaches for the tag sheet in before migrating.
            //
            // The same trap was fixed once already for a block that exists without a `tags:` key;
            // this is the case underneath it.
            if (refiled || retitled != null) Frontmatter.forNote(retitled, ordered) else frontmatter
        }
        // `updated` tracks the writing, not the filing — see the file format contract in `CLAUDE.md`.
        // A note re-filed under a different tag, or given a better title, is not a note somebody
        // rewrote this morning, and the archive's dates are the thing it is kept for.
        //
        // A block the app has just created can take the stamp, because `rewritten` means the body
        // genuinely changed. What it must not do is appear for the stamp's sake: a blockless note
        // whose words were edited and nothing else stays blockless, since inventing a frontmatter to
        // hold a timestamp nobody asked for is the prime directive's second prohibition exactly.
        val stamped = if (rewritten) titled.withKey("updated", stamp(now)) else titled
        // A created block gets the blank line every note in the archive has under its frontmatter —
        // the same rule, and the same reason, as `Migration.plan`. Done after `rewritten` is
        // decided, so the app's own separator is never mistaken for the author's writing.
        val addedBlock = !frontmatter.present && stamped.present
        val spaced = if (addedBlock && kept.isNotEmpty() && !kept.startsWith("\n")) "\n" + kept else kept
        return copy(frontmatter = stamped, body = spaced)
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
