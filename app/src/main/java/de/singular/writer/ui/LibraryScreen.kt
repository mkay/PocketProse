// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import de.singular.writer.vault.IndexedNote
import de.singular.writer.vault.VaultFailure

/**
 * The list of notes — the app's home, and the screen it is judged on.
 *
 * A row is title, the first line or so of the writing, and a quiet footer of date and tags. The
 * middle line is the reason the app exists: it is the note in the user's own words, not metadata
 * about the note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    notes: List<IndexedNote>,
    folderName: String?,
    error: VaultFailure?,
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    onSearchingChange: (Boolean) -> Unit,
    selectedTag: String?,
    onOpenDrawer: () -> Unit,
    onChooseFolder: () -> Unit,
    onOpenNote: (IndexedNote) -> Unit,
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
            TopAppBar(
                title = {
                    if (searching) {
                        SearchField(query, onQueryChange)
                    } else {
                        Column {
                            Text(
                                text = folderName ?: stringResource(R.string.library_title),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = selectedTag
                                    ?.let { stringResource(R.string.filter_showing, it) }
                                    ?: pluralStringResource(R.plurals.note_count, notes.size, notes.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.drawer_open),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onSearchingChange(!searching) }) {
                        Icon(
                            imageVector = if (searching) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(
                                if (searching) R.string.search_close else R.string.search_open,
                            ),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (notes.isEmpty()) {
                Empty(query, onChooseFolder)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(notes, key = { it.file.uri.toString() }) { note ->
                        NoteRow(note, onClick = { onOpenNote(note) })
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One note in the list.
 *
 * Three tiers, in descending weight: the title, the writing, then the footer. That order is the
 * whole design — a person scanning this list is looking for a song, and a song is recognised by its
 * words far more reliably than by when it was last saved.
 */
@Composable
private fun NoteRow(note: IndexedNote, onClick: () -> Unit) {
    val date = rememberDateFormatter()
    val excerpt = note.excerpt

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = note.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            // 40 of the author's 168 notes have no prose at all — they are a title filed under a
            // tag, which is a legitimate kind of note here and not a defect. The placeholder keeps
            // every row the same height so the list has an even rhythm; it is set in the secondary
            // colour and nothing about it is alarming.
            text = excerpt.ifEmpty { stringResource(R.string.note_empty) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Date left, tags right, on one line — see TagRow, which fits what it can and stands the
        // rest behind a +N chip. A plain Row here was wrong twice over: the tags drifted in from the
        // date rather than landing on an edge, and with five of them the last chip was squeezed
        // until its label wrapped, making the whole footer four lines tall.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        ) {
            note.updated?.let {
                Text(
                    text = date(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            TagRow(note.tags, Modifier.weight(1f))
        }
    }
}

/** Nothing matched — either a search or a tag filter. */
@Composable
private fun Empty(query: String, onChooseFolder: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = if (query.isBlank()) {
                stringResource(R.string.filter_no_results)
            } else {
                stringResource(R.string.search_no_results, query)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(text = stringResource(R.string.search_hint)) },
        singleLine = true,
        // No clear button inside the field. It put a second identical ✕ immediately beside the top
        // bar's close-search one, and two of the same glyph an inch apart is a coin toss rather than
        // a choice. The bar's ✕ closes search and clears the query in one go, which is what someone
        // reaching for either of them wanted.
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
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
