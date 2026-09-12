// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

import android.net.Uri

/**
 * What happened when the user renamed one note's file.
 *
 * Separate from [RenameResult], which is the tag rename: that one rewrites the insides of up to 131
 * files and needs to be able to say how far it got. This touches one file and does not open it, so
 * the outcomes are far simpler — but they are not "worked" and "didn't", because the interesting
 * failure is a name already in use, and the archive is full of names that nearly collide.
 */
sealed interface RenameNoteResult {

    /** The file now has the new name, at [uri] — a new document id, as after any SAF rename. */
    data class Renamed(val uri: Uri, val name: String) : RenameNoteResult

    /**
     * The new name is the old name, so nothing was done.
     *
     * Not an error. The dialog refuses an unchanged name before it gets here, but the two names go
     * through [Vault.fileStem] on the way and can arrive equal — typing a `?` on the end of a name
     * that already exists is the case, since the filename cannot hold it either way.
     */
    data object Unchanged : RenameNoteResult

    /**
     * A note of that name is already in the folder.
     *
     * **Refused rather than numbered.** `Vault.create` answers a collision by counting up —
     * `Atlantik 2.md` beside `Atlantik.md`, which is what the archive already does by hand — and
     * that is right for a name the app chose out of a title. It is wrong here: the user typed this
     * name, and quietly handing back a different one would be the app renaming their file to
     * something they did not ask for. So they are told, and they choose.
     */
    data object Taken : RenameNoteResult

    /** The provider would not do it, or would not say what it did — [reason] says which. */
    data class Failed(val reason: Failure) : RenameNoteResult
}
