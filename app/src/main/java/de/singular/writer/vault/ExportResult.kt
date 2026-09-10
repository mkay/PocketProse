// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

/**
 * What came of writing the folder to a zip.
 *
 * Either the whole thing was written or nothing usable was: a zip is one stream, so a failure part
 * way is a truncated file, which the caller deletes rather than leaving a half-archive for somebody
 * to unpack later and trust.
 */
sealed interface ExportResult {

    /**
     * Written and closed. [notes] and [attachments] are what went in; [verbatim] is how many of the
     * notes were copied byte for byte *although* the inline switch was on, because the parser could
     * not reproduce them and transforming a note the app has misread is how a copy stops being one.
     * Zero unless the switch was on and a note was in that state.
     */
    data class Exported(val notes: Int, val attachments: Int, val verbatim: Int) : ExportResult

    /** Nothing usable was written; the destination was removed. */
    data class Failed(val reason: String) : ExportResult
}
