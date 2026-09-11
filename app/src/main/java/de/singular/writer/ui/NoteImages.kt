// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import de.singular.writer.markdown.ImageRef
import de.singular.writer.markdown.Segment
import de.singular.writer.vault.Attachments

/**
 * The pictures on one line of a note.
 *
 * They come in runs — the archive's chord sheets put three or four diagrams side by side to spell
 * out a progression — so they are laid out in a flow that keeps that reading order and wraps only
 * when the phone is too narrow. Their order is the meaning; a grid that reflowed them arbitrarily
 * would be wrong.
 *
 * Drawn at their own size, never wider than the column. A chord diagram is 151×164 and was drawn
 * to be read at that size; scaling it to the width of a phone would make four of them into a wall.
 *
 * **A picture is removed by long-pressing it.** It sits between the text fields rather than in one,
 * so no cursor can reach it and no backspace can take it out; without this a picture put in by
 * mistake would be in the note for good. [onRemove] is null when the note cannot be written, and
 * the press then does nothing. Only the link leaves the note — see [Segments.withoutImage].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteImages(
    segment: Segment.Images,
    attachments: Attachments,
    onRemove: ((ImageRef) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        segment.images.forEach { ref ->
            Removable(onRemove?.let { remove -> { remove(ref) } }) { NoteImage(ref, attachments) }
        }
        // The one line in the archive that carries words beside its pictures reads "3x". It is kept
        // because it is part of the notation, and it is not editable here — a picture line is a
        // block, and cutting one in half to make three characters typable is not worth it.
        if (segment.trailing.isNotEmpty()) {
            Text(
                text = segment.trailing,
                style = LocalProseStyle.current,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Wraps a picture in a long-press that offers to take it out of the note.
 *
 * A menu rather than an immediate removal: the press is the same gesture that selects text
 * everywhere else on the page, and a picture vanishing under a thumb that meant to scroll is the
 * kind of surprise the editor exists to avoid. One item, and the note's own undo is the save it
 * has not made yet — nothing here reaches disk until the document is written.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Removable(onRemove: (() -> Unit)?, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier = if (onRemove == null) Modifier else Modifier.combinedClickable(
            onClick = {},
            onLongClick = { open = true },
        ),
    ) {
        content()
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove_image)) },
                onClick = {
                    open = false
                    onRemove?.invoke()
                },
            )
        }
    }
}

/** One picture, or a plain statement that it is not there. */
@Composable
private fun NoteImage(ref: ImageRef, attachments: Attachments) {
    var bitmap by remember(ref.path) { mutableStateOf<ImageBitmap?>(null) }
    var missing by remember(ref.path) { mutableStateOf(false) }

    LaunchedEffect(ref.path) {
        val uri = attachments.resolve(ref.path)
        if (uri == null) {
            missing = true
        } else {
            bitmap = attachments.bitmap(uri)
            missing = bitmap == null
        }
    }

    val image = bitmap
    when {
        // No frame and no size of the app's own. The picture is its pixels, scaled down to fit
        // the column when it is wider, and nothing else — a phone photo fills the width, a small
        // drawing stays small. A half-scale box was tried and turned every photo into a tall
        // letterbox with the picture at the bottom; a mount behind it went with the box.
        image != null -> Image(
            bitmap = image,
            contentDescription = ref.alt.ifBlank { stringResource(R.string.image_untitled) },
            contentScale = ContentScale.Fit,
        )

        missing -> MissingImage(ref.path)
        // Nothing while it loads. A spinner for a file already on the phone is a flicker, not
        // feedback.
        else -> Unit
    }
}

/**
 * A picture the folder does not contain.
 *
 * Says so, quietly, and shows the path that was asked for. The archive has 24 of these: the three
 * duplicate `Wer geht vor` notes point into a folder the export referred to and never created.
 * The app never guesses which file was meant and never rewrites the link — a dead link the writer
 * can see is better than a silently altered one.
 */
@Composable
private fun MissingImage(path: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = stringResource(R.string.image_missing, Attachments.decode(path)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}
