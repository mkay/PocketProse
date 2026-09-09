// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import de.singular.writer.markdown.Tags

/**
 * Rename one tag, everywhere.
 *
 * A dialog with a confirm rather than an inline edit, because this is the only gesture in the app
 * that writes to more than one file — `lyrics/snippet` is 131 of the archive's 168 notes — and the
 * count is the thing the user needs before they commit, not after. It is reached by long-pressing a
 * row in the tag drawer.
 *
 * Three things the button has to say out loud, and each is a different sentence:
 *
 * - **How many notes.** "Rename in 131 notes" is a different decision from "rename in 2".
 * - **That children come too.** Renaming `album` moves `album/debut` with it, because the tree is
 *   built from path segments and a parent renamed alone would split the branch. Somebody looking at
 *   a row labelled `album` cannot see the three tags underneath it from here.
 * - **That an existing name is a merge.** Renaming onto a tag that already exists folds the two
 *   together, and renaming back does not separate them again. It is the one outcome here that is not
 *   undone by another rename, so it gets its own wording rather than a footnote.
 *
 * The field opens on the tag's own name so a typo is a two-character fix rather than a retype, and
 * the keyboard comes up with it: this is a dialog nobody opens except to type in.
 */
@Composable
fun RenameTagDialog(
    tag: String,
    known: Set<String>,
    counting: (String) -> Int,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember(tag) { mutableStateOf(tag) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // What the tag would actually become. Folded on the way in for the same reason the tag sheet
    // folds: the archive was case-flattened once already and a `Lyrics` beside `lyrics` splits a
    // branch of the drawer with no UI that makes it look like anything but a bug.
    val target = Tags.normalize(typed)
    val notes = remember(tag) { counting(tag) }

    // A name already in use, and not this tag's own name. `album` -> `album/debut` is also a merge in
    // effect, since the children move under the new path; the check is on the path, not on the leaf.
    val merges = target.isNotEmpty() && target != tag && known.any { Tags.isUnder(it, target) }
    val valid = target.isNotEmpty() && target != tag && ' ' !in target && '#' !in target

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_tag_title, tag)) },
        text = {
            Column {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.rename_tag_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onRename(target) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                Text(
                    text = when {
                        merges -> stringResource(R.string.rename_tag_merges, target)
                        else -> pluralStringResource(R.plurals.rename_tag_scope, notes, notes)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onRename(target) }) {
                Text(
                    stringResource(
                        if (merges) R.string.rename_tag_confirm_merge else R.string.rename_tag_confirm,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
