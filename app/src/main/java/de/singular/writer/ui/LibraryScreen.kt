// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import de.singular.writer.vault.NoteIndex
import de.singular.writer.vault.VaultFailure

/**
 * Phase 2's library: titles, newest first.
 *
 * Still not the finished row — the excerpt, the date and the tag chips arrive in phase 3, along with
 * the drawer and search. What it does have is the right *identity*: rows are notes rather than
 * files, titled from the frontmatter, so the four notes called "Wer geht vor?" appear four times and
 * the eleven titles ending in a question mark read correctly.
 */
@Composable
fun LibraryScreen(
    index: NoteIndex,
    folderName: String?,
    error: VaultFailure?,
    onChooseFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (error) {
        VaultFailure.NO_FOLDER_CHOSEN -> Invitation(
            title = stringResource(R.string.welcome_title),
            body = stringResource(R.string.welcome_body),
            action = stringResource(R.string.welcome_choose),
            onAction = onChooseFolder,
            modifier = modifier,
        )

        VaultFailure.FOLDER_UNREACHABLE, VaultFailure.FOLDER_UNREADABLE -> Invitation(
            title = stringResource(R.string.folder_gone_title),
            body = stringResource(R.string.folder_gone_body),
            action = stringResource(R.string.folder_gone_choose),
            onAction = onChooseFolder,
            modifier = modifier,
        )

        VaultFailure.FOLDER_EMPTY -> Invitation(
            title = stringResource(R.string.library_empty),
            body = folderName.orEmpty(),
            action = stringResource(R.string.action_change_folder),
            onAction = onChooseFolder,
            modifier = modifier,
        )

        null -> Column(modifier.fillMaxSize()) {
            // The folder can be changed from here as well as from the empty states. Leaving it out
            // of the populated screen stranded anyone who picked the wrong folder: the only way back
            // was to clear the app's data. Phase 3 gives this a proper top bar; until then it is a
            // plain row, and the action stays reachable at every point rather than only when
            // something has gone wrong.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = folderName ?: stringResource(R.string.library_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.note_count, index.size, index.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onChooseFolder, shape = ControlShape) {
                    Text(text = stringResource(R.string.action_change_folder))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(Modifier.fillMaxSize()) {
                items(index.notes, key = { it.file.uri.toString() }) { note ->
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

/** A centred title, a sentence, and one button — the shape every "nothing to show yet" state takes. */
@Composable
private fun Invitation(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onAction, shape = ControlShape) { Text(text = action) }
        }
    }
}
