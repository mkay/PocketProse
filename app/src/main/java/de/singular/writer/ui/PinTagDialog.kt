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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import de.singular.writer.markdown.Tags

/**
 * Name the tag that pins a note — see `Settings.pinTag`.
 *
 * A text field and not the tag drawer the start view picks from, because the pin tag is the one tag
 * that usually does not exist yet: it comes into being with the first pin. A picker that offered
 * only existing tags would need a note tagged by hand first so the name could be chosen, which is
 * the chicken-and-egg this dialog is here to avoid. An existing tag can be typed too — pointing the
 * pin at a `set` or `favoriten` the folder already has is a fair choice.
 *
 * The line underneath says how many notes carry the typed name now, which is how many would sit on
 * top the moment it is confirmed. Zero is the ordinary answer and is said as such rather than
 * treated as a warning.
 */
@Composable
fun PinTagDialog(
    tag: String,
    counting: (String) -> Int,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember(tag) { mutableStateOf(tag) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Folded the way every tag input is — see the tag sheet — so `Pinned` and `pinned` cannot come
    // to mean two different things.
    val target = Tags.normalize(typed)
    val accepted = Tags.accepts(target)
    val valid = accepted && target != tag
    val refusal = when {
        target.isEmpty() || accepted -> null
        ' ' in target -> R.string.tag_no_spaces
        else -> R.string.tag_no_hash
    }
    val notes = if (accepted) counting(target) else 0

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogHeading(
                ImageVector.vectorResource(R.drawable.ic_keep),
                stringResource(R.string.settings_pin_tag),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = Tags.typed(it) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.pin_tag_label)) },
                    isError = refusal != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onSelect(target) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                Text(
                    text = when {
                        refusal != null -> stringResource(refusal)
                        else -> pluralStringResource(R.plurals.pin_tag_scope, notes, notes)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (refusal != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSelect(target) }) {
                Text(stringResource(R.string.pin_tag_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
