// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import de.singular.writer.markdown.Note
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.time.Instant

/**
 * One file in the folder, as the listing sees it — before anything has been read out of it.
 *
 * [name] is the display name including the extension. It is **not** the note's title and must never
 * be used as one: the archive has four different files titled "Wer geht vor?", eleven titles ending
 * in a question mark that no filename can contain, and a note whose filename ends in a double dot.
 * Titles come out of the file, in phase 1.
 */
data class NoteFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

/** What one listing of the folder found. */
data class VaultListing(
    val files: List<NoteFile> = emptyList(),
    val error: VaultFailure? = null,
)

/**
 * The user's notes folder, reached through the Storage Access Framework.
 *
 * A tree grant, not a path. The user points at a folder once — a synced Nextcloud folder, a folder
 * on an SD card, anywhere; the app has no opinion and never proposes a location — and the grant is
 * persisted so it survives reboots. In return we work in document ids rather than file paths, which
 * is why folders are passed around as `content://` *document* uris built against the tree.
 *
 * There is no import step and no internal copy. This class is the only way the app ever reaches a
 * note, which is what keeps the prime directive in `CLAUDE.md` enforceable rather than aspirational:
 * if the files are the database, then nothing may sit between them and the screen.
 *
 * Every call here touches a ContentProvider: they already move themselves to [Dispatchers.IO].
 */
class Vault(context: Context) {

    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The granted root, or null until the user has picked one — or if the grant has been revoked. */
    val root: Uri?
        get() = prefs.getString(KEY_ROOT, null)
            ?.let(Uri::parse)
            ?.takeIf { hasGrant(it) }

    /**
     * Whether a folder was ever picked, grant or no grant. Together with [root] being null this
     * says the folder is *gone* rather than never chosen — see [VaultFailure], where those are two
     * different things to tell the user.
     */
    val rootWasSet: Boolean
        get() = prefs.getString(KEY_ROOT, null) != null

    /** The root as a document uri — what [list] wants. */
    fun rootFolder(): Uri? = root?.let { documentUri(it, DocumentsContract.getTreeDocumentId(it)) }

    /**
     * Remember the tree the user just granted, and hold onto the grant across restarts. Returns
     * false if the system refused to persist it, in which case the pick has to be repeated.
     */
    fun setRoot(uri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val ok = runCatching { resolver.takePersistableUriPermission(uri, flags) }.isSuccess
        // The cached name belongs to the old folder; drop it in the same edit so the two can never
        // disagree, and let the next listing fill it in again.
        if (ok) prefs.edit().putString(KEY_ROOT, uri.toString()).remove(KEY_ROOT_NAME).apply()
        return ok
    }

    /**
     * The chosen folder's name as it was last read, available **without touching a provider**.
     *
     * Read straight from preferences so the very first frame can show the right title. Without it
     * the header says "Notes" for as long as the first listing takes and then changes under the
     * reader's eyes, which is a small thing that makes an app feel like it is thinking rather than
     * like it is open.
     */
    val cachedRootName: String?
        get() = if (rootWasSet) prefs.getString(KEY_ROOT_NAME, null) else null

    /** Human-readable name of the chosen folder, and caches it for [cachedRootName]. */
    suspend fun rootName(): String? = withContext(Dispatchers.IO) {
        val folder = rootFolder() ?: return@withContext null
        queryOne(folder, DocumentsContract.Document.COLUMN_DISPLAY_NAME)?.also {
            prefs.edit().putString(KEY_ROOT_NAME, it).apply()
        }
    }

