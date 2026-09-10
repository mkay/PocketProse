// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import de.singular.writer.markdown.Frontmatter
import de.singular.writer.markdown.Migration
import de.singular.writer.markdown.Note
import de.singular.writer.markdown.Tags
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
 * A tree grant, not a path. The user points at a folder once — a synced folder, a folder
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
     * a sync client rewrites on sync — ordering the library by it would reshuffle the list every time
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
     * Syncthing: a sync client rewrites modification times whenever it feels like it, so a note can
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
        // **Read concurrently.** Every file is a separate round trip to the DocumentsProvider, and
        // 168 of them one after another took about three seconds on the Fairphone — the whole of the
        // app's startup, spent waiting on IPC rather than on work. The archive is 107 KB; the time
        // was latency, and latency is what overlapping requests is for.
        //
        // Bounded, because "start 168 at once" is a different way to be slow: the provider answers
        // from one process and a queue that deep buys nothing. Eight is comfortably past the point
        // where the round trips stop being the limit.
        //
        // Measured on the Fairphone against the real archive, 2026-09-07: reading the 168 notes one
        // after another took 5774 ms, and eight at a time takes 964 ms. The listing itself is 270 ms
        // either way, being a single cursor query.
        val texts = coroutineScope {
            val permits = Semaphore(PARALLEL_READS)
            listing.files
                .map { file -> async { file to permits.withPermit { read(file.uri) } } }
                .awaitAll()
        }

        // Parsing stays on this coroutine. It is fast — the whole archive is 107 KB — and it keeps
        // `parseCache` a plain map touched by one thread, which is worth more than the microseconds
        // sharing it would save.
        val indexed = texts.mapNotNull { (file, text) ->
            if (text == null) return@mapNotNull null
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
        newTags: List<String> = note.note.tags,
        now: Instant = Instant.now(),
        newTitle: String? = note.note.title,
    ): SaveResult =
        withContext(Dispatchers.IO) {
            if (!note.roundTrips) return@withContext SaveResult.Refused

            val updated = note.note.withTags(newBody, newTags, now, newTitle)
                ?: return@withContext SaveResult.Unchanged
            write(note, updated)
        }

    /**
     * Put [updated] where [note] is, atomically, having checked that nothing moved underneath it.
     *
     * The write itself, with no opinion about what changed or whether `updated` should have moved —
     * [save] decides that for an edit, [migrateInline] decides it differently for a migration,
     * and both arrive here with a finished [Note]. Splitting it out is what lets the migration reuse
     * the temp-file-and-rename dance rather than grow a second copy of it: there is one piece of
     * code in this app that can leave a note half-written, and it should stay one.
     */
    private suspend fun write(note: IndexedNote, updated: Note): SaveResult =
        withContext(Dispatchers.IO) {
            if (!note.roundTrips) return@withContext SaveResult.Refused
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
                createNamed(parent, tempName)
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
     * Rename [from] to [to] everywhere in the folder, or nowhere.
     *
     * The one edit that writes many files at once. `lyrics/snippet` sits on 131 of the archive's 168
     * notes, so this is not a save with a loop around it — the failure modes are different in kind,
     * and [RenameResult] is the type that says so.
     *
     * **Two passes, and the first one writes nothing.** Every affected note is re-read from disk and
     * checked twice over: that it still hashes to what the index recorded, and that it round-trips.
     * Only if all of them pass does anything get written.
     *
     * The reason is not corruption — [save] refuses a note that changed underneath it, so pushing
     * through would skip that note rather than mangle it. The reason is what a half-renamed tag
     * *looks* like. 130 notes saying `record` and one still saying `album` puts two tags in the
     * drawer where the user asked for one, which reads as the app having lost their data even though
     * every file on disk is intact, and finding the straggler means opening notes one at a time. An
     * archive in one state or the other is worth an extra pass of reads.
     *
     * The extra pass is real work — it doubles the reads, since [save] re-reads each note for its own
     * hash check — and it is the price of the all-or-nothing property. On 131 notes it is about a
     * second.
     *
     * **It does not close the window entirely.** A sync client can land a file between the check and
     * the write, and SAF offers no transaction to undo the writes already made. [RenameResult.Partial]
     * reports exactly how far it got, and the recovery is to run it again: renaming is idempotent, so
     * a second pass covers only what the first missed and writes nothing for the notes it already
     * did.
     *
     * `updated` does not move on any of these notes — see `Note.withTags` and the file format
     * contract in `CLAUDE.md`. Re-filing is not writing, and a rename that restamped 131 notes would
     * destroy the dates this archive is kept for.
     */
    suspend fun renameTag(index: NoteIndex, from: String, to: String): RenameResult =
        withContext(Dispatchers.IO) {
            val affected = index.notes.filter { note -> note.tags.any { Tags.isUnder(it, from) } }
            if (affected.isEmpty()) return@withContext RenameResult.NoSuchTag

            // Pass one: read only. A note the parser cannot reproduce is a note this must not
            // rewrite, and it is worth knowing before half the folder has been written.
            val refused = affected.filterNot { it.roundTrips }.map { it.file.name }
            if (refused.isNotEmpty()) return@withContext RenameResult.Refused(refused)

            val stale = affected.filter { note ->
                val current = read(note.file.uri)
                current == null || sha256(current) != note.contentHash
            }.map { it.file.name }
            if (stale.isNotEmpty()) return@withContext RenameResult.Stale(stale)

            // Pass two: write. Every note here passed the check a moment ago, so a failure now is a
            // sync landing inside the window — rare, and reported rather than hidden.
            var written = 0
            for ((i, note) in affected.withIndex()) {
                val renamed = Tags.rename(note.tags, from, to)
                when (val result = save(note, note.note.body, renamed)) {
                    is SaveResult.Saved -> written++
                    // Nothing to write is a success: the note already says what it should. This is
                    // what makes running the rename a second time safe.
                    SaveResult.Unchanged -> written++
                    else -> {
                        val remaining = affected.drop(i).map { it.file.name }
                        val reason = when (result) {
                            is SaveResult.Conflict -> "a note changed while the rename was running"
                            is SaveResult.Failed -> result.reason
                            SaveResult.Refused -> "a note could not be reproduced byte for byte"
                            else -> "the rename stopped"
                        }
                        return@withContext RenameResult.Partial(written, remaining, reason)
                    }
                }
            }
            RenameResult.Renamed(written)
        }

    /**
     * Move every note's filing out of the folder's text and into its frontmatter — once, on consent.
     *
     * The migration a folder from another editor needs: a tag written as `#lyrics/snippet` in the
     * body goes into the note's `tags:` list, an `# Adlerohr` heading opening the note goes into its
     * `title:`, and the lines they were on go away. `Migration` holds all of the reasoning about
     * *what* a move is; this holds the reasoning about writing it.
     *
     * **Two passes, and the first one writes nothing** — the same shape as [renameTag], for a
     * stronger version of the same reason. A half-migrated folder is not merely untidy: some notes
     * would show their tags as chips and the rest would still show them as text, in a library the
     * user is looking at for the first time. That reads as the app having worked on some notes and
     * broken others, and there is nothing on screen to say which is which.
     *
     * The precheck matters more here than anywhere else in the app, because this runs on a folder
     * the app has just met. A note whose shape the parser has misunderstood is likelier now than it
     * will ever be again, and the very first thing a new user must not experience is a mangled note.
     * So a single note that does not round-trip stops the whole migration before a byte is written,
     * and says which one.
     *
     * **`updated` does not move on any of these notes.** The bodies change and the timestamps stay:
     * this is filing, not writing — see the file format contract in `CLAUDE.md`, and the class
     * comment in `Migration`. It is why the notes are written through [write] with a note that
     * `Migration.plan` built, rather than through [save], whose whole job is to decide that a
     * changed body means a new stamp.
     *
     * Not called on folder adoption, and not called on its own. See `Migration`'s class comment for
     * why the offer waits until the user has seen their notes.
     */
    suspend fun migrateInline(index: NoteIndex): MigrationResult =
        withContext(Dispatchers.IO) {
            val planned = index.notes
                .map { it to Migration.plan(it.note) }
                .mapNotNull { (indexed, outcome) ->
                    when (outcome) {
                        is Migration.Outcome.Move -> indexed to outcome
                        // A note the app declines to file is left exactly as it is, and does not
                        // stop the folder — unlike a note it cannot reproduce, which does. The
                        // difference is that one is a shape this app knows it should not touch and
                        // the other is a shape it does not understand.
                        is Migration.Outcome.Blocked -> null
                        Migration.Outcome.Untouched -> null
                    }
                }
            if (planned.isEmpty()) return@withContext MigrationResult.NothingToMove

            // Pass one: read only.
            val refused = planned.filterNot { (indexed, _) -> indexed.roundTrips }.map { it.first.file.name }
            if (refused.isNotEmpty()) return@withContext MigrationResult.Refused(refused)

            val stale = planned.filter { (indexed, _) ->
                val current = read(indexed.file.uri)
                current == null || sha256(current) != indexed.contentHash
            }.map { it.first.file.name }
            if (stale.isNotEmpty()) return@withContext MigrationResult.Stale(stale)

            // Pass two: write. `write` rather than `save`, so no note is stamped.
            var written = 0
            for ((i, entry) in planned.withIndex()) {
                val (indexed, move) = entry
                when (val result = write(indexed, move.note)) {
                    is SaveResult.Saved -> written++
                    // Nothing to write is a success: the note already says what it should, which is
                    // what makes running this a second time safe.
                    SaveResult.Unchanged -> written++
                    else -> {
                        val remaining = planned.drop(i).map { it.first.file.name }
                        val reason = when (result) {
                            is SaveResult.Conflict -> "a note changed while the move was running"
                            is SaveResult.Failed -> result.reason
                            SaveResult.Refused -> "a note could not be reproduced byte for byte"
                            else -> "the move stopped"
                        }
                        return@withContext MigrationResult.Partial(written, remaining, reason)
                    }
                }
            }
            MigrationResult.Moved(
                notes = written,
                tags = planned.flatMap { it.second.filed }.distinct().size,
                titles = planned.count { it.second.titled != null },
            )
        }

    /**
     * Create a document called exactly [name], whatever the provider would rather call it.
     *
     * `DocumentsContract.createDocument` treats the display name as a suggestion. Handed
     * `text/plain` and `Foobar.md`, `ExternalStorageProvider` decides the extension disagrees with
     * the type and writes `Foobar.md.txt` — which this app's own listing then ignores, since it
     * looks for `.md`. The first new note ever made vanished exactly that way: created, written,
     * verified, and invisible.
     *
     * So the mime type says markdown, and the name is checked afterwards and corrected if the
     * provider changed it anyway. Both, because providers differ and this is not worth being clever
     * about: the cost of guessing wrong is a file the user cannot see.
     *
     * It matters beyond new notes. A conflict copy would have landed as `Atlantik 2.md.txt` and been
     * lost the same way, and a temp file mangled to `.pocketprose-tmp.txt` would no longer match
     * what `recoverTemp` looks for — so an interrupted write would leave an orphan nothing collects.
     */
    private fun createNamed(parent: Uri, name: String, mime: String? = null): Uri? {
        if (mime != null) {
            // An attachment is not a note: its own type is the right one and the markdown fallback
            // below would be nonsense for a PNG.
            val made = create(parent, mime, name) ?: return null
            if (displayName(made) == name) return made
            return runCatching { DocumentsContract.renameDocument(resolver, made, name) }
                .getOrNull() ?: made
        }
        // Markdown first, so a provider that understands it keeps the name as given. Falling back to
        // text/plain because a provider that does *not* understand a type can refuse outright, and
        // text/plain is the one this app is known to be able to create with on this device.
        val created = create(parent, MIME_MARKDOWN, name) ?: create(parent, MIME_TEXT, name) ?: return null
        if (displayName(created) == name) return created
        return runCatching { DocumentsContract.renameDocument(resolver, created, name) }
            .getOrNull() ?: created
    }

    private fun create(parent: Uri, mime: String, name: String): Uri? = runCatching {
        DocumentsContract.createDocument(resolver, parent, mime, name)
    }.getOrNull()

    /** What the provider actually calls [uri], or null if it will not say. */
    private fun displayName(uri: Uri): String? {
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return runCatching {
            resolver.query(uri, columns, null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
    }

    /**
     * Make a new, empty note titled [title], and hand back the file it landed in.
     *
     * The one place the app chooses a filename, and it does so **once, at creation**. `CLAUDE.md`
     * forbids renaming a file to match its title — that rule is about a file the user already has,
     * whose name is theirs; a file that does not exist yet has to be called something, and calling
     * it after its title is what the other 168 notes do.
     *
     * The name is the title with the characters a filename cannot hold taken out, NFC-normalised,
     * and numbered if it is taken — `Atlantik 2.md` beside `Atlantik.md`, which is how the archive
     * already handles a repeat. **The title keeps everything the filename dropped**: 11 notes end in
     * a `?` that their filenames cannot carry, and a new one may too.
     *
     * The frontmatter is the archive's own shape and nothing more: `title`, `created`, `updated`,
     * and an empty `tags: []` exactly as the three untagged notes carry it. No app-owned key, no id,
     * no marker — the prime directive's second prohibition.
     *
     * The body is a single blank line, which is what every note in the archive has between its
     * frontmatter and its first word.
     */
    suspend fun create(title: String, now: Instant = Instant.now()): CreateResult =
        withContext(Dispatchers.IO) {
            val clean = title.trim()
            if (clean.isEmpty()) return@withContext CreateResult.Failed("a note needs a title")
            val parent = rootFolder()
                ?: return@withContext CreateResult.Failed("the folder is no longer reachable")

            val taken = list().files.map { normalizedName(it.name) }.toSet()
            val stem = fileStem(clean)
            val name = generateSequence(0) { it + 1 }
                .map { if (it == 0) "$stem.md" else "$stem $it.md" }
                .first { normalizedName(it) !in taken }

            val text = newNoteText(clean, now)

            val created = runCatching {
                createNamed(parent, name)
            }.getOrNull() ?: return@withContext CreateResult.Failed("the note could not be made")

            val written = runCatching {
                resolver.openOutputStream(created, "wt")?.use {
                    it.write(text.toByteArray(Charsets.UTF_8)); it.flush()
                } ?: error("no stream")
                read(created) == text
            }.getOrDefault(false)
            if (!written) {
                runCatching { DocumentsContract.deleteDocument(resolver, created) }
                return@withContext CreateResult.Failed("the note could not be written")
            }
            CreateResult.Made(created, text)
        }

    /**
     * Give [note]'s file a new name, leaving every byte inside it alone.
     *
     * **This is not the rename `CLAUDE.md` forbids.** That prohibition — "rename a file to match its
     * title, its heading, or anything else" — is about the app deciding, on its own, that a file is
     * called the wrong thing. A person renaming their own note is the opposite act, and it is the
     * same kind of edit as renaming a tag: asked for, confirmed, and touching exactly what was named.
     *
     * The note is not opened, parsed or written. `title` in the frontmatter is untouched, and stays
     * authoritative — after this the two may disagree, which is the archive's ordinary state and not
     * a thing to reconcile. `updated` does not move either, for the reason it never moves on a filing
     * change: nobody rewrote the song.
     *
     * [stem] is the name without `.md`, and it goes through [fileStem] here as well as in the dialog
     * that previewed it — the check the user saw and the check that runs must be the same one.
     *
     * **A taken name is refused, never numbered.** See [RenameNoteResult.Taken].
     *
     * The provider hands back a new document id, so the caller has to re-point at it; there is no
     * way to rename in SAF that keeps the uri. Verified afterwards by asking the provider what the
     * file is now called, because `createNamed` had to learn that a display name is a suggestion.
     */
    suspend fun renameNote(note: IndexedNote, stem: String): RenameNoteResult =
        withContext(Dispatchers.IO) {
            val clean = fileStem(stem.trim().removeSuffix(".md"))
            val name = "$clean.md"
            if (normalizedName(name) == normalizedName(note.file.name)) {
                return@withContext RenameNoteResult.Unchanged
            }

            val listing = list()
            if (listing.error != null) {
                return@withContext RenameNoteResult.Failed("the folder is no longer reachable")
            }
            // Compared normalised, because a name that came off a Mac is decomposed and would
            // otherwise look free while the provider knows it is taken — and then the rename fails
            // for a reason the user was told would not happen.
            if (listing.files.any { normalizedName(it.name) == normalizedName(name) }) {
                return@withContext RenameNoteResult.Taken
            }

            val renamed = runCatching {
                DocumentsContract.renameDocument(resolver, note.file.uri, name)
            }.getOrNull() ?: return@withContext RenameNoteResult.Failed("the note could not be renamed")

            // A provider that renamed the file but called it something else has made a note this app
            // may not be able to find again — `.md` is what `list` looks for. Say so rather than
            // report a success the folder does not agree with.
            val actual = displayName(renamed)
            if (actual != null && normalizedName(actual) != normalizedName(name)) {
                return@withContext RenameNoteResult.Failed("your folder named it “$actual” instead")
            }
            RenameNoteResult.Renamed(renamed, name)
        }

    /**
     * Delete [note]'s file.
     *
     * The only place the app removes anything the user wrote, and it is never reached without an
     * explicit confirmation in front of it — `CLAUDE.md` is unambiguous that nothing goes without
     * being asked for. There is no trash and no undo here: the file is gone from the folder, and
     * whether it comes back is the sync client's business rather than this app's.
     */
    suspend fun delete(note: IndexedNote): Boolean = withContext(Dispatchers.IO) {
        runCatching { DocumentsContract.deleteDocument(resolver, note.file.uri) }.getOrDefault(false)
    }

    /**
     * A title reduced to something a filesystem will accept, without being reduced any further.
     *
     * Only the characters that genuinely cannot appear in a name are removed — `/` above all, which
     * would make the note a path — plus the reserved set Android's providers and FAT-formatted cards
     * object to. Everything else the archive already proves is fine stays: commas, parentheses,
     * umlauts, accents, a trailing full stop, and spaces.
     */


    /**
     * Copy [source] into the folder's `attachments/`, and hand back the relative path to link it by.
     *
     * **The file goes into the user's folder, not into ours.** `CLAUDE.md` is explicit: an image the
     * user adds is written next to the note and linked relatively, never copied into app-private
     * storage as the canonical copy and never base64-inlined. So what comes back is
     * `attachments/name.png` — a path that means the same thing to this app, to a desktop editor, and
     * to whatever syncs the folder — and the bytes live where the other 26 attachments already do.
     *
     * The subfolder is created if the folder has none. A flat sibling layout is also valid and
     * occurs in the archive (see [Attachments]), but a folder being written into for the first time
     * gets the tidier of the two rather than 26 pictures loose among the notes.
     *
     * [suggested] is the name the picker offered, which for a camera roll is often something like
     * `IMG_20260910_112233.jpg` and occasionally nothing at all. It goes through [fileStem] like any
     * other name the user did not type, keeps its extension, and is numbered if it is taken — the
     * archive's own convention, and the only alternative to overwriting a picture already in use.
     *
     * Returns null if anything went wrong, because the caller's answer to all of it is the same: say
     * so and insert no link. **A link to a file that is not there is worse than no image**, since the
     * note then carries a broken reference the user has to find and remove by hand.
     */
    suspend fun addAttachment(source: Uri, suggested: String?): String? = withContext(Dispatchers.IO) {
        val root = rootFolder() ?: return@withContext null
        val folder = attachmentsFolder(root) ?: return@withContext null

        val taken = childNames(folder)
        val raw = suggested?.substringAfterLast('/').orEmpty()
        val extension = raw.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 }
            ?: mimeExtension(source)
            ?: "bin"
        val stem = fileStem(raw.substringBeforeLast('.', raw).ifBlank { "image" })
        val name = generateSequence(0) { it + 1 }
            .map { if (it == 0) "$stem.$extension" else "$stem $it.$extension" }
            .first { normalizedName(it) !in taken }

        val created = createNamed(folder, name, mimeTypeOf(source) ?: "application/octet-stream")
            ?: return@withContext null
        val copied = runCatching {
            resolver.openInputStream(source)?.use { input ->
                resolver.openOutputStream(created, "wt")?.use { out -> input.copyTo(out) }
                    ?: error("no stream")
            } ?: error("no source")
            true
        }.getOrDefault(false)
        if (!copied) {
            // A half-written picture in somebody's folder is litter this app put there.
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            return@withContext null
        }
        "$ATTACHMENTS/${displayName(created) ?: name}"
    }

    /** The `attachments/` subfolder, made if the folder has not got one yet. */
    private fun attachmentsFolder(root: Uri): Uri? {
        val tree = this.root ?: return null
        val existing = runCatching {
            resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    tree,
                    DocumentsContract.getDocumentId(root),
                ),
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )?.use { c ->
                generateSequence { if (c.moveToNext()) c else null }
                    .firstOrNull {
                        c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR &&
                            normalizedName(c.getString(1)) == ATTACHMENTS
                    }
                    ?.let { documentUri(tree, c.getString(0)) }
            }
        }.getOrNull()
        if (existing != null) return existing
        return runCatching {
            DocumentsContract.createDocument(
                resolver, root, DocumentsContract.Document.MIME_TYPE_DIR, ATTACHMENTS,
            )
        }.getOrNull()
    }

    /** Every name already in [folder], normalised, so a new one can avoid them. */
    private fun childNames(folder: Uri): Set<String> {
        val tree = root ?: return emptySet()
        return runCatching {
            resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    tree, DocumentsContract.getDocumentId(folder),
                ),
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                buildSet { while (c.moveToNext()) add(normalizedName(c.getString(0))) }
            }
        }.getOrNull().orEmpty()
    }

    /** A file extension for [uri] from its type, when its name did not carry one. */
    private fun mimeExtension(uri: Uri): String? = when (mimeTypeOf(uri)) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> null
    }

    private fun mimeTypeOf(uri: Uri): String? = runCatching { resolver.getType(uri) }.getOrNull()

    /**
     * Delete every note in [notes], and say honestly how far it got.
     *
     * The batch form of [delete], and it exists because a selection can stop half way — see
     * [DeleteResult]. Each file is removed on its own; one that refuses does not stop the rest,
     * because a user who ticked five notes and lost one to a sync is better served by four of them
     * being gone and being told which one is not.
     *
     * **No confirmation lives here.** `CLAUDE.md` forbids deleting anything without an explicit yes,
     * and that yes is asked for in the dialog this is called from — with the count in the question,
     * because agreeing to remove two notes is not the same act as agreeing to remove twelve.
     *
     * There is no trash and no undo, exactly as with [delete]. Whether the sync client kept a copy
     * is its business and not something this app will imply.
     */
    suspend fun deleteAll(notes: List<IndexedNote>): DeleteResult = withContext(Dispatchers.IO) {
        if (notes.isEmpty()) return@withContext DeleteResult.Deleted(0)
        rootFolder() ?: return@withContext DeleteResult.Failed("the folder is no longer reachable")

        var gone = 0
        val left = mutableListOf<String>()
        for (note in notes) {
            val ok = runCatching {
                DocumentsContract.deleteDocument(resolver, note.file.uri)
            }.getOrDefault(false)
            if (ok) gone++ else left.add(note.file.name)
        }
        if (left.isEmpty()) DeleteResult.Deleted(gone) else DeleteResult.Partial(gone, left)
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
        newTags: List<String> = note.note.tags,
        now: Instant = Instant.now(),
        newTitle: String? = note.note.title,
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
            val text = (note.note.withTags(newBody, newTags, now, newTitle) ?: note.note).render()

            val created = runCatching {
                createNamed(parent, name)
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
        /** How many notes are fetched from the provider at once. See the note in [readAll]. */
        private const val PARALLEL_READS = 8

        /**
         * The bytes a brand-new note is made of.
         *
         * The archive's own shape and nothing more: `title`, `created`, `updated`, `tags: []` — the
         * four keys every one of the 168 notes carries, in the order they carry them, quoted the way
         * they quote them. **No app-owned key, no id, no marker**, which is the prime directive's
         * second prohibition and the thing every editor the author tried got wrong.
         *
         * The body is one blank line, which is what sits between frontmatter and first word
         * everywhere in the archive.
         *
         * Pure and separate so it can be tested, because it has to satisfy something easy to get
         * wrong: a note whose bytes the parser cannot reproduce is refused by `Vault.save`. Emit
         * this slightly off and every new note is born read-only, which would look like a mystery
         * rather than like a bug.
         */
        fun newNoteText(title: String, now: Instant): String {
            val stamp = Note.stamp(now)
            return buildString {
                append("---\n")
                append("title: ").append(Frontmatter.quoted(title)).append('\n')
                append("created: ").append(stamp).append('\n')
                append("updated: ").append(stamp).append('\n')
                append("tags: []\n")
                append("---\n\n")
            }
        }

        /**
         * A title reduced to the part a filename can hold.
         *
         * **What it drops is the point.** 11 titles in the archive end in a `?` that no filename can
         * carry, so the two are not the same string and the app must never treat one as derivable
         * from the other. This exists for the two moments a name has to be chosen — a note being
         * made, and a note being renamed by hand — and in the second it is shown to the user before
         * they commit, because a field that silently eats the `?` you typed is a field that lies.
         *
         * Public so the rename dialog can preview it. There is exactly one rule for what a note may
         * be called and it lives here; a second copy in the UI is a second rule waiting to disagree.
         */
        fun fileStem(title: String): String {
            val stripped = title.filterNot { it in "/\\:*?\"<>|" || it.code < 0x20 }.trim()
            val stem = normalizedName(stripped).take(120).trim()
            return stem.ifEmpty { UNTITLED }
        }

        /** What a note is called when its title survives none of the filename rules. */
        private const val UNTITLED = "Note"

        const val TEMP_SUFFIX = ".pocketprose-tmp"

        /** The subfolder attachments go in. The archive's own, holding its 19 PNGs and 7 PDFs. */
        const val ATTACHMENTS = "attachments"

        /** Providers disagree about Markdown's type; this is only what a new file is created as. */
        /**
         * What the app tells the provider a note is.
         *
         * `text/plain` was wrong in a way that hid files: a provider that thinks the extension
         * disagrees with the type appends its own, so `Foobar.md` became `Foobar.md.txt`. See
         * [createNamed], which also checks afterwards rather than trusting this to be enough.
         */
        private const val MIME_MARKDOWN = "text/markdown"

        /** The fallback, and the type this app is known to be able to create with here. */
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
