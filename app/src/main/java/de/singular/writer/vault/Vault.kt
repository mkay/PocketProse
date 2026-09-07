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
        if (ok) prefs.edit().putString(KEY_ROOT, uri.toString()).apply()
        return ok
    }

    /** Human-readable name of the chosen folder, for the "Notes in …" line. */
    suspend fun rootName(): String? = withContext(Dispatchers.IO) {
        val folder = rootFolder() ?: return@withContext null
        queryOne(folder, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
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
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                val name = it.getString(1) ?: continue
                val mime = it.getString(2) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                if (!isNote(name)) continue
                files += NoteFile(
                    uri = documentUri(tree, id),
                    name = name,
                    sizeBytes = if (it.isNull(3)) 0L else it.getLong(3),
                    modifiedAt = if (it.isNull(4)) 0L else it.getLong(4),
                )
            }
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
