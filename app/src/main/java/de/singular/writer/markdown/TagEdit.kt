// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/**
 * Writing a tag change into a note's body — the other half of a change to its frontmatter.
 *
 * Every note carries its tags twice: once in the `tags:` list and once as `#hashtags` in the text.
 * Neither is a copy of the other. The notes were written in an editor that had no frontmatter at
 * all, where a tag was only an inline hashtag; the YAML block was added by the export on the way
 * out, deriving `tags:` from the hashtags already in the body. So the duplication predates this
 * app, both copies are the user's, and keeping them in agreement is the price of touching either.
 *
 * ## What this file will and will not do
 *
 * It adds the tag that was just added and removes the tag that was just removed, and nothing else.
 * There is deliberately **no reconciliation**: no pass that reads the frontmatter and rewrites the
 * body to match, or the reverse. Measured across the archive the two already agree everywhere, so
 * such a pass would have nothing to fix and everything to break — it would rewrite lines nobody
 * edited, which is the prime directive's first prohibition.
 *
 * Two things are therefore never written:
 *
 * - **A tag that cannot be spelled as a hashtag.** Every tag in the archive can be, since `100%`,
 *   `50%` and `75%` were renamed to `100`, `50` and `75` on 2026-09-07 — see
 *   `tools/rename-percent-tags.py`. The guard stays because a name typed with a space or a `%` in
 *   it would still be writable to the frontmatter and not to a body, and this file must know which
 *   before it writes rather than after.
 * - **Anything outside a tag line.** A hashtag inside a sentence is part of the sentence. The whole
 *   edit happens through [Segments], which has already decided which lines are tags and which are
 *   somebody's song, so there is no second definition here to drift from the first.
 *
 * Pure Kotlin, no Android, and every case below is a unit test — a mistake here corrupts a lyric
 * silently, and the file it corrupts is the only copy.
 */
object TagEdit {

    /**
     * [body] with [add] written into its tag lines and [remove] taken out of them.
     *
     * Returns the identical string when there is nothing to do, so an edit that changed only
     * frontmatter-only tags leaves the body byte-for-byte alone and `Note.withBody`'s
     * unchanged-means-no-write rule still holds.
     */
    fun apply(body: String, add: List<String>, remove: List<String>): String {
        val toAdd = add.filter(Tags::isInlineWritable).distinct()
        val toRemove = remove.filter(Tags::isInlineWritable).toSet()
        if (toAdd.isEmpty() && toRemove.isEmpty()) return body

        // **Adding happens first, and the order is the point.** Swapping a note's only tag is two
        // operations, and removing first empties the note's tag run, drops it, and leaves the
        // addition to start a fresh one at the foot — so a note whose tags sat at the head came back
        // with them at the bottom. Nobody asked for that, and it is a line moved in a file the app
        // was told to touch as little as possible. Adding first writes into the run that is still
        // there, and the removal then takes only what it was asked for.
        val segments = Segments.split(body).toMutableList()
        if (toAdd.isNotEmpty()) addTo(segments, toAdd)
        if (toRemove.isNotEmpty()) removeFrom(segments, toRemove)
        return Segments.join(segments)
    }

