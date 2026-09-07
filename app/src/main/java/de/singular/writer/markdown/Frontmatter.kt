// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

/**
 * The block of keys at the very top of a note, and the single most dangerous thing in this app.
 *
 * **The raw text is the truth and the parsed values are a view onto it.** [raw] holds the block
 * exactly as it was read — both `---` delimiters, every line between them, their quoting, their
 * order, their line endings, and any key this app has never heard of. Rendering a note is
 * [raw] + body with no reassembly step, so a note that is opened and not edited comes back out of
 * the app byte-identical by construction rather than by care.
 *
 * That is the whole design. Every editor the author tried before this one parsed the block into a
 * map and serialised the map back, and every one of them reordered keys, changed `"..."` to `...`,
 * dropped what it did not understand, or added an id of its own. Doing that here would fail
 * `CLAUDE.md`'s prime directive on the first file opened. If you find yourself writing a function
 * that builds a frontmatter block out of a data class, stop — that function is the bug.
 *
 * Changing a value is therefore surgery on one line ([withKey], [withTags]) and never a rewrite.
 *
 * Pure Kotlin: no Android here, on purpose. A round-trip bug in this file destroys an archive that
 * exists nowhere else, so it is tested on the JVM against all 168 real notes rather than on a phone.
 */
class Frontmatter private constructor(
    /** The block as it was read, delimiters included, or "" when the note has no frontmatter. */
    val raw: String,
) {

    /** Whether the note actually opened with a frontmatter block. */
    val present: Boolean get() = raw.isNotEmpty()

    /**
     * The lines between the delimiters, without their terminators. Empty when [present] is false.
     *
     * Found by locating the *closing* delimiter rather than by counting back from the end. A block
     * ends with a newline, so `raw.lines()` hands back a trailing empty string, and dropping "the
     * last element" leaves the closing `---` in the list — which then gets re-emitted a second time
     * by [of] the moment anything is edited. That was a real bug, caught by the round-trip test.
     */
    private val lines: List<String> by lazy {
        if (!present) return@lazy emptyList()
        val all = raw.lines()
        val close = all.indexOfLast { it.trimEnd('\r') == "---" }
        if (close <= 0) emptyList() else all.subList(1, close)
    }

    /**
     * The line ending this block was written with, so an edit does not silently convert a file.
     *
     * The archive is LF throughout and `CLAUDE.md` says so, but a note that arrives from elsewhere
     * with CRLF must come back out with CRLF — rewriting every line of a file the user did not ask
     * to touch is exactly the kind of gratuitous change the prime directive forbids.
     */
    private val terminator: String get() = if (raw.contains("\r\n")) "\r\n" else "\n"

    /**
     * The value of a scalar key, unquoted, or null if the key is absent.
     *
     * Only top-level keys, and only on the line the key itself is on. This is not a YAML parser and
     * must not become one: the archive uses four keys in one shape, and the correct response to a
     * note that is more complicated than that is to preserve it untouched, not to understand it.
     */
    fun value(key: String): String? = lines
        .firstOrNull { it.startsWith("$key:") && !it.startsWith(" ") }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(::unquote)

    /** `title`, authoritative, and never derived from the filename or from a heading. */
    val title: String? get() = value("title")

    /** `created` as the raw ISO string. Read on import; never written. */
    val created: String? get() = value("created")

    /** `updated` as the raw ISO string. Written only on a real content change. */
    val updated: String? get() = value("updated")

    /**
     * The `tags` list.
     *
     * Two shapes exist in the archive and both are handled: a block list of `  - "tag"` lines under
     * a bare `tags:`, and the literal `tags: []` that the three untagged notes carry. A missing
     * `tags` key and an empty list are the same answer here — no tags — but they are *not* the same
     * bytes, and [withTags] keeps whichever the file already had.
     */
    val tags: List<String>
        get() {
            val at = lines.indexOfFirst { it.startsWith("tags:") }
            if (at < 0) return emptyList()
            val inline = lines[at].substringAfter(':').trim()
            if (inline == "[]") return emptyList()
            if (inline.startsWith("[")) {
                return inline.removeSurrounding("[", "]")
                    .split(',')
                    .map { unquote(it.trim()) }
                    .filter { it.isNotEmpty() }
            }
            return lines.drop(at + 1)
                .takeWhile { it.startsWith(" ") || it.startsWith("\t") }
                .mapNotNull { line ->
                    line.trim().takeIf { it.startsWith("- ") }?.removePrefix("- ")?.let(::unquote)
                }
        }

    /**
     * This block with one scalar key's value replaced, or added if it was absent.
     *
     * The line's own indentation, its key, its colon and the spacing after it are all kept; only the
     * value changes, and it is quoted the way the line already quoted it. A key that was written
     * `title:  "x"` with two spaces stays that way, because the app has no business tidying it.
     *
     * A new key is appended after the last line, which is the only placement that cannot disturb the
     * order of what is already there.
     */
    fun withKey(key: String, value: String): Frontmatter {
        if (!present) return this
        val at = lines.indexOfFirst { it.startsWith("$key:") && !it.startsWith(" ") }
        val updated = if (at >= 0) {
            val line = lines[at]
            val head = line.substring(0, line.indexOf(':') + 1)
            val spacing = line.substring(head.length).takeWhile { it == ' ' }
            val old = line.substring(head.length + spacing.length)
            lines.toMutableList().also { it[at] = head + spacing + requote(old, value) }
        } else {
            lines + "$key: ${quoteLike(lines.firstOrNull { it.startsWith("title:") }, value)}"
        }
        return of(updated)
    }

    /**
     * This block with the `tags` list replaced.
     *
     * The shape is preserved: a note that had a block list keeps a block list, one that had
     * `tags: []` keeps the inline form when the new list is empty too, and the indentation and
     * quoting of the existing entries are copied onto the new ones. A note whose tags all went away
     * collapses to `tags: []`, which is what the three untagged notes in the archive look like — the
     * app writes the form the archive already uses rather than inventing a third.
     */
    fun withTags(newTags: List<String>): Frontmatter {
        if (!present) return this
        val at = lines.indexOfFirst { it.startsWith("tags:") }
        if (at < 0) return this
        val existing = lines.drop(at + 1)
            .takeWhile { it.startsWith(" ") || it.startsWith("\t") }
        val indent = existing.firstOrNull()?.takeWhile { it == ' ' || it == '\t' } ?: "  "
        val sample = existing.firstOrNull()?.trim()?.removePrefix("- ")
        val head = lines[at].substringBefore(':') + ":"

        val rebuilt = when {
            newTags.isEmpty() -> listOf("$head []")
            else -> listOf(head) + newTags.map { "$indent- " + requote(sample ?: "\"\"", it) }
        }
        return of(lines.subList(0, at) + rebuilt + lines.drop(at + 1 + existing.size))
    }

    /** Rebuild a block from its inner lines, restoring the delimiters and this block's terminator. */
    private fun of(inner: List<String>): Frontmatter = Frontmatter(
        (listOf("---") + inner + listOf("---")).joinToString(terminator) + terminator,
    )

    override fun toString(): String = raw

    companion object {
        /**
         * Split [text] into its frontmatter block and everything after it.
         *
         * The block is recognised **only at the very top of the file** and only up to the first
         * closing `---` line. That restriction is load-bearing: five notes in the archive use `---`
         * inside the body as a horizontal rule, and a parser that scanned for delimiters anywhere
         * would eat half of one of those notes as metadata.
         *
         * A note with no frontmatter at all is fine and common enough to expect — someone drops a
         * plain text file into the folder — and comes back with [present] false and the whole text
         * as the body.
         */
        fun split(text: String): Pair<Frontmatter, String> {
            val opener = when {
                text.startsWith("---\n") -> 4
                text.startsWith("---\r\n") -> 5
                else -> return Frontmatter("") to text
            }
            var i = opener
            while (i <= text.length) {
                val eol = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                val line = text.substring(i, eol).trimEnd('\r')
                if (line == "---") {
                    val after = (eol + 1).coerceAtMost(text.length)
                    return Frontmatter(text.substring(0, after)) to text.substring(after)
                }
                if (eol >= text.length) break
                i = eol + 1
            }
            // An opening delimiter with no closing one is not frontmatter — it is a note that
            // happens to start with a rule. Treating it as an unterminated block would swallow the
            // entire file.
            return Frontmatter("") to text
        }

        /** Strip one layer of matching quotes, if the value has them. */
        private fun unquote(value: String): String = when {
            value.length >= 2 && value.startsWith('"') && value.endsWith('"') -> value.substring(1, value.length - 1)
            value.length >= 2 && value.startsWith('\'') && value.endsWith('\'') -> value.substring(1, value.length - 1)
            else -> value
        }

        /** [value] quoted the way [old] was quoted — the app never changes a value's quoting style. */
        private fun requote(old: String, value: String): String = when {
            old.startsWith('"') -> "\"" + escape(value) + "\""
            old.startsWith('\'') -> "'" + value.replace("'", "''") + "'"
            else -> value
        }

        /** Quoting for a key being added, matched to how `title` is written in the same block. */
        private fun quoteLike(titleLine: String?, value: String): String =
            requote(titleLine?.substringAfter(':')?.trim() ?: "\"\"", value)

        private fun escape(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
