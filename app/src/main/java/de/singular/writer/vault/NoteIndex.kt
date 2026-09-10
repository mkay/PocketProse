// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

import de.singular.writer.markdown.Excerpt
import de.singular.writer.markdown.Note
import de.singular.writer.markdown.Stats
import de.singular.writer.markdown.Tags
import de.singular.writer.SortBy
import de.singular.writer.SortOrder
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * What ordering a list of notes actually depends on: a name, a date, a length.
 *
 * Named rather than left implicit so the sort can be tested without a note — an [IndexedNote] cannot
 * be built off a device, its [NoteFile] needing a real `Uri`, and that is why nothing in the vault
 * package had a test before this one. The alternative was a mocking framework to stand in for a
 * field the sort never reads, which is a dependency bought to paper over an unstated contract. This
 * states it instead.
 */
interface Sortable {
    val title: String
    val updated: Instant?
    val words: Int
}

/**
 * One note, read and parsed, with what the library needs to show it.
 *
 * Identity is the [file]'s uri and nothing else. **Never resolve a note by title**: four files in
 * the archive are titled "Wer geht vor?", three "Wer nicht will", and two pairs share a title again
 * — and three files are byte-identical in the body as well. Any map keyed on title or on content
 * silently loses notes, and the loss looks like a sync problem rather than like a bug here.
 */
data class IndexedNote(
    val file: NoteFile,
    val note: Note,
    /** SHA-256 of the file's bytes — see [NoteIndex] on why mtime is not enough. */
    val contentHash: String,
    /**
     * Whether re-rendering this note reproduces the bytes it was read from.
     *
     * Checked on every read, because it is nearly free — one string comparison against text already
     * in hand — and because it is the app's own answer to `CLAUDE.md`'s strictest requirement,
     * available per note instead of once per `md5sum` run.
     *
     * **This is a safety gate, not a statistic.** A note that does not round-trip is one the parser
     * has misunderstood, and writing it back would corrupt it. From phase 4 on, an edit to such a
     * note must be refused rather than attempted: the user is told the app cannot safely save this
     * one, which is a far better outcome than a silently mangled lyric. It is false for no note in
     * the archive today.
     */
    val roundTrips: Boolean,
) : Sortable {
    /**
     * The note's title, falling back to the filename without its extension.
     *
     * The fallback is for a plain text file someone dropped into the folder, which has no
     * frontmatter to carry a title. It is *not* how the archive works — all 168 notes carry a
     * `title`, and 11 of them end in a `?` that no filename could hold.
     */
    override val title: String
        get() = note.title?.takeIf { it.isNotBlank() } ?: file.name.removeSuffix(".md")

    val tags: List<String> get() = note.tags

    /** The first line or so of prose, or "" for the 40 notes that have none. See [Excerpt]. */
    val excerpt: String get() = Excerpt.of(note)

    /**
     * How many words the note holds, for the sort that orders by length.
     *
     * `by lazy` rather than a `get()`, unlike [excerpt] beside it, because the two are paid at
     * different times: an excerpt is drawn for every visible row on every frame and is cheap enough
     * to recompute, while this is asked for once per note when the list is sorted and never again.
     * Computing it eagerly would count the words of all 168 notes on every read of the folder, for a
     * sort most readers will never choose.
     *
     * `Stats.of` counts the body as it stands, leftover hashtag lines included — see the note there
     * on why nothing is excused. So the order this produces is the order of what the files hold.
     */
    override val words: Int by lazy { Stats.of(note.body).words }

    /**
     * When the note was written, from the frontmatter — never the file's timestamp.
     *
     * `CLAUDE.md` calls the 2015–2025 span the archive's main value, and the file mtimes cannot
     * carry it: the notes were exported out of another editor, copied onto a phone, and are now rewritten by
     * a sync client whenever it feels like it. Only `created` remembers.
     *
     * Null if the key is missing or unparseable, which no note in the archive currently is.
     */
    val created: Instant? get() = instant(note.frontmatter.created)

    override val updated: Instant? get() = instant(note.frontmatter.updated)

    private fun instant(value: String?): Instant? =
        value?.let { runCatching { Instant.parse(it) }.getOrNull() }
}

/**
 * Every note in the folder, parsed once, with the tag tree over them.
 *
 * Rebuilt wholesale on each refresh rather than mutated. The archive is 168 notes and 107 KB — the
 * whole of it parses in well under the time a single frame takes — so there is nothing to gain from
 * incremental updates and a great deal to lose: an index that mutates has to be told when a note
 * vanished, and getting that wrong is how an app deletes something that was only half-synced.
 */
class NoteIndex(notes: List<IndexedNote>) {

    /**
     * Most recently changed first, by the note's own `updated`.
     *
     * Not `created`, though that is the field `CLAUDE.md` calls the archive's main value. The two
     * are genuinely different — they fall on different days for 48 of the 168 notes — and what a
     * writing app owes the top of its list is the thing you were last working on. `created` is what
     * the archive is *worth*; `updated` is what you want to reach for. Both are read, and the row
     * shows `updated` because that is what the brief asked for.
     *
     * Notes without a parseable date sort last rather than first — an unknown date is not "just
     * now", and putting one at the top would push the actual newest note off the first screen.
     */
    val notes: List<IndexedNote> = notes.sortedWith(
        compareByDescending<IndexedNote> { it.updated != null }
            .thenByDescending { it.updated }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )

