// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

import de.singular.writer.markdown.Excerpt
import de.singular.writer.markdown.Note
import de.singular.writer.markdown.Segments
import de.singular.writer.markdown.Stats
import de.singular.writer.markdown.Tags
import de.singular.writer.SortBy
import de.singular.writer.SortOrder
import java.time.Instant
import java.text.Normalizer
import java.time.format.DateTimeParseException

/** Whether a note has to carry files, has to carry none, or may do either. */
enum class AttachmentFilter { ANY, WITH, WITHOUT }

/**
 * The three questions the library can be asked at once.
 *
 * One value rather than three parameters threaded through the screen, because they are answered
 * together in one dialog and are only ever meaningful together — "notes tagged radio, holding the
 * word Zeit, with something attached" is one question, and splitting it into three would let two of
 * them be set while the third was forgotten somewhere else. Which is what the app did before this:
 * the tag lived in the drawer, the text in the bar, and nothing named both.
 */
data class Filters(
    val text: String = "",
    val tag: String? = null,
    val attachments: AttachmentFilter = AttachmentFilter.ANY,
    /** Only notes that share a title or a body with another note — see [NoteIndex.duplicateGroups]. */
    val duplicates: Boolean = false,
) {
    /** Nothing is being asked, so the whole folder is the answer. */
    val isEmpty: Boolean
        get() = text.isBlank() && tag == null && attachments == AttachmentFilter.ANY && !duplicates
}

/**
 * What filtering a list of notes actually depends on.
 *
 * The sibling of [Sortable], and it exists for the same reason: an [IndexedNote] cannot be built off
 * a device — its [NoteFile] needs a real `Uri` and `Uri.EMPTY` is null in the stub android.jar — so a
 * predicate written against the note itself can only be tested on a phone. Naming what it actually
 * reads makes it a JVM test.
 */
