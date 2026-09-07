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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.singular.writer.R

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
    body: TextFieldState,
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
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
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
        )

        if (!editable) {
            ReadOnlyNotice()
        }

        Box(Modifier.weight(1f)) {
            BasicTextField(
                state = body,
                enabled = editable,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                outputTransformation = transformation,
                scrollState = rememberScrollState(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        SnackbarHost(snackbar)

        // Formatting exists only while something is selected. A collapsed cursor is someone
        // writing; a selection is someone looking at a piece of text and considering it.
        val selection = body.selection
        AnimatedVisibility(
            visible = editable && !selection.collapsed,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            FormatBar(body)
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

/**
 * Put [marker] on both sides of the selection, or take it off again if it is already there.
 *
 * Toggling matters more than it looks: without it the Bold button is a one-way door, and the only
 * way back is to find and delete asterisks that the editor is deliberately hiding.
 */
private fun TextFieldState.wrapSelection(marker: String) {
    val range = selection
    if (range.collapsed) return
    val text = this.text
    val start = range.min
    val end = range.max
    val already = start >= marker.length &&
        end + marker.length <= text.length &&
        text.substring(start - marker.length, start) == marker &&
        text.substring(end, end + marker.length) == marker
    edit {
        if (already) {
            replace(end, end + marker.length, "")
            replace(start - marker.length, start, "")
            selection = androidx.compose.ui.text.TextRange(start - marker.length, end - marker.length)
        } else {
            replace(end, end, marker)
            replace(start, start, marker)
            selection = androidx.compose.ui.text.TextRange(start + marker.length, end + marker.length)
        }
    }
}

/** Put [prefix] at the start of the line the selection begins on, or take it off again. */
private fun TextFieldState.prefixLine(prefix: String) {
    val text = this.text
    val lineStart = text.lastIndexOf('\n', (selection.min - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else it + 1 }
    val already = text.startsWith(prefix, lineStart)
    edit {
        if (already) replace(lineStart, lineStart + prefix.length, "")
        else replace(lineStart, lineStart, prefix)
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
