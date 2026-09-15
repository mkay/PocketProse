// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.pluralStringResource
import de.singular.writer.R
import de.singular.writer.RowDensity
import de.singular.writer.SortBy
import de.singular.writer.SortOrder
import de.singular.writer.markdown.Migration
import de.singular.writer.markdown.Tags
import de.singular.writer.vault.AttachmentFilter
import de.singular.writer.vault.Filters
import de.singular.writer.vault.IndexedNote
import de.singular.writer.vault.VaultFailure
import java.time.Instant

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
    // True while a pull is being answered. Not `loading`: that one blanks the list for a folder
    // being read from nothing, and a pull is a folder being read again behind a list that stays.
    refreshing: Boolean,
    onRefresh: () -> Unit,
    // What `loading` is doing, for the drift's description. The library cannot tell a folder being
    // read from a tag being renamed across it, and both stop the list being drawable.
    @StringRes loadingSays: Int,
    // A line under the drift, or null for none — see `LoadingSheets`. Startup passes null.
    @StringRes loadingCaption: Int?,
    // All three questions as one value — see `Filters`. The tag inside it is the same tag the
    // drawer sets; there is one filter with two ways to reach it.
    filters: Filters,
    onOpenSearch: () -> Unit,
    onClearFilters: () -> Unit,
    // The offer to move a folder's inline tags into its notes, or null when there is nothing to
    // move or the user has waved it away. It sits between the header and the list rather than over
    // them: this is an offer about the notes below it, and it must be ignorable. See MoveTagsBanner.
    moveTags: Migration.Survey?,
    onMoveTags: () -> Unit,
    onDismissMoveTags: () -> Unit,
    // How the list is ordered and how much of a note a row shows. Both live in Settings, so they
    // survive a restart the way every other preference does; the menu that changes them is in this
    // screen's own bar because they are facts about this list rather than about the app.
    sortBy: SortBy,
    sortOrder: SortOrder,
    onSortChange: (SortBy, SortOrder) -> Unit,
    // The tag that puts a note on top — see `Settings.pinTag`. The caller has already ordered the
    // list; the screen only needs to know which rows to mark.
    pinTag: String,
    density: RowDensity,
    onDensityChange: (RowDensity) -> Unit,
    // Multi-select. Hoisted like `searching` is, because the Back handling lives in MainActivity and
    // a mode Back cannot leave is a trap.
    selecting: Boolean,
    selected: Set<String>,
    onToggleSelect: (IndexedNote) -> Unit,
    onStartSelecting: (IndexedNote?) -> Unit,
    onSelectAll: () -> Unit,
    onEndSelecting: () -> Unit,
    onTagSelected: () -> Unit,
    onMergeSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onOpenDrawer: () -> Unit,
    onChooseFolder: () -> Unit,
    onOpenNote: (IndexedNote) -> Unit,
    onNewNote: () -> Unit,
    // A sentence to show once, or null. The library had no host of its own until 2026-09-09, so
    // everything it had to say — a finished tag rename, a finished tag move, a note that could not
    // be made — sat in the state unshown until an editor was opened and said it there, under a
    // "Couldn't save:" that belonged to something else entirely.
    message: String?,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (error) {
        VaultFailure.NO_FOLDER_CHOSEN -> Invitation(
            title = stringResource(R.string.welcome_title),
            body = stringResource(R.string.welcome_body),
            action = stringResource(R.string.welcome_choose),
            onAction = onChooseFolder,
            modifier = modifier,
            // Only here. This is the first screen of the app and there is nothing else on it to say
            // what has been opened — the other two invitations are a folder that went missing and a
            // folder with nothing in it, both of which are things that happened to somebody already
            // using the app, and neither wants to be introduced to it again.
            mark = true,
        )

        VaultFailure.FOLDER_UNREACHABLE, VaultFailure.FOLDER_UNREADABLE -> Invitation(
            title = stringResource(R.string.folder_gone_title),
            body = stringResource(R.string.folder_gone_body),
            action = stringResource(R.string.folder_gone_choose),
            onAction = onChooseFolder,
            modifier = modifier,
        )

        // An empty folder is the library with nothing in it, not a fault. Until 2026-09-15 it got
        // an invitation of its own — "No notes in this folder yet" over a "Change folder" button —
        // and that screen had no way to write the first note: the + button lives on the list, and
        // the list was never reached. Somebody starting from an empty folder rather than from an
        // export had to leave and come back with a file already in it. A tester found it on the
        // first run. So the folder falls through to the list, which says the same sentence in the
        // list's own empty state and keeps the button.
        VaultFailure.FOLDER_EMPTY, null -> Box(modifier.fillMaxSize()) {
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(message) {
                if (message != null) {
                    snackbar.showSnackbar(message)
                    onMessageShown()
                }
            }
            Column(Modifier.fillMaxSize()) {
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
                    if (selecting) {
                        // The count, not the wordmark. While a selection is on, the one thing the
                        // bar has to say is how much is about to be acted on.
                        Text(
                            text = pluralStringResource(
                                R.plurals.selected_count,
                                selected.size,
                                selected.size,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
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
                    if (selecting) {
                        // The way out sits where Back would put it, and does what Back does.
                        IconButton(onClick = onEndSelecting) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.selection_end),
                            )
                        }
                    } else {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.drawer_open),
                            )
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = onSelectAll) {
                            Icon(
                                imageVector = Icons.Filled.SelectAll,
                                contentDescription = stringResource(R.string.select_all),
                            )
                        }
                        // Filing before deleting: the common act sits nearer the thumb than the
                        // destructive one, and the destructive one keeps the edge.
                        IconButton(onClick = onTagSelected, enabled = selected.isNotEmpty()) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_label_more),
                                contentDescription = stringResource(R.string.retag_selection),
                            )
                        }
                        // Two is the smallest thing that can be merged. One note has nothing to
                        // fold into, so the button waits for a second tick rather than opening a
                        // dialog that would have to refuse.
                        IconButton(onClick = onMergeSelected, enabled = selected.size >= 2) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_stack_group),
                                contentDescription = stringResource(R.string.merge_notes),
                            )
                        }
                        // Enabled only with something ticked, and tinted `error` — the one colour
                        // this palette spends on the one thing the app cannot undo. See DeleteDialog.
                        IconButton(onClick = onDeleteSelected, enabled = selected.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.delete_note),
                                tint = if (selected.isEmpty()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    } else {
                        ListOptionsMenu(
                            sortBy = sortBy,
                            sortOrder = sortOrder,
                            onSortChange = onSortChange,
                            density = density,
                            onDensityChange = onDensityChange,
                            onSelectNotes = { onStartSelecting(null) },
                        )
                        // The bar keeps the wordmark now; the field moved into the dialog. The
                        // icon changes when something is set, because **an active filter nobody
                        // can see is the failure this whole change is about** — but it still just
                        // opens the dialog. Clearing is a labelled button in there, never a second
                        // tap out here that silently throws away what was asked for.
                        //
                        // Still a magnifier when set — a magnifier with a check in it — rather
                        // than a funnel. The funnel read as a second feature; this reads as the
                        // same control in another state, which is what it is. Both states come
                        // from the same Material Symbols export so the glass is the same glass.
                        IconButton(onClick = onOpenSearch) {
                            Icon(
                                imageVector = ImageVector.vectorResource(
                                    if (filters.isEmpty) R.drawable.ic_search else R.drawable.ic_search_check,
                                ),
                                contentDescription = stringResource(R.string.search_open),
                                tint = if (filters.isEmpty) {
                                    LocalContentColor.current
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                },
                // The bar and the strip beneath it share one ground, so the top of the screen reads
                // as a single header block sitting a shade above the page rather than as two bands.
                // The only rule below it is the one under the strip, dividing header from list.
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
            StatusStrip(
                count = notes.size,
                filters = filters,
                counted = !loading,
                onClear = onClearFilters,
            )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Under the divider, so it is part of the page rather than part of the header, and only
            // once the folder has actually been read — an offer about notes nobody has seen yet is
            // the thing this design exists to avoid.
            if (moveTags != null && !loading) {
                MoveTagsBanner(
                    survey = moveTags,
                    onOffer = onMoveTags,
                    onDismiss = onDismissMoveTags,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (loading) {
                // What must not happen here is the "nothing matches" line appearing before anything
                // has been looked at, which is why this branch exists at all.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingSheets(
                        Modifier.padding(bottom = 48.dp),
                        describedBy = loadingSays,
                        caption = loadingCaption,
                    )
                }
            } else {
            // Pull to refresh, on the list and on the empty state alike — a filter that matches
            // nothing is still a folder that may have changed. Four messages in the app already
            // tell the reader to "pull to refresh" after a sync landed mid-write; until 2026-09-10
            // the gesture they named did not exist, and the only way to re-read was to leave the
            // app and come back.
            //
            // The indicator is drawn in the app's own ink on its own surface rather than Material's
            // primary-on-primaryContainer, which in this palette is a taupe disc nothing else on
            // the screen wears.
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                state = pullState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = refreshing,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) {
            if (notes.isEmpty()) {
                // Scrollable so that the pull has something to pull on: a plain Box swallows the
                // gesture and the empty state would be the one place a refresh cannot be asked
                // for.
                // The scroll makes the height unbounded, so the message is given the screen's own
                // height back explicitly — otherwise it wraps and sits at the top instead of centred.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val screen = maxHeight
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Box(Modifier.fillMaxWidth().height(screen)) {
                            Empty(filters, folderEmpty = error == VaultFailure.FOLDER_EMPTY)
                        }
                    }
                }
            } else {
                val listState = rememberLazyListState()
                // Picking a tag or an order is asking a different question, so the answer starts
                // at the top.
                //
                // Without this the list keeps its offset: the rows are keyed by uri, so Lazy tries
                // to hold the note you were looking at. Filtering, that note may not be in the new
                // set at all and you land somewhere arbitrary in a list you have never seen — a
                // filter of three notes could open scrolled past all of them. Sorting, it is worse
                // for being subtler: the note *is* still there, so the list obligingly keeps you
                // level with it, several screens into an order you chose precisely to see the top
                // of.
                //
                // Instant rather than animated. An animation carries the eye from one place to
                // another in the same list; here the list has been replaced or reordered under it,
                // so there is nothing in between to travel through.
                //
                // Not on the search text, with one exception. Firing per keystroke would fight
                // somebody scrolling a result set while still typing, and narrowing a list already
                // brings the best matches up. But `isEmpty` is in the keys, so the two edges are
                // caught: the first character typed, and — the one that matters — clearing the
                // filters, which puts a list of 168 back under somebody who was three rows into a
                // list of four.
                LaunchedEffect(filters.tag, filters.attachments, filters.duplicates, filters.isEmpty, sortBy, sortOrder) {
                    if (notes.isNotEmpty()) listState.scrollToItem(0)
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(notes, key = { it.file.uri.toString() }) { note ->
                        val uri = note.file.uri.toString()
                        NoteRow(
                            note = note,
                            density = density,
                            // The date a row carries is the one the list is in the order of.
                            // Ordered by creation and captioned with the last edit, the dates
                            // would run out of sequence down the page and the reader could not
                            // tell the sort was working. `updated` for every other sort, as
                            // before: a list by title still says when the note was last touched.
                            date = if (sortBy == SortBy.CREATED) note.created else note.updated,
                            // The file name is what tells four notes titled "Wer geht vor?" apart
                            // when the tags do not, and it is the one thing a merge or a delete
                            // will be judged by. Shown only here: on an ordinary list it is a
                            // second, less readable title under every row.
                            showFileName = filters.duplicates,
                            pinned = note.tags.any { Tags.isUnder(it, pinTag) },
                            selecting = selecting,
                            // Ticked by uri and never by title: four notes in the archive are called
                            // "Wer geht vor?" and three more share another title, so a selection
                            // keyed on the name would delete the wrong file.
                            checked = uri in selected,
                            onClick = {
                                if (selecting) onToggleSelect(note) else onOpenNote(note)
                            },
                            onLongClick = { if (!selecting) onStartSelecting(note) },
                        )
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

            // Over the list rather than in the bar. Starting a note is the one thing you come to
            // this screen to *do* — everything else here is looking — and it belongs under the
            // thumb rather than up in the corner beside the search.
            NewNoteButton(
                onClick = onNewNote,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(20.dp),
            )

            // Above the new-note button rather than beside it: a snackbar that shares the bottom
            // edge with a floating button covers it, and the button is the one thing on this screen
            // somebody might be reaching for while the message is still up.
            NoticeHost(
                snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 76.dp),
            )
        }
    }
}

/**
 * The button that starts a note.
 *
 * Quiet, like everything else here: the accent is barely a colour by design, so a filled button
 * reads as a warm grey rather than as a splash. Small rather than the full 56dp — the screen is
 * somebody's writing and this is a tool on top of it.
 */
@Composable
private fun NewNoteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = ControlShape,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.new_note),
        )
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
private fun StatusStrip(count: Int, filters: Filters, counted: Boolean, onClear: () -> Unit) {
    // **Everything that is on, named.** It said the tag alone, which was true while the tag was the
    // only filter that could be set from somewhere you could not see. Now three can be, and a strip
    // that mentioned one of them would be worse than the old silence: it would look like a complete
    // answer.
    //
    // Assembled here rather than as one format string with three optional arguments — a translator
    // handed `%1$s %2$s %3$s` cannot know which are present, and German would want them in another
    // order anyway. Each piece is its own string and the joining is punctuation.
    val parts = buildList {
        filters.tag?.let { add(it) }
        filters.text.trim().takeIf { it.isNotEmpty() }
            ?.let { add(stringResource(R.string.filter_by_text, it)) }
        when (filters.attachments) {
            AttachmentFilter.ANY -> Unit
            AttachmentFilter.WITH -> add(stringResource(R.string.filter_by_attachments_with))
            AttachmentFilter.WITHOUT -> add(stringResource(R.string.filter_by_attachments_without))
        }
        if (filters.duplicates) add(stringResource(R.string.filter_by_duplicates))
    }
    val summary = parts.takeIf { it.isNotEmpty() }
        ?.let { stringResource(R.string.filter_showing, it.joinToString(" · ")) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Held at the height it will have once the count arrives, so the list below does not
            // jump up the screen the moment the folder finishes reading.
            .heightIn(min = 28.dp)
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        if (summary != null) {
            // weight(1f) rather than weight(1f, fill = false): the label must claim the whole space
            // left over so the count is pushed flush to the right edge, where it lines up with the
            // counts in the tag drawer. With fill = false a short tag left the count floating.
            Text(
                text = summary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        // The quick way out, next to the thing it undoes. Back clears the filters too, and the
        // dialog has a labelled Clear — but both mean leaving what you are looking at or opening a
        // panel over it, and a filter you can see should be removable where you can see it.
        //
        // Only while something is set, so the strip does not carry a permanent × for the folder's
        // own unfiltered state.
        if (summary != null) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(28.dp).padding(start = 4.dp, end = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.search_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        // Nothing at all until the folder has actually been read. "0 notes" is a statement about
        // the archive, and for the moment before the first read it is a false one — the strip said
        // it on every start, in front of 168 notes.
        if (counted) {
            Text(
                text = pluralStringResource(R.plurals.note_count, count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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
/**
 * The menu behind the list icon: how this list is ordered, how much of a note it shows, and the way
 * into multi-select.
 *
 * One menu rather than three controls in the bar. All three are "how this list behaves", none of
 * them is reached often, and the bar of a reading screen is not the place to spend three slots on
 * settings — the one thing that belongs out in the open there is search.
 *
 * The active row carries a check that is **always laid out** and only coloured in, the same rule the
 * tag sheet's rows follow: a check that appears and disappears shifts every label beside it, and a
 * menu whose text moves as you read it looks like it reordered itself.
 */
@Composable
private fun ListOptionsMenu(
    sortBy: SortBy,
    sortOrder: SortOrder,
    onSortChange: (SortBy, SortOrder) -> Unit,
    density: RowDensity,
    onDensityChange: (RowDensity) -> Unit,
    onSelectNotes: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    IconButton(onClick = { open = true }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.list_options),
        )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        listOf(
            SortBy.UPDATED to R.string.sort_by_updated,
            SortBy.CREATED to R.string.sort_by_created,
            SortBy.TITLE to R.string.sort_by_title,
            SortBy.WORDS to R.string.sort_by_length,
        ).forEach { (value, label) ->
            CheckableItem(stringResource(label), value == sortBy) {
                // Picking an order never changes the direction. Somebody who has set the list to
                // A-to-Z and then switches to length has said nothing about which end they want.
                onSortChange(value, sortOrder)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        // Not a check but a switch, and **it says what the chosen sort makes it mean**. One label
        // for all three — "Newest, Z, longest first" — was what shipped first, and it made the
        // reader parse three orders to find the one they were in. A direction has no meaning apart
        // from the thing it is a direction of.
        val down = sortOrder == SortOrder.DESC
        CheckableItem(
            label = stringResource(
                when (sortBy) {
                    SortBy.UPDATED, SortBy.CREATED -> if (down) R.string.sort_updated_desc else R.string.sort_updated_asc
                    SortBy.TITLE -> if (down) R.string.sort_title_desc else R.string.sort_title_asc
                    SortBy.WORDS -> if (down) R.string.sort_length_desc else R.string.sort_length_asc
                },
            ),
            checked = false,
            icon = if (down) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
        ) {
            onSortChange(sortBy, if (down) SortOrder.ASC else SortOrder.DESC)
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        listOf(
            RowDensity.FULL to R.string.density_full,
            RowDensity.COMPACT to R.string.density_compact,
        ).forEach { (value, label) ->
            CheckableItem(stringResource(label), value == density) { onDensityChange(value) }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        CheckableItem(
            stringResource(R.string.select_notes),
            checked = false,
            icon = ImageVector.vectorResource(R.drawable.ic_library_add_check),
        ) {
            open = false
            onSelectNotes()
        }
    }
}

/** A menu row with room for a mark on the left, whether or not it is currently wearing one. */
@Composable
private fun CheckableItem(
    label: String,
    checked: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon ?: Icons.Filled.Check,
                contentDescription = null,
                tint = if (checked || icon != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    Color.Transparent
                },
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: IndexedNote,
    density: RowDensity,
    date: Instant?,
    showFileName: Boolean,
    pinned: Boolean,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val format = rememberDateFormatter()
    val excerpt = note.excerpt
    val compact = density == RowDensity.COMPACT

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // The same ground a picked tag chip wears, so "selected" looks like one thing across
            // the app rather than like two ideas that happen to both mean chosen.
            .background(
                if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (selecting) {
            Icon(
                imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (checked) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(start = 20.dp).size(22.dp),
            )
        }
    Column(
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
        modifier = Modifier
            .weight(1f)
            .padding(
                start = if (selecting) 16.dp else 20.dp,
                end = 20.dp,
                top = if (compact) 10.dp else 14.dp,
                bottom = if (compact) 10.dp else 14.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // A note that carries files says so on the title line, where the eye already is, and
            // on the right edge, where the eye can find it without reading the titles. Only three of
            // the archive's 168 notes carry anything, so a mark that trailed each title would appear
            // at a different x on every row it turned up on — a few ragged marks in 168 rows, which
            // is harder to spot than one column with a few things in it.
            //
            // The title takes the rest of the width whether or not the mark is there, so a row does
            // not reflow when one appears.
            if (note.hasAttachments) {
                Icon(
                    painter = painterResource(R.drawable.ic_attach_file),
                    contentDescription = stringResource(R.string.note_has_attachments),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 8.dp, top = 3.dp).size(16.dp),
                )
            }
            // The pin on the outer edge, so it is the one mark that always sits in the same column:
            // it is the reason the row is where it is, and the eye looks for it at the top of the
            // list rather than scanning every row for it.
            if (pinned) {
                Icon(
                    painter = painterResource(R.drawable.ic_keep),
                    contentDescription = stringResource(R.string.note_pinned),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 8.dp, top = 3.dp).size(16.dp),
                )
            }
        }
        if (showFileName) {
            Text(
                text = note.file.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Compact drops the writing and the date and keeps the tags. Not an arbitrary half: the
        // excerpt is the tallest part of the row and the least useful when you already know which
        // note you are after, while the tags are the only thing that tells four notes titled
        // "Wer geht vor?" apart.
        if (!compact) {
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
        }
        // Date left, tags right, on one line — see TagRow, which fits what it can and stands the
        // rest behind a +N chip. A plain Row here was wrong twice over: the tags drifted in from the
        // date rather than landing on an edge, and with five of them the last chip was squeezed
        // until its label wrapped, making the whole footer four lines tall.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        ) {
            if (!compact) date?.let {
                Text(
                    text = format(it),
                    style = MaterialTheme.typography.labelSmall,
                    // A step quieter than the excerpt above it. The date is the least useful thing
                    // in the row for finding a note, so it reads as a footnote to the tags beside
                    // it rather than as a peer of the writing.
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
            if (!compact) Spacer(Modifier.width(12.dp))
            // Sorted for the eye only — the file keeps its own order, and two notes carrying the
            // same tags in different orders read as the same row here. The same order as the
            // drawer's, and as the editor's foot.
            TagRow(note.tags.sorted(), Modifier.weight(1f))
        }
    }
    }
}

/** Nothing matched — either a search or a tag filter. */
@Composable
private fun Empty(filters: Filters, folderEmpty: Boolean) {
    val query = filters.text
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = when {
                // Nothing to filter yet, whatever the filter says.
                folderEmpty -> stringResource(R.string.library_empty)
                query.isNotBlank() -> stringResource(R.string.search_no_results, query)
                // The one empty list that is good news, and it should read as such.
                filters.duplicates -> stringResource(R.string.filter_no_duplicates)
                else -> stringResource(R.string.filter_no_results)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}


/**
 * The app's mark, above the welcome screen's first sentence.
 *
 * **No tile and no clip**, unlike the launcher, because the artwork carries its own ground now —
 * `ic_mark.xml` is mk6, whose pale disc sits under the whole notebook. That disc is the reason this
 * composable is three lines instead of a tile, a crop and a corner radius.
 *
 * It replaced exactly that. `AboutScreen` had already found that this artwork cannot go bare on a
 * themed page: its ink runs from near-black to near-white and every contrast ratio it was drawn to
 * assumed the launcher's tile, so on the dark page its lower edges dissolved at 1.1:1 and on the
 * light page the paper's outer edge went at 1.05:1 — a different piece of the silhouette missing on
 * each theme. Reassembling the adaptive icon here worked, and cost a dark rounded square on a page
 * that has no other box on it. The disc does the tile's job at 6.87:1 and is not a box.
 *
 * The drawable's own 84dp is the size; see `ic_mark.xml` for what that makes the disc.
 */
@Composable
private fun AppMark() {
    Image(
        painter = painterResource(R.drawable.ic_mark),
        // Named by the title beside it, which is the actual sentence on the screen. A reader
        // hearing "Pocket Prose, Your notes live in a folder" is being told the app's name twice
        // before the sentence that matters.
        contentDescription = null,
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
    /** Whether to introduce the app above the title. See the call site for why only one state does. */
    mark: Boolean = false,
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
            if (mark) {
                AppMark()
                // A step more air under the icon than the 12dp the column spaces everything by: the
                // icon and the title are two different kinds of thing, and run together at 12dp the
                // title reads as a caption on the picture.
                Spacer(Modifier.height(8.dp))
            }
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

