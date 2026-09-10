// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material3.AlertDialog
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
 * The offer to move a folder's tags and titles into its notes — the migration's whole surface.
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
 * they can see is that a `#lyrics/snippet` is sitting in the middle of their words, and that the
 * first line of every note repeats the name at the top of the list. What they are being offered is
 * to have both moved out of the way. That is the whole sentence.
 *
 * ## Why the sentence gets the whole width
 *
 * The first build put the text in a column beside the action and the dismiss, which left it about
 * 197dp of the screen's 372 — so both lines wrapped, and the banner stood four lines tall in a
 * layout meant to be quieter than the list under it. The sentence now runs the full width and the
 * two answers sit on a row of their own beneath it, which is Material's own banner shape and takes
 * the same words to two lines.
 *
 * The count line shrank with it. It read "165 notes have tags and titles written into the text",
 * which is the line above it said again; its job is the size of the thing, so it is now "In 165
 * notes." and nothing more.
 *
 * ## Both answers are buttons, and dismissing is still permanent
 *
 * The dismiss was an × in the corner until 2026-09-09. The × carried the meaning well — a small,
 * unlabelled no — but it is the harder target of the two and it made the row lopsided once the
 * actions moved to their own line. Two text buttons read better and hit better.
 *
 * What that costs is clarity about consequence: a labelled button next to "Move them" looks like
 * the reversible half of a pair, and this one is not — [Settings.moveTagsDeclined] is permanent and
 * the way back is a row in Settings the user has no reason to have found yet. So the label is "No
 * thanks" rather than "Not now", which would promise a return that never comes, and dismissing says
 * where the way back is, once, in a snackbar. See `MainActivity`.
 *
 * ## Why the noun is a variable
 *
 * A folder needs one half of this move, or the other, or both, and all three cases are real: the
 * author's own archive has tags in its bodies and titles in its frontmatter, the folder this was
 * built against had the reverse, and a folder straight out of the old editor has both. So every
 * sentence here takes [what] rather than saying "tags", because an offer that announces it will
 * move titles in a folder with no titles to move is an offer that has not looked.
 *
 * ## What the dialog has to say out loud
 *
 * - **How many notes.** "165 of your 168" is a different decision from "2 of your 168".
 * - **That it changes those files.** Said plainly, once. It is the only writing this app does that
 *   the user did not type.
 * - **What the tag list gains**, when it gains anything. On an archive whose tags already agree it
 *   gains nothing, and claiming otherwise would be a lie the drawer would then contradict.
 * - **How many notes gain a title**, when any do. Until they land, the editor's title field is empty
 *   for every one of them and the library is showing the file name in its place — which is not the
 *   same string for the 24 notes whose heading the file name cannot hold.
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
    val what = stringResource(moveTagsWhat(survey))
    Column(
        modifier = modifier
            .fillMaxWidth()
            // A step *down* the ramp — `surfaceContainerLow` is below the page in both schemes —
            // so this is a recess cut into the list rather than a card resting on it. Raising it a
            // step was tried and rejected on 2026-09-09: at this palette's contrast a lifted band
            // reads as a slab, the same finding that left the top bar with no ground of its own.
            // The recess is quieter, and quiet is right. It is an offer, not an alarm, and a folder
            // that never takes it must not feel nagged.
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.move_tags_banner, what),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = pluralStringResource(
                R.plurals.move_tags_banner_count,
                survey.notes,
                survey.notes,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Dismissing is permanent and the settings row is how it comes back — see MainActivity,
            // which says so in a snackbar at the moment it happens. A banner that returned on every
            // launch would be the app nagging about the user's own filing, which is theirs and not
            // its business.
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.move_tags_banner_dismiss),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOffer) {
                Text(stringResource(R.string.move_tags_banner_action))
            }
        }
    }
}

/**
 * "tags", "titles" or "tags and titles" — what this folder actually has written into its text.
 *
 * Every sentence in this file takes it, and so does the line the library says afterwards, so the
 * banner, the confirm, the settings row and the result cannot disagree about what was offered.
 */
@StringRes
fun moveTagsWhat(survey: Migration.Survey): Int = when {
    survey.tagged > 0 && survey.titled > 0 -> R.string.move_tags_what_both
    survey.titled > 0 -> R.string.move_tags_what_titles
    else -> R.string.move_tags_what_tags
}

/** The confirm. See [MoveTagsBanner] for what it has to say and what it must not promise. */
@Composable
fun MoveTagsDialog(
    survey: Migration.Survey,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val what = stringResource(moveTagsWhat(survey))
    AlertDialog(
        onDismissRequest = onDismiss,
        // The same mark as the tag rename, because this is the same subject. Two dialogs sharing an
        // icon is not a collision when they are about one thing.
        title = {
            DialogHeading(
                Icons.AutoMirrored.Outlined.Label,
                stringResource(R.string.move_tags_title, what),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    pluralStringResource(
                        R.plurals.move_tags_scope,
                        survey.notes,
                        survey.notes,
                        survey.scanned,
                        what,
                    ),
                )
                Text(stringResource(R.string.move_tags_body, what))
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
                // The title half's equivalent of the line above: what the note gains, rather than
                // what it loses. Until it lands the title field is empty for every one of these
                // notes and the library is showing the file name in its place.
                if (survey.titled > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.move_tags_titled,
                            survey.titled,
                            survey.titled,
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