interface Filterable {
    val title: String
    val body: String
    val tags: List<String>
    val hasAttachments: Boolean
}

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
) : Sortable, Filterable {
    /**
     * The note's title, falling back to the filename without its extension.
     *
     * The fallback is for a plain text file someone dropped into the folder, which has no
     * frontmatter to carry a title. It is *not* how the archive works — all 168 notes carry a
     * `title`, and 11 of them end in a `?` that no filename could hold.
     */
    override val title: String
        get() = note.title?.takeIf { it.isNotBlank() } ?: file.name.removeSuffix(".md")

    override val tags: List<String> get() = note.tags

    /** The note's writing, for the text search. The frontmatter is not part of it. */
    override val body: String get() = note.body

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
     * Whether the note carries anything besides words, for the mark the library's rows wear.
     *
     * **Files of any kind, not pictures only.** The two forms do not overlap in this archive: three
     * notes embed images and four link files, and no note does both. Counting only the embeds — which
     * is what this did at first — left the one note with an explicit `## Anhänge` section unmarked,
     * along with its three byte-identical siblings, which is the row that most wanted saying. Only
     * `png` and `pdf` are referenced anywhere in the archive, and nothing but an image can be
     * embedded in practice: `Segments.split` hands any `![]()` line to the bitmap loader.
     *
     * A web address is not an attachment. `Attachments.isAbsoluteUrl` is the same test the editor
     * uses to decide whether to open a browser or reach into the folder.
     *
     * **Refers to, not has.** Nothing here checks the file is where the link says: resolving a
     * relative path is a provider query per file, and doing that for every row of a scrolling list
     * is what makes a list stutter. The archive holds 24 links pointing at nothing, so the two
     * questions genuinely differ — but a note that names a file is a note with a file in it as far
     * as its author is concerned, and finding out one is missing is what opening it is for.
     *
     * Seven of the archive's 168 notes carry something, so the mark is rare enough to mean something
     * when it appears. Measured at 0.8 ms for all 168, against 8.2 ms for the excerpts the same rows
     * recompute far more often.
     */
    override val hasAttachments: Boolean by lazy {
        Segments.imagesIn(note.body).isNotEmpty() ||
            Segments.linksIn(note.body).any { !Attachments.isAbsoluteUrl(it.target) }
    }

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
     * The notes matching all three criteria at once.
     *
     * **Tag first, then text.** Filtering a tag's notes by a word is the useful order, and it means
     * a search inside a tag does not leave the tag. That combination is not new — the app has always
     * ANDed the two — but it used to be invisible, the tag living in the drawer one screen away with
     * nothing in front of the reader saying so. It is the dialog that fixes that, not this: the tag
     * is shown, selected, with the way to widen it one tap above.
     *
     * The text still matches tag names as well as title and body. Having a tag picker does not make
     * that redundant: typing `radio` is how somebody who has not opened the tag tree will look for
     * notes tagged `radio`, and taking it away would be a loss dressed as tidiness.
     *
     * The search runs over the tag's notes rather than over the folder, which is also what makes it
     * cheap — this is called on every keystroke.
     */
    fun <T : Filterable> matching(notes: List<T>, filters: Filters): List<T> {
        val needle = filters.text.trim()
        // Decided over the whole list handed in, before the other questions narrow it: a note is a
        // duplicate because of what else is in the folder, not because of what else is on screen.
        // Its partner may then be filtered out by the tag or the text — that is the reader's
        // narrowing, and the row that remains is still one of a pair.
        val groups = if (filters.duplicates) duplicateGroups(notes) else null
        return notes.filterIndexed { i, note ->
            (groups == null || groups[i] != null) &&
                (filters.tag == null || note.tags.any { Tags.isUnder(it, filters.tag) }) &&
                (
                    needle.isEmpty() ||
                        note.title.contains(needle, ignoreCase = true) ||
                        note.body.contains(needle, ignoreCase = true) ||
                        note.tags.any { it.contains(needle, ignoreCase = true) }
                    ) &&
                when (filters.attachments) {
                    AttachmentFilter.ANY -> true
                    AttachmentFilter.WITH -> note.hasAttachments
                    AttachmentFilter.WITHOUT -> !note.hasAttachments
                }
        }
    }

    /** The whole folder, filtered. */
    fun matching(filters: Filters): List<IndexedNote> = matching(notes, filters)

    /**
     * Which of [notes] are duplicates of which: for each position, the group it belongs to, or null
     * for a note nothing else resembles. A group is named by the lowest position in it.
     *
     * **Two notes are duplicates when they share a title or share a body**, and the two relations are
     * chained — A titled like B, B worded like C, puts all three in one group. The archive has all
     * three kinds: four files titled `Wer geht vor?` with different words, three (`Wer geht vor 2`,
     * `3`, `4`) with the same 959 bytes of body, and a sync client's conflict copy, which is the
     * same title and nearly the same body under a name the app has no business recognising. One
     * rule finds all of them without the app knowing any sync client's filename grammar, which is
     * why this exists instead of a pattern match on `.sync-conflict-`.
     *
     * A title matches case-insensitively and NFC-normalised, since the macOS-origin names in this
     * folder arrive both ways. A body matches after trimming — the gap under the frontmatter and a
     * trailing newline are structure, not writing — and **an empty body matches nothing**: 40 notes
     * in the archive are a title and a tag, and calling them duplicates of each other would be
     * the filter's own invention.
     *
     * "Duplicate" here is a suspicion the reader is invited to look at, never a verdict: a note
     * titled `Lieblos` twice may be two songs. The app finds, the reader decides, and nothing is
     * merged or removed by anything but the reader's own hand.
     */
    fun <T : Filterable> duplicateGroups(notes: List<T>): List<Int?> {
        val parent = IntArray(notes.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) x = parent[x].also { parent[x] = parent[parent[x]] }
            return x
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb)
        }

        val byTitle = HashMap<String, Int>()
        val byBody = HashMap<String, Int>()
        val paired = BooleanArray(notes.size)
        for ((i, note) in notes.withIndex()) {
            val title = Normalizer.normalize(note.title.trim(), Normalizer.Form.NFC).lowercase()
            byTitle.put(title, i)?.let { union(it, i); paired[it] = true; paired[i] = true }
            val body = note.body.trim()
            if (body.isNotEmpty()) {
                byBody.put(body, i)?.let { union(it, i); paired[it] = true; paired[i] = true }
            }
        }
        return List(notes.size) { i -> if (paired[i]) find(i) else null }
    }

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

    /**
     * [notes] with the ones filed under [tag] moved to the front, each half in the order it came.
     *
     * The pin. It is applied to the sorted list rather than folded into the comparator, so a pinned
     * block and the rest of the list are each in the chosen order — a pinned note does not stop
     * being the newest just because it also sits on top. `Tags.isUnder`, as the filter reads it, so
     * a pin tag that is a parent takes its children with it.
     *
     * A stable partition, and applied *before* the duplicate grouping in the library: a group lands
     * where its first member does, so a pinned "Wer geht vor?" brings its three partners up with it
     * rather than leaving them scattered below. Sitting together is the stronger promise when that
     * filter is on.
     */
    fun <T : Filterable> pinnedFirst(notes: List<T>, tag: String): List<T> =
        if (tag.isEmpty()) notes else notes.sortedBy { note -> note.tags.none { Tags.isUnder(it, tag) } }

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