    /** Whether we still hold a persisted grant on [uri] — the user can revoke it in Settings. */
    private fun hasGrant(uri: Uri): Boolean =
        resolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }

    /**
     * Every note in the chosen folder, sorted by name.
     *
     * Flat: sub-folders are listed past, not descended into. The archive keeps its images in an
     * `attachments/` folder beside the notes and has no other subfolder, and a note is a note
     * wherever it sits — so a folder here is a place attachments live, not a category. Phase 5
     * reaches into `attachments/` by name when a note asks for an image; nothing else needs to.
     *
     * Sorted by name only, and deliberately not by date. `modifiedAt` is the *file's* mtime, which
     * Nextcloud rewrites on sync — ordering the library by it would reshuffle the list every time
     * the phone came back online. The dates the user cares about live in the notes themselves and
     * arrive in phase 1; until then, alphabetical is the only order that is stable.
     */
    suspend fun list(): VaultListing = withContext(Dispatchers.IO) {
        val tree = root ?: return@withContext VaultListing(
            error = if (rootWasSet) VaultFailure.FOLDER_UNREACHABLE else VaultFailure.NO_FOLDER_CHOSEN,
        )
        val folder = rootFolder() ?: return@withContext VaultListing(error = VaultFailure.FOLDER_UNREACHABLE)
        val children = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getDocumentId(folder),
            )
        }.getOrNull() ?: return@withContext VaultListing(error = VaultFailure.FOLDER_UNREACHABLE)

        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val cursor = runCatching { resolver.query(children, columns, null, null, null) }.getOrNull()
            ?: return@withContext VaultListing(error = VaultFailure.FOLDER_UNREADABLE)

        val files = ArrayList<NoteFile>()
        val temps = ArrayList<NoteFile>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                val name = it.getString(1) ?: continue
                val mime = it.getString(2) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                if (name.endsWith(TEMP_SUFFIX)) {
                    temps += NoteFile(documentUri(tree, id), name, 0, 0)
                    continue
                }
                if (!isNote(name)) continue
                files += NoteFile(
                    uri = documentUri(tree, id),
                    name = name,
                    sizeBytes = if (it.isNull(3)) 0L else it.getLong(3),
                    modifiedAt = if (it.isNull(4)) 0L else it.getLong(4),
                )
            }
        }
        // Anything left over from an interrupted write is dealt with before the listing is
        // returned, so a recovered note appears in the very listing that found the temp file.
        if (temps.isNotEmpty()) {
            recoverTemp(tree, temps, files.map { it.name }.toSet())
            return@withContext list()
        }
        VaultListing(
            files = files.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { f -> f.name }),
            error = if (files.isEmpty()) VaultFailure.FOLDER_EMPTY else null,
        )
    }

    /**
     * The text of one note, or null if it cannot be read.
     *
     * UTF-8, and the bytes are taken exactly as they are — no line-ending translation, no trailing
     * newline added or removed, no BOM stripped. Whatever comes back here is what [readAll] hands
     * to the parser and what the app must be able to write again unchanged.
     */
    suspend fun read(uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
    }

    /**
     * Every note in the folder, read and parsed.
     *
     * Reads all 168 files on every refresh, which sounds wasteful and is not: the archive is 107 KB
     * in total, less than a single photo, and the alternative is trusting a timestamp.
     *
     * **Change is detected by content, never by mtime.** `CLAUDE.md` says so and the reason is
     * Nextcloud: a sync client rewrites modification times whenever it feels like it, so a note can
     * have a new mtime and identical bytes, or — worse, and this is the one that loses work — the
     * same mtime after a genuine change made on another device within the same second. A SHA-256 of
     * the bytes answers the only question that matters, and hashing 107 KB costs less than the
     * provider round trip that fetched it.
     *
     * The parse cache is keyed on that hash, so a refresh where nothing changed re-parses nothing.
     */
    suspend fun readAll(): Pair<NoteIndex, VaultFailure?> = withContext(Dispatchers.IO) {
        val listing = list()
        if (listing.error != null && listing.files.isEmpty()) {
            return@withContext NoteIndex(emptyList()) to listing.error
        }
        val indexed = listing.files.mapNotNull { file ->
            val text = read(file.uri) ?: return@mapNotNull null
            val hash = sha256(text)
            val note = parseCache.getOrPut(hash) { Note.parse(text) }
            IndexedNote(file, note, hash, roundTrips = note.render() == text)
        }
        // Files that vanished between the listing and now simply do not appear. They are never
        // deleted from anything, and nothing is written to say they are gone — a half-synced folder
        // must not destroy data, so the app's only response to a missing note is to stop showing it
        // until it comes back.
        parseCache.keys.retainAll(indexed.map { it.contentHash }.toSet())
        NoteIndex(indexed) to listing.error
    }

    /**
     * Write [newBody] and [newTags] into [note], if and only if that is safe and necessary.
     *
     * The order of the checks is the design, and each one exists to prevent a specific way of
     * losing the user's writing:
     *
     * 1. **Refuse a note we could not reproduce.** If the parser did not round-trip this note when
     *    it was read, it will not round-trip it now, and writing would corrupt it.
     * 2. **Do nothing if nothing changed.** `Note.withTags` returns null when neither the body nor
     *    the tags moved, and that is the common case — opening a note and closing it must leave the
     *    file alone, mtime included. Without this the archive's dates would be destroyed by
     *    ordinary reading.
     * 3. **Re-read and compare before writing.** The folder is synced; the file may have changed
     *    since we loaded it. Compared by content hash rather than by timestamp, because a sync
     *    client rewrites mtimes and because two edits inside one second share theirs.
     * 4. **Write through a temp file**, then swap.
     *
     * A tag change and a body change are one write, not two. `Note.withTags` puts the new tag in
     * the `tags:` list *and* on the note's hashtag line, and stamps `updated` once for the pair —
     * so the two representations cannot be left disagreeing by a save that half succeeded.
     *
     * ## On atomicity, honestly
     *
     * `CLAUDE.md` asks for "temp file plus rename, so a sync client never sees a truncated note".
     * SAF cannot do a POSIX atomic replace: `DocumentsContract.renameDocument` refuses to overwrite
     * an existing name, so the swap is necessarily delete-then-rename and there is a window — of
     * milliseconds — in which neither name holds the finished file.
     *
     * That window is survivable and the alternative is not. If the process dies inside it, the
     * temp file holds the complete new text and [recoverTemp] restores it on the next launch. The
     * alternative — writing in place — puts the truncation *inside the user's own file*, which no
     * amount of recovery undoes. This is a platform limit rather than a shortcut; do not "simplify"
     * it back to an in-place write.
     *
     * The file's modification time is not set explicitly, because SAF offers no way to. It ends up
     * as the moment of the write, which is within a second of the `updated` stamp — which is what
     * mirroring the two was asking for.
     */
    suspend fun save(
        note: IndexedNote,
        newBody: String,
        newTags: List<String> = note.note.editableTags,
        now: Instant = Instant.now(),
    ): SaveResult =
        withContext(Dispatchers.IO) {
            if (!note.roundTrips) return@withContext SaveResult.Refused

            val updated = note.note.withTags(newBody, newTags, now) ?: return@withContext SaveResult.Unchanged
            val text = updated.render()

            val current = read(note.file.uri)
                ?: return@withContext SaveResult.Failed("the note could not be re-read")
            if (sha256(current) != note.contentHash) {
                return@withContext SaveResult.Conflict(current)
            }

            val parent = rootFolder()
                ?: return@withContext SaveResult.Failed("the folder is no longer reachable")
            val tempName = note.file.name + TEMP_SUFFIX
            val temp = runCatching {
                DocumentsContract.createDocument(resolver, parent, MIME_TEXT, tempName)
            }.getOrNull() ?: return@withContext SaveResult.Failed("no temporary file could be made")

            val bytes = text.toByteArray(Charsets.UTF_8)
            val written = runCatching {
                resolver.openOutputStream(temp, "wt")?.use { it.write(bytes); it.flush() } ?: error("no stream")
                // Read it back before trusting it with the only copy. A short write here and a
                // delete below would lose the note outright.
                read(temp) == text
            }.getOrDefault(false)
            if (!written) {
                runCatching { DocumentsContract.deleteDocument(resolver, temp) }
                return@withContext SaveResult.Failed("the note could not be written in full")
            }

            val swapped = runCatching {
                DocumentsContract.deleteDocument(resolver, note.file.uri)
                DocumentsContract.renameDocument(resolver, temp, note.file.name)
            }.getOrNull()
            if (swapped == null) {
                return@withContext SaveResult.Failed("the note could not be put back in place")
            }
            parseCache[sha256(text)] = updated
            SaveResult.Saved(swapped, text, sha256(text))
        }

    /**
     * Write [newBody] into a **new note beside** [note], leaving both.
     *
     * The answer to a conflict. `CLAUDE.md` forbids overwriting silently and forbids auto-merging,
     * which leaves exactly one honest option when a note has changed on another device while it sat
     * open here: keep both and let the user sort it out with the two of them in front of them.
     *
     * The copy takes the original's frontmatter — `created` included, because it is the same note's
     * history — with a fresh `updated`, and with whatever tag change was pending. The filename gains a numbered suffix, and the numbering
     * matches what the archive already does by hand: `Wer geht vor 2.md` sits beside
     * `Wer geht vor.md`. No marker, no "(conflicted copy)" — the user names their own files.
     */
    suspend fun saveCopy(
        note: IndexedNote,
        newBody: String,
        newTags: List<String> = note.note.editableTags,
        now: Instant = Instant.now(),
    ): SaveResult =
        withContext(Dispatchers.IO) {
            if (!note.roundTrips) return@withContext SaveResult.Refused
            val parent = rootFolder()
                ?: return@withContext SaveResult.Failed("the folder is no longer reachable")

            val stem = note.file.name.removeSuffix(".md")
            val taken = list().files.map { it.name }.toSet()
            val name = generateSequence(2) { it + 1 }
                .map { "$stem $it.md" }
                .first { it !in taken }

            // The copy carries the tag change too. A conflict is not a reason to lose the chip the
            // user just tapped — that would make "keep both" quietly mean "keep neither version of
            // the tags", and the user would have no way of knowing.
            val text = (note.note.withTags(newBody, newTags, now) ?: note.note).render()

            val created = runCatching {
                DocumentsContract.createDocument(resolver, parent, MIME_TEXT, name)
            }.getOrNull() ?: return@withContext SaveResult.Failed("the copy could not be made")

            val ok = runCatching {
                resolver.openOutputStream(created, "wt")?.use {
                    it.write(text.toByteArray(Charsets.UTF_8)); it.flush()
                } ?: error("no stream")
                read(created) == text
            }.getOrDefault(false)
            if (!ok) {
                runCatching { DocumentsContract.deleteDocument(resolver, created) }
                return@withContext SaveResult.Failed("the copy could not be written in full")
            }
            SaveResult.Saved(created, text, sha256(text))
        }

    /**
     * Put back anything a write was interrupted in the middle of.
     *
     * Called on every listing, because the window it covers is a crash or a kill during the swap in
     * [save] and the next launch is the only chance to notice. Two cases, distinguished by whether
     * the real note is still there:
     *
     * - The note exists: the write died before the swap, so the temp is a stale draft and is
     *   deleted. The note on disk is untouched and correct.
     * - The note is gone: the write died *inside* the swap, after the delete and before the rename.
     *   The temp holds the complete new text, so it is renamed into place. This is the case the
     *   whole temp-file dance exists for.
     */
    private fun recoverTemp(tree: Uri, temps: List<NoteFile>, notes: Set<String>) {
        for (temp in temps) {
            val target = temp.name.removeSuffix(TEMP_SUFFIX)
            runCatching {
                if (target in notes) DocumentsContract.deleteDocument(resolver, temp.uri)
                else DocumentsContract.renameDocument(resolver, temp.uri, target)
            }
        }
    }

    /**
     * Parsed notes by content hash, so an unchanged note is not re-parsed on every foreground.
     *
     * Keyed by content rather than by uri on purpose: the three byte-identical `Wer geht vor` notes
     * share one entry, which is correct — a [Note] is an immutable value, and which file it came
     * from is [IndexedNote]'s business, not this cache's.
     */
    private val parseCache = HashMap<String, Note>()

    /**
     * Whether a filename is one of ours.
     *
     * Extension only, and never the MIME type. Providers disagree wildly about what a `.md` file is
     * — `text/markdown`, `text/plain`, `application/octet-stream` and empty are all answers this has
     * to survive — and a note that vanished from the library because a sync client relabelled it
     * would look exactly like data loss to the person whose archive it is.
     */
    private fun isNote(name: String) = name.endsWith(".md", ignoreCase = true)

    private fun documentUri(tree: Uri, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, documentId)

    /** One column of one document, or null if it cannot be read. */
    private fun queryOne(uri: Uri, column: String): String? =
        runCatching { resolver.query(uri, arrayOf(column), null, null, null) }.getOrNull()
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

    companion object {
        private const val PREFS = "vault"
        private const val KEY_ROOT = "root_tree_uri"
        private const val KEY_ROOT_NAME = "root_display_name"

        /**
         * What an in-progress write is called while it is being written.
         *
         * Long and unmistakable on purpose: it must never collide with a note the user made, and if
         * one is ever left behind it should be obvious what left it. It keeps the `.md` in the
         * middle (`Atlantik.md.pocketprose-tmp`) so the recovered name is a plain suffix strip.
         */
        const val TEMP_SUFFIX = ".pocketprose-tmp"

        /** Providers disagree about Markdown's type; this is only what a new file is created as. */
        private const val MIME_TEXT = "text/plain"

        /**
         * A filename in the form the archive is stored in.
         *
         * The notes came off a Mac, where the filesystem hands out decomposed names — "Müde" as `u`
         * followed by a combining diaeresis — while the archive on disk is composed. The two are the
         * same name and compare unequal as strings, so any lookup by name has to normalise first or
         * `Müde.md` and `Croquette Voilà.md` become unfindable depending on which machine last
         * touched them.
         *
         * NFC on write, per `CLAUDE.md`; this is the matching half of that rule, and it lives here
         * so that both halves are in one file. Not yet used by [list], which compares nothing — it
         * is for phase 5's attachment lookup and phase 2's change detection.
         */
        fun normalizedName(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC)

        /** A note's identity-of-content, for detecting a change a timestamp would miss. */
        fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
