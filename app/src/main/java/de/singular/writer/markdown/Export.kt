// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/**
 * A note as the export writes it: the bytes it has, or the bytes it has with its filing written back
 * into the text for an editor that reads it there.
 *
 * This is [Migration]'s inverse, and the reason the migration's dialog may promise an export in place
 * of an undo. The migration moved a `#lyrics/snippet` line into `tags:` and an `# Adlerohr` heading
 * into `title:`; this writes them back, as a `# Heading` opening the note and a tag line under it,
 * in the shape the original export wrote at the head of 65 notes:
 *
 * ```
 * ---
 * (the frontmatter, untouched)
 * ---
 *
 * # Atlantik
 *
 * #lyrics/snippet
 *
 * Du erreichst mich nicht
 * ```
 *
 * ## The frontmatter is untouched
 *
 * Adding is all this does. No key is rewritten, nothing is removed, and `updated` says what the file
 * says — this is filing, not writing. The other editor's own export had no frontmatter at all, and
 * reproducing that would drop ten years of `created` for a shape nothing needs; what comes out is
 * the state the app held until 2026-09-09, both representations present.
 *
 * ## Nothing is doubled
 *
 * 165 notes in the archive still carry their old tag line, and an export that wrote one above each
 * of them would say every tag twice. So this asks [Migration.carried] what the body already holds
 * and writes only the difference: a note whose body carries all its tags gets no tag line, and a
 * note that opens with *any* heading gets no heading, whatever that heading says — a second one
 * above it would be wrong either way. A tag line that has to be added to a note that already opens
 * with a heading goes under the heading, not above it.
 *
 * The hashtag grammar stays with its one owner. This never runs the regex; it asks.
 *
 * ## Nothing is derived
 *
 * A note with no `title:` gets no heading. The file name is not promoted into one, because nothing
 * in the app derives a title from a name and the export must not start.
 *
 * ## The blank lines
 *
 * The body's opening blank line, where it has one, stays where it is and the filing goes under it,
 * so the copy has the archive's own gap under the frontmatter. Between the heading, the tag line and
 * the words there is one blank line each, and the last line of filing ends in a newline like every
 * other line. A note whose words are nothing — 40 of them are a title and a tag — ends on its tag
 * line, which is how the original export wrote `Die Eule.md`.
 *
 * Pure Kotlin, no Android, tested against the corpus in both directions.
 */
object Export {

    /** [note]'s bytes, with its tags and title written into the body when [inline] asks for it. */
    fun render(note: Note, inline: Boolean): String {
        if (!inline) return note.render()
        val body = inlined(note) ?: return note.render()
        return note.frontmatter.raw + body
    }

    /** The body with the filing written in, or null when there is nothing to write. */
    fun inlined(note: Note): String? {
        val body = note.body
        val carried = Migration.carried(body)
        val title = note.title?.trim()?.takeIf { it.isNotEmpty() }
        val heading = title?.takeIf { carried.heading == null }
        val tags = note.tags.map(Tags::normalize).filterNot { it in carried.tags }
        if (heading == null && tags.isEmpty()) return null

        val lines = Migration.physicalLines(body)
        // Where the filing goes: under the opening blank line, or — when the note already opens
        // with a heading and only tags are being added — under that heading and the blank line it
        // sits on.
        var at = if (body.startsWith("\n")) 1 else 0
        if (heading == null && carried.heading != null) {
            at = lines.take(carried.at + 1).sumOf { it.length }
            if (carried.at + 1 in lines.indices && lines[carried.at + 1].isBlank()) {
                at += lines[carried.at + 1].length
            }
        }

        val pieces = buildList {
            heading?.let { add("# $it") }
            if (tags.isNotEmpty()) add(tags.joinToString(" ") { "#$it" })
        }
        val block = pieces.joinToString("\n\n")
        val rest = body.substring(at)
        return body.substring(0, at) + block + "\n" + if (rest.isBlank()) "" else "\n$rest"
    }
}
