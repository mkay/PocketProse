// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * A text toolbar that never appears, so the editor's own bar is the only one.
 *
 * Android's floating selection popup lands **on top of the words being selected**, which in a lyric
 * is the one place it must not be: you select a line to do something to it, and the popup covers the
 * line. It also arrives with its own vocabulary and its own idea of what a text field is for, beside
 * a bar this app has already designed for the purpose.
 *
 * Suppressing it is a composition-local swap rather than a flag, because that is all Compose offers
 * and all it needs: the selection handles, the drag behaviour and the accessibility actions are
 * untouched. Only the menu goes.
 *
 * **What goes with it is paste-at-a-plain-cursor**, which was the popup's job when nothing was
 * selected. That is why the editor's bar appears on focus rather than on selection — see the note
 * on `FormatBar`. Removing the popup without moving that job somewhere would have quietly taken
 * pasting away.
 */
object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun hide() = Unit

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit
}