    /**
     * Strike [tags] from every tag run in the note.
     *
     * All of them, not the first: 13 notes carry tag lines in more than one place, and a tag left
     * behind in the second run would reappear the moment the file was read again.
     *
     * A run left with no tags at all is dropped whole, and its blank lines go with it — they were
     * absorbed into the segment precisely because they were scaffolding around the tags. The one
     * exception is a run at the very top of the body, which keeps a single newline: every note in
     * the archive has a blank line between its frontmatter and its first line, and removing a tag is
     * not a reason to be the one that does not.
     */
    private fun removeFrom(segments: MutableList<Segment>, tags: Set<String>) {
        for (i in segments.indices) {
            val run = segments[i] as? Segment.Tags ?: continue
            val rebuilt = Segments.physicalLines(run.raw).mapNotNull { line ->
                val content = line.removeSuffix("\n")
                if (!Tags.isTagLine(content)) return@mapNotNull line
                val kept = words(content).filterNot { it.trim().removePrefix("#") in tags }
                if (kept.isEmpty()) return@mapNotNull null
                // The line keeps its own trailing whitespace rather than the removed tag's: striking
                // the last tag off `#chords #radio` must not leave `#chords ` behind, and striking
                // one off `#album/debut #radio #busch ` must not cost the file its final space.
                val trailing = content.substring(content.trimEnd().length)
                indent(content) + kept.joinToString("").trimEnd() + trailing + line.drop(content.length)
            }.joinToString("")

            val survived = Segments.physicalLines(rebuilt).any { Tags.isTagLine(it.removeSuffix("\n")) }
            segments[i] = when {
                survived -> run.copy(raw = rebuilt, tags = run.tags.filterNot { it in tags })
                // Emptied. At the foot the blank lines go too — they were the gap above the tags and
                // there is nothing left to hold a gap open. At the head the ones *above* the tags
                // stay: they are the gap under the frontmatter that every note in the archive has,
                // and one of the three untagged notes is nothing but two of them, so keeping exactly
                // what was there is also what makes the round trip byte-exact.
                i == 0 -> Segment.Prose(leadingBlanks(run.raw))
                else -> Segment.Prose("")
            }
        }
        val kept = segments.filterNot { it is Segment.Prose && it.raw.isEmpty() }
        segments.clear()
        segments += kept.ifEmpty { listOf(Segment.Prose("")) }
    }

    /**
     * Write [tags] onto the note's last tag line, or make one if it has none.
     *
     * The last, because that is where the archive puts them: 110 runs sit at the foot of a note
     * against 66 at the head, and it is also where the chip row the user just tapped is drawn. A tag
     * appearing at the point of the gesture is the least surprising thing that can happen.
     *
     * The line's own spelling is kept. Several tag lines end in a space — `#album/debut ` — and the
     * new tag goes in front of that trailing whitespace rather than after it, so the line keeps the
     * shape it had.
     */
    private fun addTo(segments: MutableList<Segment>, tags: List<String>) {
        val suffix = tags.joinToString("") { " #$it" }
        val at = segments.indexOfLast { it is Segment.Tags }
        if (at < 0) {
            segments += newRun(segments, tags)
            return
        }

        val run = segments[at] as Segment.Tags
        val lines = Segments.physicalLines(run.raw)
        val last = lines.indexOfLast { Tags.isTagLine(it.removeSuffix("\n")) }
        val line = lines[last]
        val content = line.removeSuffix("\n")
        val written = content.trimEnd() + suffix + content.substring(content.trimEnd().length)
        segments[at] = run.copy(
            raw = lines.toMutableList().also { it[last] = written + line.drop(content.length) }.joinToString(""),
            tags = run.tags + tags,
        )
    }

    /**
     * A tag line for a note that has none — the three untagged notes in the archive, and any note
     * written from scratch.
     *
     * It goes at the foot, after a blank line, which is the shape the other 165 notes have.
     */
    private fun newRun(segments: List<Segment>, tags: List<String>): Segment.Tags {
        val body = Segments.join(segments)
        val lead = when {
            body.isEmpty() -> ""
            body.endsWith("\n\n") -> ""
            body.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        return Segment.Tags(raw = lead + tags.joinToString(" ") { "#" + it } + "\n", tags = tags)
    }

    /** The blank lines a run absorbed before its first tag line. */
    private fun leadingBlanks(raw: String): String = Segments.physicalLines(raw)
        .takeWhile { !Tags.isTagLine(it.removeSuffix("\n")) }
        .joinToString("")

    /** A line's words, each carrying the whitespace that follows it, so rejoining is concatenation. */
    private fun words(content: String): List<String> =
        Regex("""\S+\s*""").findAll(content.substring(indent(content).length)).map { it.value }.toList()

    /** The whitespace a line opens with. No tag line in the archive has any, but nothing says none can. */
    private fun indent(content: String): String = content.takeWhile { it == ' ' || it == '\t' }
}
