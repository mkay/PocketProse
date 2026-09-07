// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Drawn at their natural size rather than stretched. A chord diagram is 151×164 and was drawn to be
 * read at that size; scaling it to the width of a phone would make four of them into a wall.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteImages(segment: Segment.Images, attachments: Attachments, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        segment.images.forEach { NoteImage(it, attachments) }
        // The one line in the archive that carries words beside its pictures reads "3x". It is kept
        // because it is part of the notation, and it is not editable here — a picture line is a
        // block, and cutting one in half to make three characters typable is not worth it.
        if (segment.trailing.isNotEmpty()) {
            Text(
                text = segment.trailing,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
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
        image != null -> Surface(
            color = ImagePaper,
            shape = RoundedCornerShape(4.dp),
        ) {
            Image(
                bitmap = image,
                contentDescription = ref.alt.ifBlank { stringResource(R.string.image_untitled) },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(4.dp)
                    .size(width = image.width.dp / 2, height = image.height.dp / 2),
            )
        }

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
