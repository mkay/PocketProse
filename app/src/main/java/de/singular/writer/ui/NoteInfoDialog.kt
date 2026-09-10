// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import de.singular.writer.markdown.Stats
import java.time.Instant

/**
 * Everything the app knows about the note being written in, on one sheet.
 *
 * **It exists so the editor can keep having none of this.** That screen's whole argument is what is
 * absent — no toolbar, no mode switch, no word count sitting in the corner counting at you while you
 * write — and a running total is the classic way a writing app stops being one. A number you have to
 * ask for is a different thing from a number that watches: it answers "how long is this now" at the
 * moment that question is actually being asked, and is invisible the rest of the time. That is why
 * this is a dialog behind an icon and not a line in the bar.
 *
 * Two kinds of fact, divided because they answer to different masters:
 *
 * - **The writing** — words, characters, lines — is the text as it stands in the editor this second,
 *   unsaved keystrokes included. Anything else would be answering a question about the file when the
 *   question was about the note.
 * - **The record** — the name in the folder, `created`, `updated` — is what is on disk. `created` is
 *   the archive's main value, spanning 2015 to 2025, and it is read from the frontmatter rather than
 *   from any timestamp the phone could offer: the notes were exported, copied about, and are rewritten
 *   by a sync client whenever it feels like it. Only the note remembers.
 *
 * The name is the one row that does something. Renaming is not forbidden — `CLAUDE.md` bans the app
 * renaming a file *to match a title*, which is the opposite act from a person renaming their own
 * note — and this is where a name gets looked at, so it is where changing it belongs. It opens
 * [RenameNoteDialog] rather than editing in place, and this dialog closes behind it: two stacked
 * sheets to change one word is a lot of furniture, and the reading is done by then anyway.
 */
@Composable
fun NoteInfoDialog(
    name: String,
    body: String,
    created: Instant?,
    updated: Instant?,
    /** Opens the rename dialog. Null while the note cannot be written — see `editable`. */
    onRename: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val stats = Stats.of(body)
    val stamp = rememberDateTimeFormatter()
    val unknown = stringResource(R.string.note_info_unknown)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogHeading(Icons.Outlined.Info, stringResource(R.string.note_info_title))
        },
        text = {
            // A long name and a long localised timestamp both wrap, and a note made in a folder that
            // has been synced around can carry a name longer than this dialog is wide.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InfoTable(
                    rows = listOf(
                        InfoEntry(stringResource(R.string.note_info_words), stats.words.toString()),
                        InfoEntry(stringResource(R.string.note_info_lines), stats.lines.toString()),
                        InfoEntry(
                            stringResource(R.string.note_info_paragraphs),
                            stats.paragraphs.toString(),
                        ),
                        InfoEntry(
                            stringResource(R.string.note_info_characters),
                            stats.characters.toString(),
                        ),
                        InfoEntry(
                            label = stringResource(R.string.note_info_name),
                            value = name.ifEmpty { unknown },
                            onClick = onRename?.takeIf { name.isNotEmpty() },
                            actionLabel = stringResource(R.string.rename_note_title),
                        ),
                        InfoEntry(
                            stringResource(R.string.note_info_created),
                            created?.let(stamp) ?: unknown,
                        ),
                        InfoEntry(
                            stringResource(R.string.note_info_updated),
                            updated?.let(stamp) ?: unknown,
                        ),
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/** One line of the table: a label, its value, and whether the row does anything. */
private data class InfoEntry(
    val label: String,
    val value: String,
    val onClick: (() -> Unit)? = null,
    val actionLabel: String? = null,
)

/**
 * The facts, in a panel of their own.
 *
 * **A second surface inside a dialog, which this app does not otherwise do.** The palette's argument
 * is against slabs — it is why the top bar refuses a tint and why the tag-move banner is recessed
 * rather than lifted — and a panel is a slab by any reading. It earns the exception by being a
 * table: six label-and-value pairs are a different kind of content from the sentences every other
 * dialog holds, and giving them an edge is what says "read this in columns" rather than "read this
 * as prose".
 *
 * The ground is [surfaceContainerHighest], the far end of the app's own ramp, so it steps away from
 * the dialog's [surfaceContainerHigh] in whichever direction the scheme runs — lighter in the dark
 * theme, darker in the light one. No invented colour, and no role that means the opposite of itself
 * between schemes, which is what picking `surfaceContainerLow` here would have done.
 *
 * The stripe is [onSurface] at four percent, which is the one way to say "a shade further" once the
 * ramp has run out. Ink over the panel darkens it on a light scheme and lightens it on a dark one,
 * so a single alpha is right in both rather than two constants that have to be kept in step.
 *
 * **No rule between the writing and the record.** There was one, and it read as a stripe rather than
 * as a border: a divider spans the panel while the rows are inset from it, so the line started and
 * ended where nothing else on the row did. Widening the rows to meet it or insetting it to meet them
 * are both ways of making a table out of a line that the stripes already do without it. The two
 * groups are legible from what they say.
 */
@Composable
private fun InfoTable(rows: List<InfoEntry>) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(vertical = 4.dp),
    ) {
        rows.forEachIndexed { index, row ->
            InfoRow(
                label = row.label,
                value = row.value,
                onClick = row.onClick,
                actionLabel = row.actionLabel,
                tinted = index % 2 == 0,
            )
        }
    }
}

/**
 * One fact: its name on the left, its value on the right.
 *
 * The value carries the weight — it is what the row is for — and the label sits in the quieter ink
 * the rest of the app uses for things that explain rather than say. The label is held to a third of
 * the width so six rows line up in a column instead of each starting wherever its own label ended.
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    actionLabel: String? = null,
    tinted: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Before the click, so the ripple is drawn over the stripe rather than under it.
            .background(if (tinted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f) else Color.Transparent)
            // The whole row, not the pencil: a 24dp glyph is a poor target beside five rows that are
            // not targets at all, and the row is what the user is looking at when they decide.
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(onClick = onClick)
                        .semantics { if (actionLabel != null) contentDescription = actionLabel }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        if (onClick != null) {
            // The only mark in the dialog that says a row does something. Quiet ink, because it is a
            // hint about the row rather than a button competing with the value beside it.
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
