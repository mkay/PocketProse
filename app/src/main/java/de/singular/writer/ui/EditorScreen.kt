// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import androidx.compose.ui.text.TextRange
import de.singular.writer.markdown.FormatActions
import de.singular.writer.markdown.LinkRef
import de.singular.writer.markdown.Segment
import de.singular.writer.markdown.Segments
import de.singular.writer.markdown.Tags
import de.singular.writer.vault.Attachments

/**
 * A note as the editor holds it: its segments, and a live buffer for each editable one.
 *
 * The note is cut at its image lines (see [Segments]) so pictures can be drawn between text fields,
 * a text field being unable to contain a composable. 165 of the archive's 168 notes have no images
 * and therefore exactly one buffer, which is the same thing the editor had before this existed.
 *
 * [body] reassembles the note. Segments that are not text hand back their original bytes untouched,
 * so an image line survives being scrolled past exactly as it was written.
 */
class NoteDocument(body: String, tags: List<String>) {
    val segments: List<Segment> = Segments.split(body)

    /**
     * The tags the chip sheet edits, seeded from the frontmatter.
     *
     * Frontmatter and not the union of both representations — see `Note.editableTags`. A tag that
     * lives only in a body is chipped from its own run and is deliberately not in here, so no save
     * can delete it or promote it into a `tags:` list the author never wrote.
     */
    var tags: List<String> by mutableStateOf(tags)
        private set

    /** Replace the editable set, folding new input to lowercase as `Tags.normalize` requires. */
    fun retag(newTags: List<String>) {
        tags = newTags.map(Tags::normalize).distinct()
    }

    /**
     * Every tag to draw at the foot: the editable ones first, then anything the runs carry that the
     * frontmatter does not.
     *
     * The second group is empty on every note in the archive and exists for the note edited
     * elsewhere and left disagreeing with itself. It is shown because the author can see it in their
     * own text, and is not removable because the app may not promote a body-only tag into a `tags:`
     * list on their behalf.
     */
    fun chips(): List<Chip> {
        val fromRuns = segments.filterIsInstance<Segment.Tags>().flatMap { it.tags }
        val extra = fromRuns.distinct().filterNot { it in tags }
        return tags.map { Chip(it, removable = true) } + extra.map { Chip(it, removable = false) }
    }

    private val buffers: Map<Int, TextFieldState> = segments.withIndex()
        .filter { it.value is Segment.Prose }
        .associate { (i, segment) -> i to TextFieldState(segment.raw) }

    fun bufferAt(index: Int): TextFieldState = buffers.getValue(index)

    /** The whole note again, as bytes to be written. */
    fun body(): String = segments.withIndex().joinToString("") { (i, segment) ->
        if (segment is Segment.Prose) buffers.getValue(i).text.toString() else segment.raw
    }

    /** One tag as the foot of the note draws it. */
    data class Chip(val tag: String, val removable: Boolean)

    /** Where the cursor is, for the format bar — the first buffer that has a selection. */
    fun anySelection(): TextFieldState? = buffers.values.firstOrNull { !it.selection.collapsed }
}

