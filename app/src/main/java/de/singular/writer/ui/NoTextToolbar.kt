// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * The selection menus, switched off, so the editor's own bar is the only one.
 *
 * Android's floating popup lands **on top of the words being selected**, which in a lyric is the one
 * place it must not be: you select a line in order to do something to it, and the popup covers the
 * line. It also arrives with its own vocabulary beside a bar this app has already designed for the
 * job.
 *
 * ## Two mechanisms, because Compose has two
 *
 * [NoTextToolbar] replaces `LocalTextToolbar`, which is the older route and still what
 * `SelectionContainer` and the like use.
 *
 * [NoTextContextMenu] replaces the newer one. Foundation 1.9 introduced `text/contextmenu`, and a
 * `BasicTextField` now asks `LocalTextContextMenuToolbarProvider` rather than the text toolbar — so
 * overriding the toolbar alone changed nothing at all, and the popup kept appearing. Both are
 * replaced, and both should stay: which one a given component uses is Compose's business and has
 * already changed once.
 *
 * `showTextContextMenu` is a suspend function that returns when the menu closes. Returning at once
 * without showing anything is how you decline to show one.
 *
 * What is *not* switched off: the selection handles, dragging them, and the accessibility actions.
 * Only the menu goes. **What goes with it is paste-at-a-plain-cursor**, which was the popup's job
 * when nothing was selected — which is why the editor's bar appears on focus rather than on
 * selection. See `FormatBar`.
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

/** The newer route, for text fields. See [NoTextToolbar]. */
@OptIn(ExperimentalFoundationApi::class)
object NoTextContextMenu : TextContextMenuProvider {
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) = Unit
}
