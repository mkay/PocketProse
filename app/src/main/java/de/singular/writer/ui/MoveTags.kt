// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import de.singular.writer.markdown.Migration

/**
 * The offer to move a folder's inline tags into its notes — the one-time migration's whole surface.
 *
 * ## Why it is a banner and not a first-run dialog
 *
 * The obvious design is to ask while the folder is being picked, and it is the wrong one. Rewriting
 * every file in somebody's archive *before they have seen a single note* is the worst moment
 * available: the folder may be mis-picked (a parent directory, the wrong sync root), the sync may be
 * mid-flight, and the app has done nothing yet to deserve being trusted with 165 files. It would
 * also make the app's one real promise — point it at a folder and it writes nothing — untrue for
 * every new user by definition.
 *
 * So the order is: adopt the folder, list the notes, *then* offer. By the time this appears the user
 * has their own writing on screen and can see what the app is talking about.
 *
 * ## Why it says "inside your notes"
 *
 * Nowhere here says "frontmatter", "YAML" or "metadata". The people this is for write songs. What
 * they can see is that a `#lyrics/snippet` is sitting in the middle of their words, and what they
 * are being offered is to have it moved out of them. That is the whole sentence.
 *
 * ## What the dialog has to say out loud
 *
 * - **How many notes.** "165 of your 168" is a different decision from "2 of your 168".
 * - **That it changes those files.** Said plainly, once. It is the only writing this app does that
 *   the user did not type.
 * - **What the tag list gains**, when it gains anything. On an archive whose tags already agree it
 *   gains nothing, and claiming otherwise would be a lie the drawer would then contradict.
 *
 * What it deliberately does *not* promise is an undo, because there is none: the honest inverse is
 * the planned export, and offering a button that does not exist would be worse than the silence.
 */
@Composable
fun MoveTagsBanner(
    survey: Migration.Survey,
    onOffer: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // A step *down* the ramp — `surfaceContainerLow` is below the page in both schemes —
            // so this is a recess cut into the list rather than a card resting on it. Raising it a
            // step was tried and rejected on 2026-09-09: at this palette's contrast a lifted band
            // reads as a slab, the same finding that left the top bar with no ground of its own.
            // The recess is quieter, and quiet is right. It is an offer, not an alarm, and a folder
            // that never takes it must not feel nagged.
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.move_tags_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.settings_move_tags_subtitle,
                    survey.notes,
                    survey.notes,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onOffer) {
            Text(stringResource(R.string.move_tags_banner_action))
        }
        // Dismissing is permanent and the settings row is how it comes back — see MainActivity.
        // A banner that returned on every launch would be the app nagging about the user's own
        // filing, which is theirs and not its business.
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.move_tags_banner_dismiss),
            )
        }
    }
}

/** The confirm. See [MoveTagsBanner] for what it has to say and what it must not promise. */
@Composable
fun MoveTagsDialog(
    survey: Migration.Survey,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_tags_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    pluralStringResource(
                        R.plurals.move_tags_scope,
                        survey.notes,
                        survey.notes,
                        survey.scanned,
                    ),
                )
                Text(stringResource(R.string.move_tags_body))
                // Only when there is something to gain. On this author's own archive the tags
                // already agree with the bodies, so the move is a pure tidy and this line would be
                // a promise the drawer immediately contradicts.
                if (survey.gained.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.move_tags_gained,
                            survey.gained.size,
                            survey.gained.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.move_tags_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.move_tags_cancel)) }
        },
    )
}
