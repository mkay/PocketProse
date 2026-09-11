// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.awaitCancellation

/**
 * The app's own selection menu, in place of Android's.
 *
 * Android's floating toolbar lands **on top of the words being selected**, which in a lyric is the
 * one place it must not be, and it wears the system's colours beside a bar this app has already
 * designed. It was switched off for that (see [NoTextToolbar]); this is what goes in its place: a
 * small strip of the format bar's own buttons, **under** the selection, in the app's colours.
 *
 * ## How the field talks to it
 *
 * A `BasicTextField` asks `LocalTextContextMenuToolbarProvider` to show a menu whenever it would
 * have shown Android's — a selection made, a handle released — and cancels that call when the menu
 * should go: the selection collapsing, the field losing focus. [showTextContextMenu] is that call.
 * It records the request and suspends until cancelled; the composable [SelectionPopup] reads the
 * request and draws. The data provider carries where the selection is ([TextContextMenuDataProvider.contentBounds]),
 * which is the one thing the popup needs from it — the menu *items* it also offers are Android's
 * cut / copy / paste, and the popup has its own of those, the same as the bar's.
 *
 * Nothing is drawn while a request is not open, so the field, not this file, decides when a
 * selection is a selection worth a menu.
 */
@OptIn(ExperimentalFoundationApi::class)
class SelectionPopupProvider : TextContextMenuProvider {

    /** The open request, or null while the field wants no menu. */
    var request: TextContextMenuDataProvider? by mutableStateOf(null)
        private set

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        request = dataProvider
        try {
            awaitCancellation()
        } finally {
            request = null
        }
    }
}

/**
 * Draws [content] in a popup under the selection the field reported, or above it when there is no
 * room below.
 *
 * [anchor] is the layout the popup is placed in — the scrolling page — and every position is
 * relative to it, so the popup stays inside the page rather than wandering over the bar or the
 * keyboard. Centred on the selection horizontally and kept within the page's width.
 *
 * Not focusable, so a tap on a button leaves the field's focus and selection exactly where they
 * were — the buttons act on the selection, and taking it away would be self-defeating.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectionPopup(
    provider: SelectionPopupProvider,
    anchor: LayoutCoordinates?,
    content: @Composable () -> Unit,
) {
    val request = provider.request ?: return
    if (anchor == null || !anchor.isAttached) return
    val selection = request.contentBounds(anchor)
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }

    Popup(popupPositionProvider = BelowSelection(selection, gap)) {
        content()
    }
}

/**
 * Under the selection, centred; above it when the page has no room underneath; never outside the
 * page horizontally.
 *
 * [selection] is in the page's coordinates and [anchorBounds] is the page in the window's, so the
 * sum is the selection in the window's — the space a popup is positioned in.
 */
private class BelowSelection(private val selection: Rect, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centre = anchorBounds.left + selection.center.x.toInt()
        val x = (centre - popupContentSize.width / 2)
            .coerceIn(anchorBounds.left, (anchorBounds.right - popupContentSize.width).coerceAtLeast(anchorBounds.left))

        val below = anchorBounds.top + selection.bottom.toInt() + gap
        val above = anchorBounds.top + selection.top.toInt() - gap - popupContentSize.height
        val y = when {
            below + popupContentSize.height <= anchorBounds.bottom -> below
            above >= anchorBounds.top -> above
            else -> below
        }
        return IntOffset(x, y)
    }
}
