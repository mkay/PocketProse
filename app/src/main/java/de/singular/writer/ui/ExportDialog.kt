// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import de.singular.writer.R

/**
 * The export: a zip of the folder, with one switch.
 *
 * **Export, not backup.** "Backup" promises a restore and the app has none; restoring is unzipping
 * into the folder, which the user does, and the sentence under the heading says so rather than
 * implying the app will do it. A verbatim export is a backup in practice without the word's promise.
 *
 * **The switch is off by default, and its label says what it does to the copy.** A backup is bytes.
 * With the switch on the bodies are rewritten — each note's tags and title written back into its
 * text, for an editor that reads them there — and that copy is the archive prepared for another
 * editor, not a copy of the archive. Both are useful; the default has to be the verbatim one. See
 * `Export` for what the switch writes and what it leaves alone.
 *
 * The count is the folder's, not the list's: this exports the folder whatever filter the library is
 * showing, and the number says so before the file picker opens.
 */
@Composable
fun ExportDialog(
    notes: Int,
    onExport: (inline: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var inline by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogHeading(
                ImageVector.vectorResource(R.drawable.ic_archive),
                stringResource(R.string.export_title),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = pluralStringResource(R.plurals.export_body, notes, notes))
                // The whole row toggles, not only the switch: the label is the larger target and
                // the one somebody reads before deciding.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { inline = !inline }
                        .padding(top = 4.dp),
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.export_inline),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.export_inline_caption),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = inline, onCheckedChange = { inline = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(inline) }) {
                Text(text = stringResource(R.string.export_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )
}
