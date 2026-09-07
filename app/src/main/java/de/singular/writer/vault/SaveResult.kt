// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

import android.net.Uri

/** What happened when the app tried to save a note. */
sealed interface SaveResult {

    /**
     * Nothing was written, because nothing had changed.
     *
     * The most common outcome by far, and the one the app is built around: opening a note, reading
     * it and leaving must not touch the file. See `Note.withBody`.
     */
    data object Unchanged : SaveResult

    /** Written. [uri] may differ from the old one — see the rename in [Vault.save]. */
    data class Saved(val uri: Uri, val text: String, val hash: String) : SaveResult

    /**
     * The file changed underneath us since it was read, so nothing was written.
     *
     * A sync client owns this folder, so this is an ordinary event rather than an error: the same note
     * was edited on a laptop while it sat open on the phone. `CLAUDE.md` is explicit that the app
     * must never overwrite silently and never auto-merge — both versions are kept and the user
     * chooses. [onDisk] is what the file says now; the caller still holds what the user typed.
     */
    data class Conflict(val onDisk: String) : SaveResult

    /**
     * The app declined to write, because it could not reproduce this note's bytes when it read them.
     *
     * A parser that misunderstood a note on the way in would corrupt it on the way out. Refusing is
     * the only honest answer. See `IndexedNote.roundTrips`.
     */
    data object Refused : SaveResult

    /** The write failed. The note on disk is unchanged. */
    data class Failed(val reason: String) : SaveResult
}
