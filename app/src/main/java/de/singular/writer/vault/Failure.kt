// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

/**
 * Why a write did not happen, for the `Failed` and `Partial` results to carry.
 *
 * A reason rather than a message, for the same cause as [VaultFailure]: the vault has no `Context`
 * and should not grow one, so it cannot look a string up — and a sentence typed here would arrive
 * in the snackbar in English whatever language the rest of the screen is in. That is exactly what
 * happened until 0.2: `save_failed` was translated, and the fragment it was wrapped around was not.
 * The words for each of these live in `strings.xml` and `MainActivity.describe` chooses them.
 *
 * Two carry a detail the sentence needs. [RenamedDifferently] has the name the provider chose,
 * because "your folder named it X instead" is the whole point of the message; [ZipNotWritten] has
 * the exception's own text when there is one, since an export that dies on a full card should say
 * so rather than shrug.
 */
sealed interface Failure {

    /** The folder the user chose cannot be opened any more. Every write checks this first. */
    data object FolderUnreachable : Failure

    /** The folder opened but would not list its files. */
    data object FolderUnlistable : Failure

    /** The note could not be read back before the write, so the change was not applied. */
    data object NoteUnreadable : Failure

    /** The provider would not make the temporary file the atomic write starts with. */
    data object TempFileNotMade : Failure

    /** Fewer bytes landed than were sent. The temp file was removed; the note is untouched. */
    data object WriteIncomplete : Failure

    /** The temp file was written but could not take the note's place. */
    data object NotPutInPlace : Failure

    /** A new note or a merge was asked for with an empty title. */
    data object TitleMissing : Failure

    /** A merge was asked for with fewer than two notes. */
    data object MergeNeedsTwo : Failure

    /** The provider would not create the new file. */
    data object NoteNotMade : Failure

    /** The new file was created but could not be written. */
    data object NoteNotWritten : Failure

    /** The provider would not rename the file, or would not say what it did. */
    data object NoteNotRenamed : Failure

    /** Renamed, but to [actual] rather than the name asked for — see [Vault.renameNote]. */
    data class RenamedDifferently(val actual: String) : Failure

    /** The export died part way; the truncated zip was removed. [detail] is the exception's text. */
    data class ZipNotWritten(val detail: String?) : Failure

    /** The keep-both copy could not be created. */
    data object CopyNotMade : Failure

    /** The keep-both copy was created but not written in full. */
    data object CopyIncomplete : Failure

    /** A batch stopped because a note changed underneath it while it was running. */
    data object BatchConflict : Failure

    /** A batch stopped at a note the parser could not reproduce byte for byte. */
    data object BatchRefused : Failure

    /** A batch stopped for a reason its save did not name. Should not happen; kept for the `else`. */
    data object BatchStopped : Failure
}
