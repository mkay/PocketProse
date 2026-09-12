// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

/**
 * What happened when the app tried to rename a tag across the folder.
 *
 * Renaming is the one edit that touches many files at once — `lyrics/snippet` is on 131 of the
 * archive's 168 notes — and this type exists because "it worked" and "it failed" are not the only
 * two answers such an edit can have. See [Vault.renameTag].
 */
sealed interface RenameResult {

    /** No note carried the tag, so there was nothing to do. */
    data object NoSuchTag : RenameResult

    /**
     * Every affected note was rewritten.
     *
     * [count] is notes, not tags: a note carrying both `album` and `album/debut` is one note, and it
     * was written once.
     */
    data class Renamed(val count: Int) : RenameResult

    /**
     * Nothing was written, because the precheck found the folder had moved underneath us.
     *
     * The ordinary outcome of a sync landing mid-rename, and not an error the user did anything to
     * cause. [notes] names the files that had changed since they were read, so the message can say
     * which — one file name is a fact a person can act on, where "something changed" is not.
     *
     * The archive is untouched. Refreshing and running the rename again is the whole recovery.
     */
    data class Stale(val notes: List<String>) : RenameResult

    /**
     * The app declined before writing anything, because a note it would have had to rewrite is one
     * it cannot reproduce byte for byte.
     *
     * The same gate as `SaveResult.Refused`, applied to the whole batch: a parser that misunderstood
     * a note would corrupt it on the way out, and a rename must not be the thing that discovers
     * that. [notes] names them so the user can look.
     */
    data class Refused(val notes: List<String>) : RenameResult

    /**
     * The writes began and did not finish. **This is the case the design exists to make rare.**
     *
     * The precheck cannot close the window entirely: a sync client can land a file between the check
     * and the write, and SAF has no transaction to roll the earlier writes back with. So this says
     * exactly how far it got — [renamed] notes now carry the new name, [remaining] still carry the
     * old one — rather than pretending the folder is in one state or the other.
     *
     * **Running the rename again finishes the job.** A tag rename is idempotent: the notes already
     * renamed no longer carry the old tag, so they are not in the next batch, and `Note.withTags`
     * writes nothing for a note whose tags already say what they should. That is why the recovery
     * offered to the user is "try again" rather than anything cleverer.
     */
    data class Partial(val renamed: Int, val remaining: List<String>, val reason: Failure) : RenameResult
}
