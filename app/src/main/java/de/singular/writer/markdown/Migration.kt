// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/**
 * Moving tags out of a note's text and into its tag list — once, for a folder that was written in
 * an editor where a tag *was* a `#hashtag` in the body.
 *
 * This app keeps tags in the frontmatter and nowhere else (see the Tag rules in `CLAUDE.md`). A
 * folder arriving from an editor that read hashtags therefore looks empty: no tags in the drawer,
 * and a `#lyrics/snippet` sitting in the middle of every note. This file is the one-time move that
 * fixes that, and it is offered to the user rather than performed on them — see **When this runs**.
 *
 * ## It is a move, not a conversion
 *
 * The tag goes into the list and the line it was written on goes away. Filing without stripping
 * would show every tag twice — a chip at the top and a hashtag in the text — which is worse than
 * doing nothing at all, so the two halves are one operation and there is no switch between them.
 *
 * It is also reversible in principle rather than by backup: the planned export ("write the tag list
 * back into the body as hashtags") is this operation's inverse, and that is what the dialog should
 * promise instead of promising an undo it does not have.
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
 * ## Nothing is stripped until the tag has landed
 *
 * [plan] builds the new frontmatter, reads the tags back out of it, and only then removes the lines
 * — see [Outcome.Blocked]. A note whose tag could not be written to its frontmatter keeps its body
 * exactly as it was. This is the difference between a migration and losing somebody's filing.
 *
 * ## `updated` does not move
 *
 * The body changes and the timestamp stays. `CLAUDE.md`'s rule is that `updated` tracks the writing,
 * not the filing, and this is filing in its purest form — the same text, in the place the app keeps
 * it. Stamping here would set 165 notes to today and destroy exactly the 2015–2025 span the archive
 * is kept for. `tools/rename-percent-tags.py` made the same call by hand for its rename.
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
         * this archive is every one of them. [lines] is how many lines the body loses, and
         * [addedBlock] says whether the note had no frontmatter at all and got one.
         */
        data class Move(
            val note: Note,
            val filed: List<String>,
            val refiled: List<String>,
            val lines: Int,
            val addedBlock: Boolean,
        ) : Outcome

        /**
         * The note has tags in its body that could not be written to its frontmatter, so **nothing
         * is done to it** — the body keeps its lines and the user keeps their filing.
         *
         * **No shape this app can parse reaches here today** — a block with no `tags:` key gets one
         * and a note with no block at all gets a block — and it is kept anyway, because it is the
         * check that makes stripping safe rather than a case that has come up: [plan] writes the
         * tags, reads them back out and compares, and only a note that passes loses lines. If a
         * survey ever reports one of these, the right response is to look at the file, not to loosen
         * the check.
         */
        data class Blocked(val tags: List<String>) : Outcome
    }

    /**
     * What moving [note]'s inline tags into its frontmatter would produce.
     *
     * Nothing is written here and nothing is decided about *whether* to write; the caller holds
     * both. The note comes back whole so the same bytes that were checked are the bytes that are
     * saved.
     */
    fun plan(note: Note): Outcome {
        val (body, removed, found) = strip(note.body)
        if (removed == 0) return Outcome.Untouched

        val existing = note.frontmatter.tags
        val known = existing.map(Tags::normalize).toSet()
        val filed = found.filterNot { it in known }
        val refiled = found.filter { it in known }

        // A note whose body only repeats what the list already says needs no frontmatter edit at
        // all — and not touching it is the byte-safe answer, since rewriting a `tags:` block the
        // user did not change is the prime directive's first prohibition. On today's archive this
        // is every one of the 165 notes.
        if (filed.isEmpty()) {
            return Outcome.Move(note.copy(body = body), filed, refiled, removed, addedBlock = false)
        }

        val wanted = existing + filed
        val frontmatter = if (note.frontmatter.present) {
            note.frontmatter.withTags(wanted)
        } else {
            Frontmatter.forTags(wanted)
        }

        // Read the tags back out of what was just built. Only if they are all there does the body
        // lose its lines — see Outcome.Blocked.
        val landed = frontmatter.tags.map(Tags::normalize).toSet()
        if (!landed.containsAll(wanted.map(Tags::normalize))) return Outcome.Blocked(found)

        val addedBlock = !note.frontmatter.present
        // A created block gets the blank line every note in the archive has under its frontmatter.
        // Only when the app is writing the block itself: a body that already opens with one keeps
        // exactly the one it has.
        val spaced = if (addedBlock && body.isNotEmpty() && !body.startsWith("\n")) "\n$body" else body
        // `updated` is deliberately not stamped. See the class comment.
        return Outcome.Move(Note(frontmatter, spaced), filed, refiled, removed, addedBlock)
    }

    /** The counts a first-run offer needs before it asks. */
    data class Survey(
        /** Notes looked at. */
        val scanned: Int,
        /** Notes that would be written. */
        val notes: Int,
        /** Lines those notes would lose. */
        val lines: Int,
        /** Every tag found in a body, sorted — what the drawer would show afterwards. */
        val tags: List<String>,
        /** Of those, the ones no note carries in its frontmatter today. What the drawer *gains*. */
        val gained: List<String>,
        /** Notes with body tags that could not be filed, and are therefore left alone. */
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
