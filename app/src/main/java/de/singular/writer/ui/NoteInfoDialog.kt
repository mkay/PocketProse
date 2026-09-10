// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
 * The name is shown and not offered for changing. Renaming is not forbidden — `CLAUDE.md` bans the
 * app renaming a file *to match a title*, which is a different act from a person renaming their own
 * note — but it is a write to the folder, and this dialog is the one place in the app that promises
 * to only look.
 */
@Composable
fun NoteInfoDialog(
    name: String,
    body: String,
    created: Instant?,
    updated: Instant?,
    onDismiss: () -> Unit,
) {
    val stats = Stats.of(body)
    val stamp = rememberDateTimeFormatter()
    val unknown = stringResource(R.string.note_info_unknown)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_info_title)) },
        text = {
            // A long name and a long localised timestamp both wrap, and a note made in a folder that
            // has been synced around can carry a name longer than this dialog is wide.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InfoRow(stringResource(R.string.note_info_words), stats.words.toString())
                InfoRow(stringResource(R.string.note_info_lines), stats.lines.toString())
                InfoRow(stringResource(R.string.note_info_characters), stats.characters.toString())

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                InfoRow(stringResource(R.string.note_info_name), name.ifEmpty { unknown })
                InfoRow(
                    stringResource(R.string.note_info_created),
                    created?.let(stamp) ?: unknown,
                )
                InfoRow(
                    stringResource(R.string.note_info_updated),
                    updated?.let(stamp) ?: unknown,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/**
 * One fact: its name on the left, its value on the right.
 *
 * The value carries the weight — it is what the row is for — and the label sits in the quieter ink
 * the rest of the app uses for things that explain rather than say. The label is held to a third of
 * the width so six rows line up in a column instead of each starting wherever its own label ended.
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
    }
}
