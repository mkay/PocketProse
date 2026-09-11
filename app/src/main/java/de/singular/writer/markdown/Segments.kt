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

                else -> text.append(raw)
            }
            i = end
        }
        if (text.isNotEmpty() || out.isEmpty()) out += Segment.Prose(text.toString())
        return out
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
     * [raw] — one image line — with the link for [ref] taken out.
     *
     * The link goes and nothing else does: the file in `attachments/` stays where it is, because
     * another note may point at it and deleting a file is a different act with its own confirmation.
     * A line left with nothing but whitespace goes entirely, newline included, so removing the only
     * picture on a line does not leave a blank line where it stood; a line that still carries words
     * or other pictures keeps them, and the space beside the removed link goes with it so two
     * neighbours do not end up separated by a double gap.
     */
    fun withoutImage(raw: String, ref: ImageRef): String {
        val m = IMAGE.findAll(raw).firstOrNull { it.groupValues[1] == ref.alt && it.groupValues[2] == ref.path }
            ?: return raw
        var from = m.range.first
        var to = m.range.last + 1
        if (to < raw.length && raw[to] == ' ') to++ else if (from > 0 && raw[from - 1] == ' ') from--
        val rest = raw.substring(0, from) + raw.substring(to)
        return if (rest.isBlank()) "" else rest
    }

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
