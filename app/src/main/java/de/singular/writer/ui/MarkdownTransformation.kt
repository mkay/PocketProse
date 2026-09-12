// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import de.singular.writer.markdown.Live
import de.singular.writer.markdown.Mark

/**
 * Shows the writing and hides the Markdown.
 *
 * `**das**` appears as **das** with no asterisks — until the cursor moves inside it, at which point
 * the asterisks fade in, dimmed, so they can be edited. That is the whole promise of this app's
 * editor to someone who has never heard of Markdown, and to someone who has.
 *
 * ## Why this is safe
 *
 * Hiding characters makes the displayed text shorter than the stored text, so every cursor position,
 * tap and swipe afterwards depends on a mapping between the two. Hand-writing that mapping is the
 * classic way to put an `IndexOutOfBoundsException` in front of a user, and it was the risk this
 * phase was spiked to find out about.
 *
 * It turns out not to need writing. [OutputTransformation] hands us a [TextFieldBuffer] and takes
 * responsibility for the mapping itself: we delete ranges, and Compose keeps the cursor where it
 * belongs. What remains ours is *which* ranges — decided in [Live], which is pure Kotlin and tested
 * against all 168 notes at every marker position in them.
 *
 * The second unknown was the cursor. An [OutputTransformation] is not passed the selection, so the
 * fade-in looked like it would need the transformation rebuilt on every cursor move — which risks a
 * render feedback loop. It does not: [TextFieldBuffer.originalSelection] is readable right here, in
 * the original coordinate space, which is exactly what [Live] wants. One instance, no rebuilding,
 * no loop.
 *
 * ## Order of operations
 *
 * Deletions run **back to front**, so that each range's original offsets are still valid when it is
 * reached — deleting forwards would shift every later range out from under itself. The styles are
 * then applied in transformed coordinates, which [Live] has already computed.
 *
 * A data class so that two equal transformations compare equal and Compose can skip work; the colours
 * are the only state.
 */
data class MarkdownTransformation(
    private val marker: Color,
    private val code: Color,
) : OutputTransformation {

    override fun TextFieldBuffer.transformOutput() {
        val text = originalText.toString()
        if (text.isEmpty()) return

        val selection = originalSelection
        val live = Live.of(text, selection.min..selection.max)

        for (h in live.hide.sortedByDescending { it.start }) {
            replace(h.start, h.end, "")
        }
        for (s in live.styles) {
            if (s.start >= s.end || s.end > length) continue
            // An indented line is a paragraph of its own — see the note on `Live.of` for why its
            // newline is hidden to make that work — and the indent is that paragraph's.
            if (s.mark == Mark.QUOTE && !s.isMarker) {
                addStyle(QUOTE_INDENT, s.start, s.end)
                continue
            }
            addStyle(style(s.mark, s.level, s.isMarker), s.start, s.end)
        }
    }

    /**
     * How each kind of markup is painted.
     *
     * A revealed marker is only dimmed, never given the style it introduces: the point of showing
     * `**` is to say "these characters are here and you may delete them", and a bold asterisk reads
     * as part of the word instead.
     *
     * Heading sizes are relative (`em`) rather than absolute, so they follow whatever the reader has
     * set the body size to instead of overriding it.
     */
    private fun style(mark: Mark, level: Int, isMarker: Boolean): SpanStyle {
        if (isMarker) return SpanStyle(color = marker, fontWeight = FontWeight.Normal)
        return when (mark) {
            Mark.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            Mark.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            Mark.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            Mark.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, color = code)
            Mark.HEADING -> SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = headingSize(level))
            // A rule has no content to paint: hidden, it is an empty line with a divider drawn
            // through it (see `RuleLines`); revealed, it is all marker and took the branch above.
            Mark.RULE -> SpanStyle()
            // Handled above as a paragraph style; a revealed `> ` is a marker and took that branch.
            Mark.QUOTE -> SpanStyle()
            // A scratch line wears the marker colour whole, `+` included: on the page, and visibly
            // not the song. Painted first, being the line's outermost span, so emphasis inside it
            // keeps its weight and takes the dimming.
            Mark.SCRATCH -> SpanStyle(color = marker)
        }
    }

    private companion object {
        /**
         * How far an indented block is pushed right. In `em` so it follows the prose size, and the
         * same on the first line as on wrapped ones — a block, not a hanging indent.
         */
        val QUOTE_INDENT = ParagraphStyle(textIndent = TextIndent(firstLine = 1.5.em, restLine = 1.5.em))
    }

    /**
     * Heading scale. The archive uses only `##`, and no note contains a single `#` at all, so the
     * sizes past level 2 exist for completeness rather than for the corpus — they step down gently
     * because a note is not a document and a fourth-level heading in a song is already unusual.
     */
    private fun headingSize(level: Int): TextUnit = when (level) {
        1 -> 1.5.em
        2 -> 1.3.em
        3 -> 1.15.em
        else -> 1.05.em
    }
}
