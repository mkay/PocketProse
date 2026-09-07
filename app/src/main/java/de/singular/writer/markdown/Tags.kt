// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/**
 * Tags, in the two forms the archive keeps them in: a list in the frontmatter, and `#hashtags`
 * written inline in the body.
 *
 * Both exist on purpose and the app keeps them in agreement. All 168 notes already agree — every
 * inline hashtag has a frontmatter entry and vice versa — so the job here is to *preserve* an
 * invariant that holds, not to reconcile drift. Do not build merge logic for a conflict the data
 * does not have.
 *
 * That was true only of the tags that *could* be written inline until 2026-09-07, when `100%`, `50%`
 * and `75%` were renamed to `100`, `50` and `75` across the archive — see
 * `tools/rename-percent-tags.py`. `%` is not a character a hashtag can hold, which is why those
 * three were frontmatter-only and why the export lost 21 of them; without the `%` they are ordinary
 * tags and the exception is gone from this file and from every screen.
 *
 * The user never sees a `#`. Tags reach the screen as chips and the drawer shows them as
 * `tag/subtag`; the hash is storage, and storage is not this app's subject.
 */
object Tags {

    /**
     * What counts as an inline hashtag.
     *
     * `#`, then a **letter or a digit**, then word characters, `/` or `-`, and the `#` must be at the
     * start of a line or preceded by whitespace. Every clause is carrying a real note in the archive:
     *
     * - *preceded by whitespace* keeps `F#` out. `Radio (Song Notes).md` reads "Tarantino für zwei
     *   in F# Moll", and that is the only sharp in 168 notes — one note away from a tag called
     *   `#` appearing in the drawer. It is also what would keep `F#m` and `C#` out if the author
     *   ever writes a chord sheet in text rather than in chord diagrams.
     * - *then a letter or digit* keeps `## Strophe` out, five notes using `##` as a heading. The
     *   digit is what lets `#100`, `#50` and `#75` be ordinary tags. It was a letter only until the
     *   `%` was dropped from those three names, and it is safe because those 33 hashtags are the
     *   only `#digit` sequences anywhere in the archive — nothing else is caught by widening it.
     * - *word characters, `/` or `-`* is the whole vocabulary: 24 tags, 11 of them nested one level.
     *
     * Test every clause when you touch this. The failure mode is silent — a tag that should not
     * exist appears in the drawer, or one that should is missing from it, and neither shows up as
     * an error anywhere.
     */
    private val HASHTAG = Regex("""(?<=^|\s)#([\p{L}\d][\w/-]*)""", RegexOption.MULTILINE)

    /** One word, tested on its own — the same rule as [HASHTAG] without the neighbours. */
    private val HASHTAG_WORD = Regex("""#[\p{L}\d][\w/-]*""")

    /** Whether a single whitespace-delimited word is a tag. Used to decide whether a line is tags. */
    fun isTagWord(word: String): Boolean = HASHTAG_WORD.matches(word)

    /**
     * Whether a whole line consists only of tags and whitespace.
     *
     * Lived in `Blocks` until phase 6, which needed the same answer in `Segments` and in `TagEdit`.
     * Two definitions of what a tag line is would be two chances to hide a line of somebody's song,
     * so there is one, here, beside the rule it is built from.
     *
     * The line must be *entirely* tags. A hashtag inside a sentence leaves the sentence visible,
     * because hiding words the user wrote would be unforgivable.
     */
    fun isTagLine(line: String): Boolean {
        val words = line.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        return words.isNotEmpty() && words.all(::isTagWord)
    }

    /**
     * Whether [tag] can be written into a body as a hashtag.
     *
     * Every tag in the archive can, since the `%` names were renamed. It stays because the rule and
     * the vocabulary are not the same thing: a tag typed with a space or a `%` in it would be
     * writable to the frontmatter and not to the body, and `TagEdit` must know which before it
     * writes rather than after.
     */
    fun isInlineWritable(tag: String): Boolean = HASHTAG.matches("#$tag")

    /** Every inline hashtag in [body], in the order it appears, without the `#`. */
    fun inBody(body: String): List<String> =
        HASHTAG.findAll(body).map { it.groupValues[1] }.toList()

    /**
     * Tags are lowercase, and new input is folded on entry.
     *
     * The archive was deliberately case-folded once already; letting a `Lyrics` in beside the
     * existing `lyrics` would split a tag in the drawer and there is no UI that makes that look
     * like anything but a bug.
     */
    fun normalize(tag: String): String = tag.trim().trimStart('#').lowercase()

    /**
     * A node in the tag tree the drawer shows.
     *
     * [count] is how many notes carry this exact tag, which is not the same as how many carry
     * something under it — `album` has three children and no note of its own.
     */
    data class Node(
        val segment: String,
        val path: String,
        val count: Int,
        /**
         * Distinct notes at this tag or anywhere below it — what the drawer puts beside the name.
         *
         * **Counted, not summed.** Adding up the children double-counts every note that carries two
         * tags under one parent, and the archive is full of them: summing gave `lyrics` a total of
         * 185 against 168 notes in existence, because a note tagged `lyrics/snippet` *and*
         * `lyrics/titel` was counted once for each. A drawer that claims more notes under a tag than
         * the folder contains is not a rounding error, it is a number nobody can trust.
         */
        val total: Int,
        val children: List<Node>,
    )

    /**
     * Build the tree from the tags actually in use.
     *
     * **From path segments, never from the existence of a parent.** `album/debut` is a child of
     * `album` even though no note is tagged with a bare `album`, and the same goes for `lyrics` —
     * four notes carry it, but its five children would need it regardless. A tree built by looking
     * for parent tags would lose every nested tag in the archive.
     *
     * **Sorted alphabetically**, at every level, and the counts carry the weight instead.
     *
     * The distribution is extremely skewed: `lyrics/snippet` alone covers 131 of 168 notes and 8 of
     * the 24 tags are on a single note each. Frequency order was the obvious answer to that and is
     * the wrong one, because it puts the least useful filter in the most prominent slot — selecting
     * a tag that matches 78% of the archive reads as no filter at all.
     *
     * What makes alphabetical safe here is that the whole tree fits on one screen: 14 top-level
     * entries and 11 children. Nothing is buried, so ranking buys nothing and predictability — a tag
     * always being where you last saw it — buys a great deal. Revisit this only if the tree grows
     * past a screen, which for a 24-tag archive it will not.
     */
    fun tree(tagsPerNote: List<List<String>>): List<Node> {
        val perNote = tagsPerNote.map { it.distinct().toSet() }
        val counts = HashMap<String, Int>()
        for (tags in perNote) for (tag in tags) counts.merge(tag, 1, Int::plus)
        return build(counts, perNote, prefix = "")
    }

    private fun build(counts: Map<String, Int>, perNote: List<Set<String>>, prefix: String): List<Node> {
        val depth = if (prefix.isEmpty()) 0 else prefix.count { it == '/' } + 1
        val segments = counts.keys
            .filter { prefix.isEmpty() || it.startsWith("$prefix/") }
            .mapNotNull { it.split('/').getOrNull(depth) }
            .distinct()
        return segments.map { segment ->
            val path = if (prefix.isEmpty()) segment else "$prefix/$segment"
            Node(
                segment = segment,
                path = path,
                count = counts[path] ?: 0,
                total = perNote.count { tags -> tags.any { it == path || it.startsWith("$path/") } },
                children = build(counts, perNote, path),
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.segment })
    }
}
