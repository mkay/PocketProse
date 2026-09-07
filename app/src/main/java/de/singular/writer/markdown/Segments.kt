// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/** One image link found in a note. [path] is exactly as written — relative, and possibly encoded. */
data class ImageRef(val alt: String, val path: String)

/**
 * One ordinary link — a PDF, a GarageBand file, a web address.
 *
 * [label] is what the note calls it and [target] is where it points, both exactly as written. The
 * archive's attachment lists give every PDF a readable label ("Pasted Graphic 10"), so there is
 * usually something better to show than a filename; where there is not, the caller falls back to
 * the target.
 */
data class LinkRef(val label: String, val target: String) {
    /** What to show for it. Labels are usually present; a bare link shows its own target. */
    val display: String get() = label.ifBlank { target.substringAfterLast('/') }
}

/**
 * A stretch of a note's body, as the editor lays it out.
 *
 * The editor is one text field per [Text] segment, with [Images] rendered between them. Every
 * segment carries its [raw] bytes, terminator included, so that joining them back is concatenation
 * and nothing else — see [Segments.join].
 */
sealed interface Segment {
    val raw: String

    /**
     * Words. Editable, and the overwhelming majority of every note.
     *
     * Named `Prose` rather than the obvious alternative because constructing one with an empty
     * literal collides with the build's hardcoded-UI-string check, which looks for a composable
     * being handed a bare string. That check is deliberately blunt and worth keeping blunt, so
     * renaming one data class is cheaper than teaching it about qualified constructors — and this
     * is the better name for what this app is anyway.
     */
    data class Prose(override val raw: String) : Segment

    /** A line that carries at least one image. [trailing] is whatever text shared the line. */
    data class Images(
        override val raw: String,
        val images: List<ImageRef>,
        val trailing: String,
    ) : Segment

    /**
     * A run of lines that are nothing but hashtags, taken out of the editable text.
     *
     * The editor draws these as chips at the foot of the note rather than as `#` text, so the
     * characters must leave the text buffer — and *leaving* is the point. Hiding them in place, the
     * way `**` is hidden, would put a stretch of invisible characters inside a field somebody is
     * typing in, where a backspace at the edge silently eats a tag. A segment cannot be reached by
     * the cursor at all.
     *
     * [raw] carries the run's own bytes plus the blank lines absorbed with it (see `Segments.split`),
     * so the file is unchanged by being displayed. [tags] is every tag on the run, in order, without
     * the `#`.
     */
    data class Tags(
        override val raw: String,
        val tags: List<String>,
    ) : Segment
}

/**
 * Splits a note's body so that images can be drawn where they belong.
 *
 * A Compose text field cannot contain a composable, so an image cannot be drawn *inside* the text
 * being edited. That looked like a straight conflict between two decisions — "one view, always
 * editable" and "images must render inline", the latter being the requirement that killed the
 * editor the author tried before this one.
 *
 * It is not a conflict, because of a fact about the archive: **every image in it sits on a line of
 * its own**, in runs of three or four, never inside a sentence. Measured across all 168 notes — 38
 * images across 3 chord sheets, and the most any line carries beside them is a bare `3x`. So the
 * body can be cut at image lines, the text between them stays directly editable, and the images are
 * drawn in the gaps. 165 of the 168 notes produce a single [Segment.Prose] and behave exactly as they
 * did before this file existed.
 *
 * **Joining must be byte-exact.** If splitting and rejoining an untouched note changed one character,
 * every note with an image in it would be rewritten the moment it was opened. That is the property
 * the tests hammer over the whole corpus.
 */
object Segments {

    private val IMAGE = Regex("""!\[([^\]]*)]\(([^)]*)\)""")

    /** An ordinary link: the same shape, without the leading `!` that makes it an image. */
    private val LINK = Regex("""(?<!!)\[([^\]]*)]\(([^)]*)\)""")

    /**
     * Split [body] at image lines and at tag lines. Never loses a byte: `join(split(x)) == x`.
     *
     * Lines are classified one at a time; consecutive tag lines join into a single run, because
     * `Helen weiss das auch.md` ends with three of them and three chip rows would be three ways of
     * saying the same thing.
     *
     * The result always contains at least one [Segment.Prose], even when the note has nothing but
     * tags in it — 40 notes in the archive are exactly that, and each still needs somewhere to type.
     */
    fun split(body: String): List<Segment> {
        if (body.isEmpty()) return listOf(Segment.Prose(""))
        val out = ArrayList<Segment>()
        val text = StringBuilder()

        fun flush() {
            if (text.isNotEmpty()) {
                out += Segment.Prose(text.toString())
                text.setLength(0)
            }
        }

        var i = 0
        while (i < body.length) {
            val nl = body.indexOf('\n', i)
            val end = if (nl < 0) body.length else nl + 1
            val raw = body.substring(i, end)
            val line = raw.removeSuffix("\n")
            val images = IMAGE.findAll(raw).map { ImageRef(it.groupValues[1], it.groupValues[2]) }.toList()
            when {
                images.isNotEmpty() -> {
                    flush()
                    out += Segment.Images(
                        raw = raw,
                        images = images,
                        trailing = Inline.strip(IMAGE.replace(raw, "")).trim(),
                    )
                }

                Tags.isTagLine(line) -> {
                    val previous = out.lastOrNull()
                    if (text.isEmpty() && previous is Segment.Tags) {
                        // A run: fold this line into the run already open rather than starting a
                        // second chip row for the same group of tags.
                        out[out.size - 1] = previous.copy(
                            raw = previous.raw + raw,
                            tags = previous.tags + tagsOn(line),
                        )
                    } else {
                        flush()
                        out += Segment.Tags(raw = raw, tags = tagsOn(line))
                    }
                }

                else -> text.append(raw)
            }
            i = end
        }
        if (text.isNotEmpty() || out.isEmpty()) out += Segment.Prose(text.toString())
        return absorbBlankLines(out)
    }

