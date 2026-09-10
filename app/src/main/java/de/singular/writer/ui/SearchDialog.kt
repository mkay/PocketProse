// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import de.singular.writer.markdown.Tags
import de.singular.writer.vault.AttachmentFilter
import de.singular.writer.vault.Filters

/**
 * The three questions the library can be asked, in one place.
 *
 * They used to be spread across two screens and one absent feature: the text in the bar, the tag in
 * the drawer, and no way at all to ask which notes carry files. Nothing named more than one of them
 * at a time, so a search inside a tag looked like a search of the folder and quietly was not.
 *
 * **Nothing here waits for an Apply.** Every keystroke and every chip updates the filter as it is
 * touched, and [matches] says how many notes are left. Search-as-you-type is the fastest way to find
 * a song by its name and is what search is used for nearly always; a modal apply step would take
 * that away to buy a tidiness nobody asked for. The count is what stands in for seeing the rows,
 * which a dialog is necessarily covering.
 *
 * The button is nonetheless labelled *Apply*, and it is the one place this file is not literal. It
 * said *Close*, which is what it does, and that read as though the panel had come to nothing — the
 * filtering having happened invisibly behind it. What the tap gives the reader is the list they have
 * just described, and *Apply* is the word for that even though the applying is already done.
 *
 * **The tag here is the drawer's tag**, not a second one. One filter with two ways to set it: the
 * status strip already names it, Back already clears it, and `Settings.startTag` already seeds it.
 * Two tag filters narrowing the same list is the kind of thing that looks broken once you forget one
 * is set — which is the failure this dialog exists to end, not to reproduce.
 *
 * The tree is [TagDrawer] itself rather than a copy, as the start-tag picker in Settings does: same
 * rows, same counts, same expand-a-parent gesture, and its "All notes" row is how the tag is cleared,
 * which is why there is no separate button for it.
 */
@Composable
fun SearchDialog(
    filters: Filters,
    onFiltersChange: (Filters) -> Unit,
    matches: Int,
    tree: List<Tags.Node>,
    totalNotes: Int,
    onDismiss: () -> Unit,
) {
    // **No keyboard on open, deliberately.** The tag rename raises one because it is a dialog nobody
    // opens except to type in; this one is opened to set any of three things, and two of them are
    // below the field. Raised on arrival the keyboard covered the chips, the count and both buttons,
    // leaving no way out but scrolling a panel whose bottom was off the screen.

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogHeading(Icons.Filled.Search, stringResource(R.string.search_title)) },
        text = {
            // Scrolls, because a field plus three chips plus a tree does not fit a phone once the
            // keyboard is up — which it is as soon as somebody taps the field.
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = filters.text,
                    onValueChange = { onFiltersChange(filters.copy(text = it)) },
                    label = { Text(stringResource(R.string.search_text_label)) },
                    singleLine = true,
                    shape = ControlShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // The keyboard's own search key closes the dialog rather than searching: the
                    // searching already happened, a keystroke ago.
                    keyboardActions = KeyboardActions(onSearch = { onDismiss() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Labelled, because three bare words are not self-explanatory: "Mit" answers a
                // question the reader has to have been told. The chips carry one word each and share
                // the width equally — "Without files" wrapped to two lines and left the row ragged,
                // which put the noun in the label where it is said once.
                Text(
                    text = stringResource(R.string.search_attachments_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AttachmentFilter.ANY to R.string.search_attachments_any,
                        AttachmentFilter.WITH to R.string.search_attachments_with,
                        AttachmentFilter.WITHOUT to R.string.search_attachments_without,
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = filters.attachments == value,
                            onClick = { onFiltersChange(filters.copy(attachments = value)) },
                            label = {
                                Text(
                                    text = stringResource(label),
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            shape = ControlShape,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // What the three of them come to, live. This is the whole of the feedback a dialog
                // can give about a list it is standing in front of, so it is a count and not a
                // reassurance: zero says zero.
                //
                // **Above the tree, not under it.** Under it, it was the last thing in a scrolling
                // panel, so the number that answers every keystroke was the one part of the dialog
                // a keyboard was certain to cover.
                Text(
                    text = pluralStringResource(R.plurals.search_matches, matches, matches),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TagDrawer(
                    tree = tree,
                    totalNotes = totalNotes,
                    selected = filters.tag,
                    onSelect = { onFiltersChange(filters.copy(tag = it)) },
                    // Capped so what is above it stays on screen. The tree scrolls inside its own
                    // bounds, and it is the one part of this panel that can afford to be scrolled to.
                    modifier = Modifier.heightIn(max = 200.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.search_apply)) }
        },
        dismissButton = {
            // Only while there is something to clear, so the button is never a no-op wearing a verb.
            if (!filters.isEmpty) {
                TextButton(onClick = { onFiltersChange(Filters()) }) {
                    Text(stringResource(R.string.search_clear))
                }
            }
        },
    )
}
