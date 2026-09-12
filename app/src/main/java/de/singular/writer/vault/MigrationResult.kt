// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

/**
 * What happened when the app moved a folder's tags and titles out of its text and into its notes.
 *
 * The second edit in this app that writes many files at once, and the larger of the two: a rename
 * touches the notes carrying one tag, this touches every note carrying any. It shares [RenameResult]'s
 * shape for the same reason — "it worked" and "it failed" are not the only two answers — and differs
 * in one place, [Partial], because this operation is *not* idempotent in the way a rename is. See
 * [Vault.migrateInline].
 */
sealed interface MigrationResult {

    /** No note had a tag or a title written in its body, so there was nothing to move. */
    data object NothingToMove : MigrationResult

    /**
     * Every affected note was rewritten.
     *
     * [notes] is files, [tags] is how many tags the drawer gained — usually far fewer, and on an
     * archive whose frontmatter already agreed with its bodies, zero. [titles] is how many notes
     * were given the title they were already showing at the top of their text.
     */
    data class Moved(val notes: Int, val tags: Int, val titles: Int) : MigrationResult

    /**
     * Nothing was written: the precheck found the folder had moved underneath us.
     *
     * A sync landing mid-migration, and not something the user did. [notes] names the files that had
     * changed since they were read, so the message can say which. The archive is untouched;
     * refreshing and offering again is the whole recovery.
     */
    data class Stale(val notes: List<String>) : MigrationResult

    /**
     * Nothing was written, because a note that would have had to be rewritten is one the app cannot
     * reproduce byte for byte.
     *
     * The same gate as `SaveResult.Refused`, applied to the batch. It matters more here than
     * anywhere else in the app: this runs on a folder the app has just met, so an unfamiliar note
     * shape is likelier than it ever is later on, and the first thing a new user must not experience
     * is a mangled note. [notes] names them so they can be looked at.
     */
    data class Refused(val notes: List<String>) : MigrationResult

    /**
     * The writes began and did not finish.
     *
     * **The one place this differs from a tag rename in kind.** A rename is idempotent, so its
     * recovery is "run it again". This one is too — a note already migrated has no tag lines left,
     * so the second pass skips it — but the *offer* around it must not simply reappear as if nothing
     * had happened, because a folder that is half moved is a folder where some notes show their tags
     * as chips and others still show them as text. [migrated] and [remaining] say exactly where it
     * stopped, so the message can be about finishing rather than about starting.
     */
    data class Partial(val migrated: Int, val remaining: List<String>, val reason: Failure) : MigrationResult
}
