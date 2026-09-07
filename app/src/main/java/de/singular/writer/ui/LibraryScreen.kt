// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
    loading: Boolean,
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
            // The header is one block — bar, strip, and the status bar above them — and it is the
            // **page's own colour**, with no tint at all. It takes the top inset itself so the page
            // runs to the very top of the screen; see the note in MainActivity.
            //
            // Two tinted versions were tried on 2026-09-07 and both were wrong. Tinting the strip
            // alone made a band across the screen that read as a toolbar rather than as a caption.
            // Tinting bar and strip together fixed that but put the header a step *down* the ramp
            // from the page, so the top of the screen receded when what a header does is sit above.
            // Going a step up instead was the obvious next move and was not taken: at this palette's
            // contrast a lifted header is still a slab, and the divider under the strip already
            // says where the list begins. The header needs no ground of its own.
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            TopAppBar(
                title = {
                    if (searching) {
                        SearchField(query, onQueryChange)
                    } else {
                        // The wordmark is the title of the list, filtered or not. It briefly changed
                        // to the folder name under a filter, on the reasoning that the bar should
                        // say *where* once the strip says *what* — but a title that changes when you
                        // pick a tag reads as having navigated somewhere else, when all that has
                        // happened is that the same list got shorter. The strip carries the filter;
                        // the bar stays put.
                        Icon(
                            painter = painterResource(R.drawable.wordmark),
                            contentDescription = stringResource(R.string.app_name),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.height(20.dp),
                        )
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
                // The bar and the strip beneath it share one ground, so the top of the screen reads
                // as a single header block sitting a shade above the page rather than as two bands.
                // The only rule below it is the one under the strip, dividing header from list.
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
            StatusStrip(count = notes.size, tag = selectedTag)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (loading) {
                // Deliberately blank. The folder is on the phone, so the first read takes a moment
                // rather than a while, and a spinner for that long is a flicker rather than
                // feedback. What must not happen is the "nothing matches" line appearing before
                // anything has been looked at.
                Box(Modifier.fillMaxSize())
            } else if (notes.isEmpty()) {
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
 * The thin line under the title: what is being shown, and how much of it.
 *
 * It used to be a second line inside the top bar, stacked under the folder name. That made the bar
 * two-storeys tall on every screen to carry a count that is glanceable at best, and it left the
 * name and the count reading as one block of title. A strip of its own is quieter — a different,
 * slightly recessed ground says "this is about the list below", not "this is the heading".
 *
 * The tag sits left and the count right, on one line. That avoids joining them with a separator,
 * which would be either a hardcoded character or a string resource that no translator can do
 * anything useful with.
 */
@Composable
private fun StatusStrip(count: Int, tag: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        if (tag != null) {
            // weight(1f) rather than weight(1f, fill = false): the label must claim the whole space
            // left over so the count is pushed flush to the right edge, where it lines up with the
            // counts in the tag drawer. With fill = false a short tag left the count floating.
            Text(
                text = stringResource(R.string.filter_showing, tag),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = pluralStringResource(R.plurals.note_count, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
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
            // tag, which is a legitimate kind of note here and not a defect. A bare dash keeps
            // every row the same height so the list has an even rhythm, without a sentence that
            // draws more attention to the gap than the gap deserves.
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

/**
 * The search field, which takes the cursor the moment it appears.
 *
 * Tapping the magnifier is unambiguously "I want to search", so making someone then tap the field
 * as well is a second gesture for a decision already made. The focus request also brings the
 * keyboard up, so the next thing that happens is typing.
 *
 * The requester is fired from a [LaunchedEffect] keyed on nothing, so it runs once when the field
 * enters the composition and never again — re-requesting focus on every recomposition would fight
 * the user the moment they tapped anywhere else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

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
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
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
    Box(
        modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentAlignment = Alignment.Center,
    ) {
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
