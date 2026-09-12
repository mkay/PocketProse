// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

import android.net.Uri

/** What happened when the app tried to make a new note. */
sealed interface CreateResult {

    /** Made. [uri] is the new file and [text] the bytes it holds. */
    data class Made(val uri: Uri, val text: String) : CreateResult

    /** Nothing was created, and nothing was left behind. */
    data class Failed(val reason: Failure) : CreateResult
}
