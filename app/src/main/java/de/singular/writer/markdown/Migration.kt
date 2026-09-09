// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/**
 * Moving a note's filing out of its text and into its frontmatter — once, for a folder that was
 * written in an editor that kept both in the body.
 *
 * Two halves, and they are the same move made twice:
 *
 * - **Tags.** A tag written as `#lyrics/snippet` on a line of its own goes into the `tags:` list.
 * - **Titles.** An `# Adlerohr` heading opening the note goes into `title:`.
 *
 * This app reads both from the frontmatter and nowhere else — tags by the Tag rules in `CLAUDE.md`,
 * the title by the file format contract, which says in as many words that it is *not* derived from
 * the filename or from any heading. A folder arriving from an editor that kept them in the body
 * therefore looks half-empty: no tags in the drawer, a `#lyrics/snippet` sitting in the middle of
 * every note, and an editor whose title field is blank for every note in the folder while the
 * library quietly shows the filename in its place. This file is the one-time move that fixes both,
 * and it is offered to the user rather than performed on them — see **When this runs**.
 *
 * ## It is a move, not a conversion
 *
 * The tag goes into the list and the line it was written on goes away; the title goes into the key
 * and the heading goes away. Filing without stripping would show everything twice — a chip at the
 * top and a hashtag in the text, a title in the row and the same words again as the note's first
 * line — which is worse than doing nothing at all, so the halves are one operation and there is no
 * switch between them. Both halves of the doubling are things the author saw on screen before this
 * was written; neither is hypothetical.
 *
 * It is also reversible in principle rather than by backup: the planned export ("write the frontmatter
 * back into the body") is this operation's inverse, and that is what the dialog should promise
 * instead of promising an undo it does not have.
 *
 * ## The grammar is back, and it lives only here
 *
 * [HASHTAG] is the rule the app indexed by until 2026-09-09, restored verbatim from `9f9ba9b^`. What
 * made it expensive was never the regex — it was the regex being *live*, in `Blocks`, in `Segments`
 * and in the editor's text on every keystroke, so that a chord or a heading could silently become a
 * tag, or a line of somebody's song could silently be hidden. Here it runs once, over a set of files
 * the user was shown a count of first, and nothing else in the app calls it. Keep it that way: if a
 * second caller ever appears, the failure mode this file was allowed to reopen comes back with it.
 *
 * Every clause is still carrying a real note in the archive:
 *
 * - *preceded by whitespace or a line start* keeps `F#` out — `Radio (Song Notes).md` reads
 *   "Tarantino für zwei in F# Moll", one note away from a tag called `#` in the drawer.
 * - *then a letter or a digit* keeps `## Strophe` out, five notes using `##` as a heading. The digit
 *   is what lets `#100`, `#50` and `#75` be ordinary tags.
 * - *word characters, `/` or `-`* is the vocabulary: 25 tags, 11 of them nested one level.
 *
 * ## Only whole lines move
 *
 * A line must be *nothing but* tags to be taken. A hashtag inside a sentence is part of the
 * sentence: it is left where it is and it is not filed either, because the app has no way to tell a
 * tag somebody wrote mid-line from a word somebody wrote with a `#` in front of it, and guessing
 * wrong in either direction edits a lyric. 179 of the archive's 179 hashtags sit on lines of their
 * own, so nothing is lost by being strict here.
 *
 * ## Only the heading the note opens with is a title
 *
 * [lift] takes an `# ` heading and only when it is the note's **first non-blank line** and the note
 * has **no `title:` of its own**. Both clauses are load-bearing:
 *
 * - A heading further down is a section of the writing. `Radio (Song Notes).md` runs on `##`
 *   headings for its sections, and a note that opened with prose and used `# Refrain` half way
 *   through would be titled "Refrain" by any looser rule.
 * - A note whose frontmatter already carries a title has said what it is called. The heading may
 *   agree with it, may be an alternative spelling, may be a section that happens to sit at the top —
 *   the app cannot tell, and a note that already answers the question does not get asked it again.
 *   This is what keeps the archive itself, all 168 notes of which are titled, entirely untouched.
 *
 * The heading text is read the way [Blocks] reads it, so what lands in `title:` is exactly the words
 * the reader was already seeing at the top of the note. `#` levels below the first are not titles:
 * the archive uses `##` for sections and this is the shape the export wrote.
 *
 * ## Nothing is stripped until it has landed
 *
 * [plan] builds the new frontmatter, reads the tags and the title back out of it, and only then
 * removes the lines — see [Outcome.Blocked]. A note whose filing could not be written to its
 * frontmatter keeps its body exactly as it was. This is the difference between a migration and
 * losing somebody's filing.
 *
 * ## `updated` does not move
 *
 * The body changes and the timestamp stays. `CLAUDE.md`'s rule is that `updated` tracks the writing,
 * not the filing, and this is filing in its purest form — the same text, in the place the app keeps
 * it. Stamping here would set 165 notes to today and destroy exactly the 2015–2025 span the archive
 * is kept for. `tools/rename-percent-tags.py` made the same call by hand for its rename. The title
 * half is filing by the same measure: giving a note the name it was already showing is not a claim
 * that somebody rewrote it this morning.
 *
 * ## When this runs
 *
 * Never on its own. Not on folder adoption either — rewriting every file in somebody's archive
 * before they have seen a single note is the worst possible moment for it: the folder may be
 * mis-picked, the sync may be mid-flight, and the app has not yet earned the trust. The intended
 * shape is: adopt the folder, list the notes, *then* offer the move with [Survey]'s counts in the
 * sentence, dismissible and re-offerable. The offer is not built yet.
 *
 * Pure Kotlin, no Android. A mistake here corrupts an archive that exists nowhere else.
 */
object Migration {

    /** What counts as an inline hashtag. Restored from `9f9ba9b^`; see the class comment. */
    private val HASHTAG = Regex("""(?<=^|\s)#([\p{L}\d][\w/-]*)""", RegexOption.MULTILINE)

    /** One word, tested on its own — the same rule as [HASHTAG] without the neighbours. */
    private val HASHTAG_WORD = Regex("""#[\p{L}\d][\w/-]*""")

    /**
     * A level-one ATX heading, and the text in it.
     *
     * Deliberately `Blocks`' own heading rule narrowed to one `#`, so the title that lands in the
     * frontmatter is character for character what the reader was seeing rendered as the heading —
     * including a trailing `#`, which CommonMark would strip as a closing sequence and this app's
     * renderer does not. Agreeing with the renderer matters more here than agreeing with the spec:
     * the promise the move makes is that the words move, not that they are also tidied on the way.
     */
    private val TITLE = Regex("""^#\s+(.*)$""")

    /** Whether a single whitespace-delimited word is a hashtag. */
    fun isTagWord(word: String): Boolean = HASHTAG_WORD.matches(word)

    /**
     * Whether a line consists only of hashtags and whitespace.
     *
     * The line must be *entirely* tags — see "Only whole lines move" above. `Ein #Traum von einem
     * Tag` is a sentence, and a migration that took the `#Traum` out of it would be editing a lyric.
     */
    fun isTagLine(line: String): Boolean {
        val words = line.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        return words.isNotEmpty() && words.all(::isTagWord)
    }

    /** What [plan] decided about one note. */
    sealed interface Outcome {

        /** Nothing to move. The note is not written. */
        data object Untouched : Outcome

        /**
         * The note as it would be after the move.
         *
         * [filed] are the tags the frontmatter did not already carry — the ones the drawer gains.
         * [refiled] were already in the list and are only being unwritten from the body, which on
         * this archive is every one of them. [titled] is the title the note gains, or null when it
         * had one already or opens with no heading. [lines] is how many lines of filing the body
         * loses, and [addedBlock] says whether the note had no frontmatter at all and got one.
         */
        data class Move(
            val note: Note,
            val filed: List<String>,
            val refiled: List<String>,
            val titled: String?,
            val lines: Int,
            val addedBlock: Boolean,
        ) : Outcome

        /**
         * The note has filing in its body that could not be written to its frontmatter, so
         * **nothing is done to it** — the body keeps its lines and the user keeps their filing.
         *
         * **No shape this app can parse reaches here today** — a block with no `tags:` key gets one,
         * a block with no `title:` key gets one, and a note with no block at all gets a block — and
         * it is kept anyway, because it is the check that makes stripping safe rather than a case
         * that has come up: [plan] writes the tags and the title, reads them back out and compares,
         * and only a note that passes loses lines. If a survey ever reports one of these, the right
         * response is to look at the file, not to loosen the check.
         */
        data class Blocked(val tags: List<String>, val title: String?) : Outcome
    }

    /**
     * What moving [note]'s inline tags into its frontmatter would produce.
     *
     * Nothing is written here and nothing is decided about *whether* to write; the caller holds
     * both. The note comes back whole so the same bytes that were checked are the bytes that are
     * saved.
     */
    fun plan(note: Note): Outcome {
        // Tags first, then the title, because stripping the tags is what can *make* a note open
        // with its heading: a body that led with `#lyrics/titel` and then `# Adlerohr` has the
        // heading as its first non-blank line only once the tag line is gone. One pass in the other
        // order would file the tags of such a note and leave its title behind.
        val (stripped, removed, found) = strip(note.body)
        val (body, title) = lift(stripped, note.title)
        if (removed == 0 && title == null) return Outcome.Untouched

        val existing = note.frontmatter.tags
        val known = existing.map(Tags::normalize).toSet()
        val filed = found.filterNot { it in known }
        val refiled = found.filter { it in known }
        val lines = removed + if (title == null) 0 else 1

        // A note whose body only repeats what the frontmatter already says needs no frontmatter edit
        // at all — and not touching it is the byte-safe answer, since rewriting a block the user did
        // not change is the prime directive's first prohibition. On today's archive this is every
        // one of the 165 notes.
        if (filed.isEmpty() && title == null) {
            return Outcome.Move(note.copy(body = body), filed, refiled, null, lines, addedBlock = false)
        }

        val wanted = existing + filed
        val frontmatter = if (note.frontmatter.present) {
            // Each key only if it is actually gaining something. A `tags:` block rewritten to say
            // what it already said is a file touched for nothing.
            val tagged = if (filed.isEmpty()) note.frontmatter else note.frontmatter.withTags(wanted)
            if (title == null) tagged else tagged.withKey("title", title)
        } else {
            Frontmatter.forNote(title, wanted)
        }

        // Read it back out of what was just built. Only if all of it is there does the body lose its
        // lines — see Outcome.Blocked.
        val landed = frontmatter.tags.map(Tags::normalize).toSet()
        if (!landed.containsAll(wanted.map(Tags::normalize))) return Outcome.Blocked(found, title)
        if (title != null && frontmatter.title != title) return Outcome.Blocked(found, title)

        val addedBlock = !note.frontmatter.present
        // A created block gets the blank line every note in the archive has under its frontmatter.
        // Only when the app is writing the block itself: a body that already opens with one keeps
        // exactly the one it has.
        val spaced = if (addedBlock && body.isNotEmpty() && !body.startsWith("\n")) "\n$body" else body
        // `updated` is deliberately not stamped. See the class comment.
        return Outcome.Move(Note(frontmatter, spaced), filed, refiled, title, lines, addedBlock)
    }

    /**
     * [body] without the heading it opens with, and the title that heading held.
     *
     * Null and the body unchanged unless every clause holds: the note has no title of its own, its
     * first non-blank line is a level-one ATX heading, and that heading has words in it. See "Only
     * the heading the note opens with is a title" in the class comment for why each one is there.
     *
     * The blank line under the heading goes with it, by [scaffolding]'s rule and for its reason: a
     * heading that opens a note has nothing but blank lines above it, so the gap that held it apart
     * from the words below is the gap that would otherwise be left growing at the top of every note
     * in the folder.
     */
    private fun lift(body: String, existing: String?): Pair<String, String?> {
        if (!existing.isNullOrBlank()) return body to null
        val lines = physicalLines(body)
        val at = lines.indices.firstOrNull { content(lines[it]).isNotBlank() } ?: return body to null
        val title = TITLE.find(content(lines[at]))?.groupValues?.get(1)?.trim()
        if (title.isNullOrEmpty()) return body to null

        val drop = setOf(at) + scaffolding(lines, listOf(at))
        return lines.filterIndexed { i, _ -> i !in drop }.joinToString("") to title
    }

    /** The counts a first-run offer needs before it asks. */
    data class Survey(
        /** Notes looked at. */
        val scanned: Int,
        /** Notes that would be written. */
        val notes: Int,
        /** Lines those notes would lose. */
        val lines: Int,
        /** Of [notes], how many have tags written into their text. */
        val tagged: Int,
        /** Of [notes], how many would gain the title they open with. */
        val titled: Int,
        /** Every tag found in a body, sorted — what the drawer would show afterwards. */
        val tags: List<String>,
        /** Of those, the ones no note carries in its frontmatter today. What the drawer *gains*. */
        val gained: List<String>,
        /** Notes with filing in their body that could not be written, and are therefore left alone. */
        val blocked: Int,
    ) {
        /** Whether there is anything worth asking about. */
        val worthOffering: Boolean get() = notes > 0
    }

    /**
     * Look over a whole folder without changing anything — the detector behind the offer.
     *
     * Cheap enough to run on every folder read: it is one regex over text the app has already
     * parsed. It exists so the dialog can say "tags in 165 of your 168 notes" rather than asking a
     * question the user has no way to size.
     */
    fun survey(notes: Collection<Note>): Survey {
        val plans = notes.map(::plan)
        val moves = plans.filterIsInstance<Outcome.Move>()
        val declared = notes.flatMap { it.tags }.toSet()
        val found = moves.flatMap { it.filed + it.refiled }.toSortedSet()
        return Survey(
            scanned = notes.size,
            notes = moves.size,
            lines = moves.sumOf { it.lines },
            tagged = moves.count { it.filed.isNotEmpty() || it.refiled.isNotEmpty() },
            titled = moves.count { it.titled != null },
            tags = found.toList(),
            gained = found.filterNot { it in declared },
            blocked = plans.count { it is Outcome.Blocked },
        )
    }

    /** [body] without its tag lines, how many went, and the tags they carried in reading order. */
    private fun strip(body: String): Triple<String, Int, List<String>> {
        val lines = physicalLines(body)
        val tagged = lines.indices.filter { isTagLine(content(lines[it])) }
        if (tagged.isEmpty()) return Triple(body, 0, emptyList())

        val drop = tagged.toMutableSet()
        for (run in runs(tagged)) drop += scaffolding(lines, run)

        val tags = tagged
            .flatMap { content(lines[it]).trim().split(Regex("""\s+""")) }
            .map(Tags::normalize)
            .distinct()
        val kept = lines.filterIndexed { i, _ -> i !in drop }.joinToString("")
        return Triple(kept, tagged.size, tags)
    }

    /**
     * The blank line a run of tag lines was sitting on, and which goes with it.
     *
     * A tag line in this archive comes with a blank line holding it apart from the words — above it
     * at the foot of a note, below it at the head. Removing the tags and leaving the gap would grow
     * a blank line into every note in the folder, which is a change the user did not ask for as
     * surely as any other.
     *
     * **Below if everything above the run is blank, above otherwise.** One rule covers all three
     * shapes the archive has: a tag line under the frontmatter keeps that opening blank line and
     * loses the one beneath it, a tag line at the foot loses the one above it, and a note that is
     * nothing but a blank line and `#lyrics/titel` — 39 of them — comes out as the blank line it
     * started with. Nothing is dropped that is not blank, so a note whose tags sit directly against
     * its words simply loses the tags.
     */
    private fun scaffolding(lines: List<String>, run: List<Int>): Set<Int> {
        val openedTheNote = (0 until run.first()).all { content(lines[it]).isBlank() }
        val neighbour = if (openedTheNote) run.last() + 1 else run.first() - 1
        val blank = neighbour in lines.indices && content(lines[neighbour]).isBlank()
        return if (blank) setOf(neighbour) else emptySet()
    }

    /** Consecutive indices grouped. 12 notes carry tag lines in more than one place. */
    private fun runs(indices: List<Int>): List<List<Int>> =
        indices.fold(mutableListOf<MutableList<Int>>()) { runs, i ->
            val last = runs.lastOrNull()
            if (last != null && last.last() == i - 1) last += i else runs += mutableListOf(i)
            runs
        }

    /** Lines with their terminators kept, so rejoining them is concatenation. */
    private fun physicalLines(text: String): List<String> =
        Regex("""[^\n]*\n|[^\n]+""").findAll(text).map { it.value }.toList()

    /** A line without its terminator. */
    private fun content(line: String): String = line.removeSuffix("\n").removeSuffix("\r")
}
