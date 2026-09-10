// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

/**
 * What happened when the user deleted a selection of notes.
 *
 * A single delete answers with a boolean and that is enough: one file, gone or not. A selection is a
 * different shape of question, because **it can stop half way**. SAF has no transaction to roll the
 * earlier deletes back with, and a sync client can take a file between one call and the next, so a
 * batch of five can genuinely end with three gone and two still there. A boolean would have to call
 * that either a success or a failure, and the folder is in neither state.
 *
 * The same reasoning, and the same shape, as [RenameResult] — which learned it first, over a rename
 * that touches 131 files.
 */
sealed interface DeleteResult {

    /** Every selected note is gone. [count] is how many. */
    data class Deleted(val count: Int) : DeleteResult

    /**
     * Some went and some did not.
     *
     * [deleted] is how many are gone; [remaining] names the files that are still in the folder, so
     * the message can say which one to go and look at. A name is a fact somebody can act on where
     * "some notes could not be deleted" is not.
     *
     * **Running it again finishes the job**, the notes already gone no longer being in the folder to
     * select — which is why the recovery offered is "try again" rather than anything cleverer.
     */
    data class Partial(val deleted: Int, val remaining: List<String>) : DeleteResult

    /** Nothing was attempted, because the folder itself is not reachable. */
    data class Failed(val reason: String) : DeleteResult
}
