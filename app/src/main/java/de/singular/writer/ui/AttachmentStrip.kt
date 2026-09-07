// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import de.singular.writer.markdown.LinkRef

/**
 * The files a note links to, gathered at its foot.
 *
 * `CLAUDE.md` requires the seven PDFs in `Wer geht vor.md` to open in an external viewer, and this
 * is how. A word on why it is a strip rather than tappable text in the note itself: the editor is a
 * text field that is always live, so a tap in it means "put the cursor here". Making a tap sometimes
 * mean "put the cursor here" and sometimes "leave the app and open a PDF" would be a coin toss
 * played while writing. Gathering the links where they can be tapped unambiguously keeps both
 * behaviours honest, and it also puts a note's attachments in one place instead of scattered through
 * it.
 *
 * The links stay exactly as written in the text — this adds an affordance, not a rewrite.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttachmentStrip(links: List<LinkRef>, missing: Set<String>, onOpen: (LinkRef) -> Unit) {
    if (links.isEmpty()) return
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.attachments_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            links.forEach { link ->
                AttachmentChip(link, gone = link.target in missing, onOpen = { onOpen(link) })
            }
        }
    }
}

@Composable
private fun AttachmentChip(link: LinkRef, gone: Boolean, onOpen: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(4.dp),
        modifier = if (gone) Modifier else Modifier.clickable(onClick = onOpen),
    ) {
        Text(
            // A missing file still shows, and says so. 24 of the archive's links point into a folder
            // that does not exist; hiding them would be tidier and would also be a lie about what
            // the note says.
            text = if (gone) {
                link.display + " · " + stringResource(R.string.attachment_missing)
            } else {
                link.display
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}
