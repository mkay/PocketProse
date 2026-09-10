// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import de.singular.writer.vault.Vault

/**
 * Rename the note's file, by hand.
 *
 * **The app never picks a name here.** `Vault.create` gets to, once, because a note that does not
 * exist yet has to be called something; from then on the name is the user's, and `CLAUDE.md`'s first
 * prohibition is the app deciding on its own that it is wrong. This is the other side of that rule
 * rather than an exception to it: the person whose folder it is, saying what the file is called.
 *
 * Three things the dialog has to be honest about, and each is why a line of it exists:
 *
 * - **A filename cannot hold everything a title can.** `/ \ : * ? " < > |` are gone the moment they
 *   are typed, and 11 titles in the archive end in the `?` that no name can carry. So the field
 *   shows the name it would actually make, from [Vault.fileStem] itself rather than from a second
 *   copy of the rule, and it shows it *before* the confirm rather than in the result. When what is
 *   dropped leaves the name exactly as it was — typing a `?` onto a name that already exists — the
 *   confirm greys out, and the line underneath has to say why: the cause is a character that is no
 *   longer on the screen to be blamed.
 * - **The title is not the filename and does not follow it.** The frontmatter `title` stays exactly
 *   as it was — that is the whole reason this app can be pointed at an archive where the two already
 *   disagree in eleven places. Somebody renaming a file may well expect the title to move with it,
 *   and the only place to say otherwise is here.
 * - **`.md` is not negotiable.** The app lists `*.md`, so a note renamed out of that extension would
 *   vanish from its own library. The field holds the stem and the suffix is shown, not typed.
 *
 * The field opens on the current name and selects nothing, so a typo is a two-character fix. No
 * keyboard is forced up: unlike the tag rename, this dialog is also opened to look at a name.
 */
@Composable
fun RenameNoteDialog(
    name: String,
    /** Every name in the folder, so a collision is caught before the write rather than after. */
    taken: Set<String>,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = name.removeSuffix(".md")
    var typed by remember(name) { mutableStateOf(current) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // What the folder would actually end up holding. The same function the write uses — a preview
    // computed a second way is a preview that can be wrong about the one thing it is for.
    val stem = Vault.fileStem(typed)
    val target = "$stem.md"
    val dropped = stem != typed.trim()
    val unchanged = Vault.normalizedName(target) == Vault.normalizedName(name)
    val collides = !unchanged && taken.any { Vault.normalizedName(it) == Vault.normalizedName(target) }
    val valid = typed.isNotBlank() && !collides && !unchanged
    // Typed something, and it comes out as the name the note already has. Worth saying out loud,
    // because the confirm goes grey and the reason is a character that is no longer on screen:
    // `Adlerohr?` is `Adlerohr.md`, which is where it started. Retyping the name exactly does not
    // need explaining, so it does not get a line.
    val fruitless = unchanged && typed.trim() != current

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // The pencil that marks the row this dialog is opened from, repeated here so the tap and
        // what it produced are visibly the same gesture.
        title = {
            DialogHeading(Icons.Outlined.Edit, stringResource(R.string.rename_note_title))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.rename_note_label)) },
                    suffix = { Text(".md") },
                    isError = collides,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onRename(stem) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                // One line, and which one it is says what the user most needs to know right now: a
                // name that cannot be had beats a character that was dropped, which beats the
                // standing fact that the title stays put.
                Text(
                    text = when {
                        collides -> stringResource(R.string.rename_note_taken)
                        fruitless && dropped -> stringResource(R.string.rename_note_dropped_unchanged)
                        fruitless -> stringResource(R.string.rename_note_unchanged)
                        dropped -> stringResource(R.string.rename_note_dropped, target)
                        else -> stringResource(R.string.rename_note_keeps_title)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (collides) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onRename(stem) }) {
                Text(stringResource(R.string.rename_note_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
