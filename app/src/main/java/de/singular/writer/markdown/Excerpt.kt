// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/**
 * The first line or two of a note, for the library list.
 *
 * What it leaves out is the point. Tag lines are gone, because tags are chips in that row already
 * and repeating them as text would waste the only line a note gets. Rules are gone. Images are gone.
 * Headings are kept, because `## Strophe` at the top of a note is genuinely the first thing in it.
 *
 * **A blank result is a normal answer, not a failure.** 36 of the author's 168 notes have a body
 * that is nothing but their tag line — `Die Eule.md` is 15 bytes of body, all of it `#lyrics/titel`.
 * Those are title ideas, filed under a tag, and they are a legitimate kind of note. The caller shows
 * a quiet placeholder for them (`R.string.note_empty`); it must not treat empty as an error, and
 * nothing here should invent text to avoid it.
 */
object Excerpt {

    /** How much of a note the library row can show before it runs out of width. */
    const val DEFAULT_LENGTH = 140

    /**
     * Up to [length] characters of what the note actually says.
     *
     * Blocks are joined with a single space and internal line breaks are flattened: the row is two
     * lines of a list, not a rendering, and a verse's line endings mean nothing at this size. The
     * cut falls on a word boundary where one is near enough, and an ellipsis marks it.
     */
    fun of(note: Note, length: Int = DEFAULT_LENGTH): String {
        val text = note.blocks
            .mapNotNull { block ->
                when (block) {
                    is Block.Paragraph -> Inline.strip(block.text)
                    is Block.Heading -> Inline.strip(block.text)
                    is Block.Bullets -> block.items.joinToString(" ") { Inline.strip(it) }
                    is Block.Quote -> block.lines.joinToString(" ") { Inline.strip(it) }
                    Block.Rule -> null
                }
            }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (text.length <= length) return text
        val cut = text.take(length)
        val space = cut.lastIndexOf(' ')
        // Only break on a word if that does not throw away a quarter of the line.
        return (if (space > length * 3 / 4) cut.take(space) else cut).trimEnd() + "…"
    }
}
