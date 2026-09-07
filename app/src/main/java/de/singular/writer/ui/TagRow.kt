// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import de.singular.writer.R

/**
 * A note's tags on **one line**, right-aligned, with a `+2` chip standing in for whatever did not
 * fit.
 *
 * One line is the requirement. Wrapping to a second was the first attempt and it made rows of
 * uneven height, which turns an otherwise even list into something that looks broken — and it spent
 * a whole line of a note's row on metadata, when the line above it is the writing and is the reason
 * anyone is looking.
 *
 * The chip count has to be decided by measuring, not by guessing at character widths: tags here run
 * from `50%` to `album/entsetzlich`, and a fixed cutoff would either truncate two short tags
 * needlessly or overflow on one long one.
 *
 * There is a circularity in that — how many chips fit depends on how wide the `+N` chip is, and `N`
 * depends on how many chips fit — which is what [SubcomposeLayout] is for. We measure the tags,
 * count how many fit outright, and if they do not all fit we walk `k` downward, re-composing the
 * `+N` chip for each candidate `N` until the row fits. Tags cap at five in the archive, so that loop
 * runs at most a handful of times and only for rows that actually overflow.
 */
@Composable
fun TagRow(tags: List<String>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    val gap = 6.dp

    SubcomposeLayout(modifier) { constraints ->
        val max = constraints.maxWidth
        val gapPx = gap.roundToPx()

        val chips = subcompose(Slot.Tags) {
            tags.forEach { TagChip(it) }
        }.map { it.measure(Constraints()) }

        fun widthOf(count: Int, extra: Int = 0): Int {
            if (count == 0 && extra == 0) return 0
            val chipsWidth = chips.take(count).sumOf { it.width }
            val gaps = (count + if (extra > 0) 1 else 0 - 1).coerceAtLeast(0) * gapPx
            return chipsWidth + extra + gaps
        }

        // The happy path: everything fits, and no overflow chip is composed at all.
        var shown = chips.size
        var overflow: androidx.compose.ui.layout.Placeable? = null
        if (widthOf(chips.size) > max) {
            shown = chips.size - 1
            while (shown >= 0) {
                val hidden = tags.size - shown
                val measured = subcompose(Slot.Overflow(hidden)) {
                    OverflowChip(hidden)
                }.first().measure(Constraints())
                if (widthOf(shown, measured.width) <= max || shown == 0) {
                    overflow = measured
                    break
                }
                shown--
            }
        }

        val used = widthOf(shown, overflow?.width ?: 0)
        val height = (chips.take(shown).maxOfOrNull { it.height } ?: overflow?.height ?: 0)

        layout(max, height) {
            // Right-aligned: the group ends on the row's right edge, and reads left to right within
            // itself, so the +N chip lands last — after the final tag that fitted.
            var x = max - used
            chips.take(shown).forEach {
                it.place(x, (height - it.height) / 2)
                x += it.width + gapPx
            }
            overflow?.place(x, (height - overflow.height) / 2)
        }
    }
}

/** Subcompose slot keys. The overflow chip's key carries its count so each candidate is distinct. */
private sealed interface Slot {
    data object Tags : Slot
    data class Overflow(val hidden: Int) : Slot
}

/**
 * One tag, written the way the drawer writes it — `lyrics/snippet`, no `#`.
 *
 * Quiet on purpose: a faint container, small type, the secondary colour. The footer's job is to be
 * available when looked at and invisible when not, and a row of bright chips under every note would
 * make the list about its metadata rather than about the writing.
 */
@Composable
fun TagChip(tag: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** The `+2` that stands in for the tags this row had no room for. */
@Composable
private fun OverflowChip(hidden: Int) {
    val description = pluralStringResource(R.plurals.tag_overflow_description, hidden, hidden)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = stringResource(R.string.tag_overflow, hidden),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
