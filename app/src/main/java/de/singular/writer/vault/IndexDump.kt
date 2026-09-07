// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

import android.content.Context
import de.singular.writer.BuildConfig
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A debug-build dump of what the app thinks is in the folder, written to app-private storage.
 *
 * This exists so the acceptance tests in `CLAUDE.md` can be checked by reading a file rather than
 * by driving the UI and taking photographs of it:
 *
 *     adb shell run-as de.singular.writer cat files/index-dump.txt
 *
 * Crystal Ball settles its persistence questions the same way, and the reasoning is the same: output
 * that needs no human to interpret is output worth verifying mechanically, every time, instead of
 * once by eye. A screenshot proves a screen drew; it does not prove that 168 notes parsed, that four
 * files titled "Wer geht vor?" stayed four notes, or that every byte can be written back.
 *
 * **Debug builds only.** [write] returns immediately on a release build, so nothing here reaches a
 * user's phone. It writes to `filesDir`, which is app-private and no part of the user's folder — the
 * app must never leave a file of its own beside the notes.
 */
object IndexDump {

    private const val FILE = "index-dump.txt"

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"))

    /** Write the dump. Cheap, and a no-op outside a debug build. */
    fun write(context: Context, index: NoteIndex) {
        if (!BuildConfig.DEBUG) return
        val out = StringBuilder()

        out.appendLine("notes: ${index.size}")
        val failed = index.notes.filterNot { it.roundTrips }.map { it.file.name }
        out.appendLine("round-trip failures: ${failed.size}${if (failed.isEmpty()) "" else " -> $failed"}")
        out.appendLine("distinct tags: ${index.allTags.size}")
        out.appendLine("untagged notes: ${index.notes.count { it.tags.isEmpty() }}")
        out.appendLine("notes with no prose: ${index.notes.count { it.excerpt.isEmpty() }}")
        out.appendLine("notes with no parseable created date: ${index.notes.count { it.created == null }}")

        out.appendLine()
        out.appendLine("== tag tree ==")
        fun node(n: de.singular.writer.markdown.Tags.Node, depth: Int) {
            out.appendLine("  ".repeat(depth + 1) + "${n.segment}  self=${n.count} total=${n.total}")
            n.children.forEach { node(it, depth + 1) }
        }
        index.tagTree.forEach { node(it, 0) }

        out.appendLine()
        out.appendLine("== duplicate titles ==")
        index.notes.groupBy { it.title }.filterValues { it.size > 1 }.forEach { (title, notes) ->
            out.appendLine("  $title -> ${notes.size}: ${notes.map { it.file.name }}")
        }

        out.appendLine()
        out.appendLine("== notes, newest first ==")
        for (n in index.notes) {
            val date = n.created?.let(DATE::format) ?: "??????????"
            val tags = if (n.tags.isEmpty()) "-" else n.tags.joinToString(",")
            val excerpt = n.excerpt.take(60).replace("\n", " ").ifEmpty { "<no prose>" }
            out.appendLine("  $date  ${n.title}  [$tags]  $excerpt")
            out.appendLine("          file=${n.file.name}")
        }

        runCatching { File(context.filesDir, FILE).writeText(out.toString()) }
    }
}
