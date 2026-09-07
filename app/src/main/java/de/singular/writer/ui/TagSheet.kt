// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import de.singular.writer.markdown.Tags

/**
 * The sheet that opens from a note's chip row: every tag in the folder, checkable, plus a field for
 * one that does not exist yet.
 *
 * **A sheet rather than an `✕` on each chip.** Removing a tag rewrites a file — the `tags:` list and
 * the note's own hashtag line, in one save — and a control that does that on a mis-tap, sitting
 * directly under the text somebody is writing in, is the wrong shape for the consequence. Here the
 * whole change is made deliberately and in one place, and dismissing the sheet writes nothing.
 *
 * **The folder's own tags, not a free-text field first.** 24 tags cover 168 notes and `lyrics/snippet`
 * alone covers 131, so the overwhelmingly common act is filing a note under something that already
 * exists. Typing a new one is possible and is deliberately the second thing on the sheet.
 *
 * Ordered the way the drawer orders them — alphabetically, with the ones already on this note first,
 * because those are what the reader came to check. No `#` anywhere: a tag reads `lyrics/snippet`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSheet(
    chips: List<NoteDocument.Chip>,
    known: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = chips.filter { it.removable }.map { it.tag }.toSet()
    val untracked = chips.filterNot { it.removable }.map { it.tag }.toSet()

    // On this note first, then the rest of the folder. Within each group alphabetical, so a tag is
    // where it was last time — see Tags.tree for why frequency order was rejected there too.
    val order = compareByDescending<String> { it in selected || it in untracked }
        .then(String.CASE_INSENSITIVE_ORDER)
    val rows = remember(known, selected, untracked) {
        (known + selected + untracked).sortedWith(order)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(bottom = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.tag_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss, shape = ControlShape) {
                    Text(text = stringResource(R.string.tag_sheet_done))
                }
            }

            NewTagField(onAdd = onToggle)

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            // Capped rather than free: the sheet must not grow past the point where the note behind
            // it disappears, and 25 tags do not need a full screen.
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(rows, key = { it }) { tag ->
                    TagSheetRow(
                        tag = tag,
                        checked = tag in selected,
                        // A tag the note's text carries but its list does not. The app shows it and
                        // will not file it on the author's behalf — see Note.editableTags.
                        untracked = tag in untracked && tag !in selected,
                        onClick = { onToggle(tag) },
                    )
                }
            }
        }
    }
}

/**
 * The field for a tag the folder does not have yet.
 *
 * Lowercased on entry by [Tags.normalize], and a leading `#` is quietly dropped for anyone who types
 * one out of habit. The archive was deliberately case-folded once already and a `Lyrics` beside the
 * existing `lyrics` would split a tag in the drawer with no UI that makes it look like anything but
 * a bug.
 */
@Composable
private fun NewTagField(onAdd: (String) -> Unit) {
    var typed by remember { mutableStateOf("") }
    val tag = Tags.normalize(typed)
    val submit = {
        if (tag.isNotEmpty()) {
            onAdd(tag)
            typed = ""
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            label = { Text(text = stringResource(R.string.tag_sheet_new)) },
            singleLine = true,
            shape = ControlShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = submit, enabled = tag.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.tag_sheet_add),
            )
        }
    }
}

/** One tag in the sheet: a check where one would go, the tag, and why it cannot be unchecked. */
@Composable
private fun TagSheetRow(tag: String, checked: Boolean, untracked: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !untracked, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            // Always laid out, so the tags line up whether or not they are on this note; only the
            // colour says which. A row that shifted sideways on being checked would read as the
            // list reordering itself.
            tint = if (checked) scheme.onSurface else Color.Transparent,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.tag_sheet_toggle, tag),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
                color = if (untracked) scheme.onSurfaceVariant else scheme.onSurface,
            )
            if (untracked) {
                Text(
                    text = stringResource(R.string.tag_sheet_untracked),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
