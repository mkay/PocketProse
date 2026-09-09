// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/**
 * Tags: the frontmatter `tags:` list, and what the app makes of it.
 *
 * **One representation.** Until 2026-09-09 tags also lived in bodies as `#hashtags` and the app kept
 * the two in agreement — which was right while the notes were shared with a macOS editor that read
 * hashtags, and stopped being right when they were not. What went with that decision: a hashtag
 * grammar whose every clause was load-bearing (`F#` in `Radio (Song Notes).md`, `## Strophe` as a
 * heading, `#100` as a tag), a `TagEdit` module that spliced hashtags into bodies without disturbing
 * the words, and hide-this-line logic in `Blocks`, `Segments` and the editor's live text. It is all
 * gone, and none of it should come back: see the Tag rules in `CLAUDE.md`.
 *
 * A `#` in a body is now text, and renders as text. 165 notes still carry a leftover hashtag line
 * from the old world; the author will clear them when it suits them, and the app never will.
 *
 * The user still never types or sees a `#` for a tag. Tags reach the screen as chips and the drawer
 * shows them as `tag/subtag`; the hash was only ever storage.
 */
object Tags {

    /**
     * [tags] with [from] renamed to [to], the rename reaching the tag's children.
     *
     * `album` -> `record` takes `album/debut` with it, because the tree is built from path segments
     * and a parent that renamed without its children would split one branch into two. That is also
     * why the match is on a path boundary and not on a prefix: renaming `album` must not touch
     * `albumcover`.
     *
     * **Renaming onto a name that already exists is a merge**, and the `distinct()` here is what
     * performs it — a note carrying both `album` and `record` ends with one `record`. Merging is not
     * undone by renaming back, which is why the caller has to say so before it happens rather than
     * after.
     *
     * Order is preserved: a renamed tag stays where it sat in the note's list rather than moving to
     * the end, so the `tags:` block keeps the shape the author gave it and the diff is one line.
     */
    fun rename(tags: List<String>, from: String, to: String): List<String> =
        tags.map { tag ->
            when {
                tag == from -> to
                tag.startsWith("$from/") -> to + tag.removePrefix(from)
                else -> tag
            }
        }.distinct()

    /** Whether [tag] is [under] it, or is it — the rule the drawer and the filter both count by. */
    fun isUnder(tag: String, under: String): Boolean = tag == under || tag.startsWith("$under/")

    /**
     * Tags are lowercase, and new input is folded on entry.
     *
     * The archive was deliberately case-folded once already; letting a `Lyrics` in beside the
     * existing `lyrics` would split a tag in the drawer and there is no UI that makes that look
     * like anything but a bug.
     */
    fun normalize(tag: String): String = tag.trim().trimStart('#').lowercase()

    /**
     * The same folding, applied to a field while somebody is still typing in it.
     *
     * [normalize] runs when a tag is committed, which meant the fold happened *after* the user had
     * looked away: they typed `Lyrics`, saw `Lyrics`, saved, and found `lyrics`. Small, and exactly
     * the kind of quiet correction that reads as the app having its own ideas. Folding the field as
     * it is typed makes the rule visible at the moment it applies — what is on screen is what will
     * be stored.
     *
     * **[normalize] minus the trailing trim**, and that is the whole difference. Trimming the end on
     * every keystroke would eat the space in `song ideas` before the second word could be typed, so
     * a tag with a space in it would become unwriteable — which is a restriction nobody asked for.
     * The archive has no such tag and the rename dialog refuses one, but the tag sheet accepts them
     * today and this is not the place to change that. The trailing space goes on commit, where
     * [normalize] still has the last word.
     */
    fun typed(tag: String): String = tag.trimStart().trimStart('#').lowercase()

    /**
     * Whether the app will accept [tag] as a *new* name — typed into the tag sheet, or typed into
     * the rename dialog.
     *
     * **A tag with a space in it cannot survive the planned export.** `CLAUDE.md` describes that
     * export as this migration's inverse — the frontmatter tags written back into bodies as
     * hashtags — and it is the whole of the app's undo story. `#man go` is not a tag called
     * `man go`; by [Migration]'s own grammar it is the tag `man` followed by the word `go`. So a
     * spaced tag is one the app can store and can never give back, which is a worse promise than
     * refusing it at the door. A `#` is refused for the same reason, one step more obviously.
     *
     * **This governs entry and nothing else.** A tag that is already in a file stays: it is listed,
     * filtered by, counted, and written back exactly as it was found. Rewriting somebody's
     * frontmatter to enforce a rule the app invented after the fact is the prime directive's first
     * prohibition, and `man go` sitting in the drawer, selectable but not re-creatable, is the
     * correct outcome rather than an inconsistency to tidy away.
     *
     * The tag sheet accepted spaces and the rename dialog refused them, silently, until 2026-09-10 —
     * so a tag could be made and then never renamed, with nothing on screen saying why.
     */
    fun accepts(tag: String): Boolean = tag.isNotEmpty() && ' ' !in tag && '#' !in tag

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
                total = perNote.count { tags -> tags.any { isUnder(it, path) } },
                children = build(counts, perNote, path),
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.segment })
    }
}