/**
 * A note, open for writing.
 *
 * **The design is what is absent.** No toolbar, no formatting controls, no word count, no mode
 * switch — tapping a note in the list puts the cursor in it and the page is the writing. The only
 * chrome is a back arrow and the title. Formatting appears when text is selected and goes away
 * again, which is the one moment it is wanted.
 *
 * The Markdown is invisible: see [MarkdownTransformation], which hides the markers and brings them
 * back under the cursor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    title: String,
    document: NoteDocument,
    attachments: Attachments,
    links: List<LinkRef>,
    missingLinks: Set<String>,
    onOpenLink: (LinkRef) -> Unit,
    editable: Boolean,
    onBack: () -> Unit,
    message: String?,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.save_failed, message.orEmpty())
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(failed)
            onMessageShown()
        }
    }
    val scheme = MaterialTheme.colorScheme
    val transformation = remember(scheme) {
        MarkdownTransformation(
            // A revealed marker is dimmer than the words around it, so `**` reads as scaffolding
            // rather than as something the author typed on purpose.
            marker = scheme.onSurfaceVariant.copy(alpha = 0.55f),
            code = scheme.onSurfaceVariant,
        )
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    // No style override: the bar's own titleLarge, which is what a screen title is
                    // meant to look like. Shrinking it to titleMedium made the note read as a
                    // subordinate detail rather than as the thing being edited.
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.editor_back),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface),
            // The editor's bar is the page's own colour, so it needs no ground of its own — but it
            // still takes the top inset, MainActivity having left it alone.
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        )

        if (!editable) {
            ReadOnlyNotice()
        }

        // One scroller for the whole note, with the text fields inside it rather than each
        // scrolling on its own — a note is one page, and images have to move with the words around
        // them.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            document.segments.forEachIndexed { i, segment ->
                when (segment) {
                    is Segment.Prose -> BasicTextField(
                        state = document.bufferAt(i),
                        enabled = editable,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        outputTransformation = transformation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                    )

                    is Segment.Images -> NoteImages(segment, attachments)

                    // Drawn nowhere here: the note's tags are gathered into one chip row at the
                    // foot, so a run in the middle of a note does not interrupt the writing with a
                    // second copy of what the foot already shows.
                    is Segment.Tags -> Unit
                }
            }
        }

        AttachmentStrip(links = links, missing = missingLinks, onOpen = onOpenLink)

        SnackbarHost(snackbar)

        // Formatting exists only while something is selected. A collapsed cursor is someone
        // writing; a selection is someone looking at a piece of text and considering it.
        val selected = document.anySelection()
        AnimatedVisibility(
            visible = editable && selected != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            FormatBar(selected ?: document.bufferAt(0))
        }
    }
}

/**
 * The bar over a selection.
 *
 * Verbs the audience already owns — Bold, Italic, Heading — and no others. Every additional control
 * here is a control on the one screen that is supposed to have none, so the bar earns its place only
 * by being the shortest possible list.
 */
@Composable
private fun FormatBar(body: TextFieldState, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().imePadding(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            FormatButton(stringResource(R.string.format_bold)) { body.wrapSelection("**") }
            FormatButton(stringResource(R.string.format_italic)) { body.wrapSelection("*") }
            FormatButton(stringResource(R.string.format_heading)) { body.prefixLine("## ") }
        }
    }
}

@Composable
private fun FormatButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, shape = ControlShape) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Shown above a note the app will not risk writing. See `IndexedNote.roundTrips`. */
@Composable
private fun ReadOnlyNotice() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.editor_readonly_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.editor_readonly_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** Applies [FormatActions.wrap] to this buffer, keeping the selection on the words. */
private fun TextFieldState.wrapSelection(marker: String) {
    val range = selection
    if (range.collapsed) return
    val result = FormatActions.wrap(text.toString(), range.min, range.max, marker)
    edit {
        replace(0, length, result.text)
        selection = TextRange(result.selectionStart, result.selectionEnd)
    }
}

/** Applies [FormatActions.prefixLine] to the line the selection starts on. */
private fun TextFieldState.prefixLine(prefix: String) {
    val result = FormatActions.prefixLine(text.toString(), selection.min, prefix)
    edit {
        replace(0, length, result.text)
        selection = TextRange(result.selectionStart, result.selectionEnd)
    }
}

/**
 * What to do when a note changed on another device while it was open here.
 *
 * Two choices and no third. Overwriting is not offered because `CLAUDE.md` forbids it, and merging
 * is not offered because an app that merges two versions of a lyric silently invents a third. Keep
 * both is the safe default and is listed first; discarding is destructive and says so plainly.
 *
 * Not dismissible by tapping outside: the decision is about the user's own writing, and dismissing
 * it by accident would leave them in an editor whose contents cannot be saved.
 */
@Composable
fun ConflictDialog(onKeepBoth: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = stringResource(R.string.conflict_title)) },
        text = { Text(text = stringResource(R.string.conflict_body)) },
        confirmButton = {
            TextButton(onClick = onKeepBoth) { Text(text = stringResource(R.string.conflict_keep_both)) }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text(text = stringResource(R.string.conflict_discard)) }
        },
    )
}