    /**
     * This index with one note replaced by what was just written to it.
     *
     * **So that leaving a note does not wait on the folder.** A save used to be followed by a full
     * re-read — 168 documents fetched through SAF and hashed — and once the editor had to wait for
     * it, tapping back took three or four seconds. Nothing in that read was news: the app has the
     * bytes it just wrote and the hash it verified them by, which is the whole of what an index
     * entry holds.
     *
     * The folder is still re-read on returning to the foreground, which is where a re-read belongs —
     * it is for changes the app did not make.
     *
     * [roundTrips] is recomputed rather than carried over, because the note being written is a new
     * one: the same gate that read it must pass on what is going back.
     */
    fun replacing(old: IndexedNote, uri: android.net.Uri, text: String, hash: String): NoteIndex {
        val note = Note.parse(text)
        val replaced = IndexedNote(
            file = old.file.copy(uri = uri),
            note = note,
            contentHash = hash,
            roundTrips = note.render() == text,
        )
        return NoteIndex(notes.map { if (it.file.name == old.file.name) replaced else it })
    }

    /**
     * This index with one note renamed — a new name and a new uri, the same note inside.
     *
     * The sibling of [replacing] and for the same reason: the editor has to be re-pointed at the
     * renamed file the moment the rename lands, and making it wait on 168 documents being re-read
     * would put three or four seconds between the confirm and the dialog closing.
     *
     * Nothing about the content changes, so [contentHash] and [roundTrips] carry over untouched.
     * That is not an optimisation but the truth: `Vault.renameNote` never opens the file, and a note
     * that round-tripped a second ago still does under a different name.
     */
    fun renamed(old: IndexedNote, uri: android.net.Uri, name: String): NoteIndex = NoteIndex(
        notes.map {
            if (it.file.name == old.file.name) it.copy(file = it.file.copy(uri = uri, name = name)) else it
        },
    )

    /**
     * [notes] in some other order — the library's sort, applied to a list the index has already
     * filtered.
     *
     * **A view onto the index, never the index's own order.** [notes] stays canonical, because
     * [replacing] and [renamed] rebuild it and neither has any business knowing what the reader last
     * picked in a menu. This is called on the filtered list in `MainActivity`, after the tag and the
     * search, so the sort applies to what is actually on screen.
     *
     * Ties break on title in every case, so a list of 39 notes that all hold one word does not
     * reshuffle itself between two reads of the same folder.
     */
    fun <T : Sortable> sorted(notes: List<T>, by: SortBy, order: SortOrder): List<T> {
        val down = order == SortOrder.DESC
        val byTitle = compareBy<T, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
        val comparator = when (by) {
            SortBy.UPDATED -> {
                val dates = if (down) {
                    compareByDescending<T> { it.updated }
                } else {
                    compareBy<T> { it.updated }
                }
                // **Dateless notes stay last in both directions**, which is why the null test is a
                // key of its own rather than something the direction gets to flip. An unknown date
                // is not "the oldest": 40 notes in the archive carry none, and floating them to the
                // top of an ascending sort would bury the note the reader asked for behind every
                // note the app knows nothing about.
                //
                // Reversing a finished ascending list would have done exactly that — it was written
                // that way first — and would have flipped the tiebreak with it.
                compareBy<T> { it.updated == null }.then(dates).then(byTitle)
            }
            SortBy.TITLE -> if (down) byTitle.reversed() else byTitle
            SortBy.WORDS -> {
                val counts = if (down) {
                    compareByDescending<T> { it.words }
                } else {
                    compareBy<T> { it.words }
                }
                // The tiebreak does not turn round with the direction. 39 notes hold one word each,
                // and A-to-Z among them either way is more use than a block that silently inverts.
                counts.then(byTitle)
            }
        }
        return notes.sortedWith(comparator)
    }

    /** The tag tree the drawer draws. See [Tags.tree] for why it is built from path segments. */
    val tagTree: List<Tags.Node> by lazy { Tags.tree(this.notes.map { it.tags }) }

    /** Every distinct tag in use, however deeply nested. */
    val allTags: Set<String> by lazy { this.notes.flatMap { it.tags }.toSet() }

    /**
     * Notes carrying [path] **or any tag beneath it**.
     *
     * Selecting `lyrics` in the drawer means "everything filed under lyrics", which is the only
     * reading that makes a parent tag useful — `album` has three children and no note of its own,
     * so an exact match there would return nothing at all.
     */
    fun withTag(path: String): List<IndexedNote> = notes.filter { indexed ->
        indexed.tags.any { Tags.isUnder(it, path) }
    }

    /**
     * Full-text search over title, tags and body, case-insensitively.
     *
     * The body is searched raw rather than through the excerpt: someone looking for a line they
     * half-remember needs the whole note searched, not the first 140 characters of it.
     */
    fun search(query: String): List<IndexedNote> {
        val needle = query.trim()
        if (needle.isEmpty()) return notes
        return notes.filter { indexed ->
            indexed.title.contains(needle, ignoreCase = true) ||
                indexed.note.body.contains(needle, ignoreCase = true) ||
                indexed.tags.any { it.contains(needle, ignoreCase = true) }
        }
    }

    /**
     * The note stored at [name], matching tolerantly across Unicode normalisation forms.
     *
     * The archive is NFC, but the notes came off a Mac and a name can arrive decomposed — "Müde" as
     * `u` plus a combining diaeresis — which compares unequal to the composed spelling as a plain
     * string. `Müde.md`, `Croquette Voilà.md` and `Ich weigere mich, meine Augen zu öffnen.md` all
     * turn on this. Used by phase 5 to resolve an image path against the folder.
     */
    fun byName(name: String): IndexedNote? {
        val wanted = Vault.normalizedName(name)
        return notes.firstOrNull { Vault.normalizedName(it.file.name).equals(wanted, ignoreCase = true) }
    }

    val size: Int get() = notes.size
}