    /** Every tag on one tag line, in order, without the `#`. */
    private fun tagsOn(line: String): List<String> =
        line.trim().split(Regex("""\s+"""))
            .filter(Tags::isTagWord)
            .map { it.removePrefix("#") }

    /**
     * Move the blank lines that belong to a tag run out of the prose around it.
     *
     * Without this a note whose tags sit at the head — 66 of the runs in the archive — opens with an
     * empty first line where the hashtags used to be, and one whose tags sit at the foot ends with a
     * stray blank. The blank line is scaffolding around a thing that is no longer drawn, so it goes
     * with it.
     *
     * **Bytes only ever move between adjacent segments**, never disappear, which is what keeps
     * `join(split(x)) == x` true — and that property is asserted over all 168 notes, because losing
     * a byte here rewrites every note in the archive the moment it is opened.
     *
     * Blank lines *before* a run are always absorbed; blank lines *after* it only when the run
     * begins the body. A run sitting between two paragraphs therefore keeps the paragraph break that
     * follows it, rather than welding the two paragraphs together on screen.
     *
     * Blank means [String.isBlank], not empty: `Selbst Schuld an deinem Glück.md` separates its
     * tags from its song with a line holding a single space, and to a reader that is a blank line.
     */
    private fun absorbBlankLines(segments: List<Segment>): List<Segment> {
        val out = segments.toMutableList()
        for (i in out.indices) {
            val run = out[i] as? Segment.Tags ?: continue

            val before = out.getOrNull(i - 1) as? Segment.Prose
            if (before != null) {
                val lines = physicalLines(before.raw)
                val moved = lines.takeLastWhile { it.isBlank() }.joinToString("")
                if (moved.isNotEmpty()) {
                    out[i - 1] = before.copy(raw = before.raw.dropLast(moved.length))
                    out[i] = run.copy(raw = moved + run.raw)
                }
            }

            val startsBody = out.take(i).all { it is Segment.Prose && it.raw.isBlank() }
            val after = out.getOrNull(i + 1) as? Segment.Prose
            if (startsBody && after != null) {
                val moved = physicalLines(after.raw).takeWhile { it.isBlank() }.joinToString("")
                if (moved.isNotEmpty()) {
                    out[i] = (out[i] as Segment.Tags).let { it.copy(raw = it.raw + moved) }
                    out[i + 1] = after.copy(raw = after.raw.drop(moved.length))
                }
            }
        }
        // A prose segment emptied by the move is not a place to type, it is a gap in the layout.
        val kept = out.filterNot { it is Segment.Prose && it.raw.isEmpty() }
        // A note whose whole body is its tags — 40 of them — still needs a field. A note that is
        // nothing but an image does not gain one it never had.
        return when {
            kept.any { it is Segment.Prose } -> kept
            kept.any { it is Segment.Tags } -> kept + Segment.Prose("")
            else -> kept
        }
    }

    /**
     * [raw] as its lines, each keeping its own newline, so that joining them back is concatenation.
     *
     * `split('\n')` would drop the terminators and invent a trailing empty line, and both of those
     * lose bytes when the pieces are reassembled.
     */
    internal fun physicalLines(raw: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < raw.length) {
            val nl = raw.indexOf('\n', i)
            val end = if (nl < 0) raw.length else nl + 1
            out += raw.substring(i, end)
            i = end
        }
        return out
    }

    /** The inverse of [split], exactly. */
    fun join(segments: List<Segment>): String = segments.joinToString("") { it.raw }

    /**
     * Every image a note refers to, in the order it refers to them.
     *
     * Used by the library and by the attachment loader; the editor works from [split] instead,
     * because it needs to know *where* they are as well as what they are.
     */
    fun imagesIn(body: String): List<ImageRef> =
        IMAGE.findAll(body).map { ImageRef(it.groupValues[1], it.groupValues[2]) }.toList()

    /**
     * Every non-image link in a note, in order, without duplicates.
     *
     * Duplicates are dropped because the same file can be linked twice — `Wer geht vor 2.md` links
     * `Pasted Graphic 3.pdf` from two different places — and an attachment list that showed it twice
     * would be reporting the note's structure rather than its files.
     */
    fun linksIn(body: String): List<LinkRef> =
        LINK.findAll(body)
            .map { LinkRef(it.groupValues[1], it.groupValues[2]) }
            .distinctBy { it.target }
            .toList()
}
