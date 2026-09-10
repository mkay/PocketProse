// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder

/**
 * Finds the files a note points at, and turns the pictures among them into bitmaps.
 *
 * A Markdown link is a *relative path*, and SAF has no paths — only document ids in a tree. So every
 * `![](attachments/casablanca-chords-01.png)` has to be walked segment by segment from the granted
 * folder, matching names as it goes. `CLAUDE.md` calls this the requirement that killed the previous
 * candidate, so it is treated as a hard acceptance criterion rather than a nicety.
 *
 * Three things it must get right, each of which the archive contains an example of:
 *
 * - **Both layouts.** `attachments/x.png` and a flat sibling `x.png` are both valid and both occur —
 *   the fixture folder has a note of each.
 * - **Percent-encoding.** The export wrote `Wer%20geht%20vor/Pasted%20Graphic%2012.pdf`, so a
 *   path is decoded before it is walked.
 * - **A target that is not there.** Those same links point at a folder the archive does not contain:
 *   24 dead links across three notes. A missing file is an ordinary answer here — null — never an
 *   error, and never something to repair. See [resolve].
 */
class Attachments(private val vault: Vault, context: Context) {

    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver

    /**
     * Decoded bitmaps, by document uri.
     *
     * 4 MB, which is a great deal for this archive — its 19 PNGs are 151×164 and decode to about
     * 100 KB each, so the whole set fits with room to spare. Sized in bytes rather than in entries
     * because a note the user adds a photograph to would otherwise blow the budget silently.
     */
    private val bitmaps = object : LruCache<String, ImageBitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** Folder listings by document id, so walking ten image paths is one query, not ten. */
    private val listings = HashMap<String, Map<String, Uri>>()

    /**
     * The document a relative [path] points at, or **null if there is nothing there**.
     *
     * Null is the expected answer for the 24 links into `Wer geht vor/`, a folder the export
     * referred to and the archive does not contain. The app shows such a link as a link, says
     * plainly that the file is missing, and does not touch it: repairing a link means guessing which
     * file was meant, and guessing wrong is worse than a dead link the user can see.
     *
     * Absolute URLs are not this method's business and come back null too — they are opened by the
     * browser, not read from the folder.
     */
    suspend fun resolve(path: String): Uri? = withContext(Dispatchers.IO) {
        if (path.isBlank() || isAbsoluteUrl(path)) return@withContext null
        val tree = vault.root ?: return@withContext null
        var folder = vault.rootFolder() ?: return@withContext null

        val parts = decode(path).split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return@withContext null

        for ((i, part) in parts.withIndex()) {
            val child = childrenOf(tree, folder)[Vault.normalizedName(part).lowercase()]
                ?: return@withContext null
            if (i == parts.lastIndex) return@withContext child
            folder = child
        }
        null
    }

    /** [uri] decoded, from the cache when it is there. Null if it is not an image we can read. */
    suspend fun bitmap(uri: Uri): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = uri.toString()
        bitmaps.get(key)?.let { return@withContext it }
        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return@withContext null
        decoded.asImageBitmap().also { bitmaps.put(key, it) }
    }

    /**
     * Hand [uri] to whatever app the user has for that kind of file.
     *
     * A read permission is granted along with it, because the receiving app has no rights to our
     * tree. `CLAUDE.md` requires the seven PDFs in `Wer geht vor.md` to open in an external viewer,
     * and this app has no business rendering a PDF itself.
     */
    fun openExternally(uri: Uri, mimeType: String?): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Hand [uri] to an app the user picks, as one file — the note as it sits in the folder.
     *
     * The same grant as [openExternally], carried on a [ClipData] as well because the chooser only
     * forwards a grant it finds there. One file only: a share sheet full of an archive is the bulk
     * gesture this app does not make, and the export is the way to move the folder.
     */
    fun share(uri: Uri, mimeType: String?): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType ?: "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            null,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** A browser intent for a link that was already a URL. */
    fun openUrl(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The MIME type a provider reports for [uri], for choosing a viewer. */
    suspend fun mimeTypeOf(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching { resolver.getType(uri) }.getOrNull()
    }

    /**
     * The children of [folder], keyed by normalised lowercase name.
     *
     * Normalised because the archive came off a Mac: a name can arrive decomposed — `Müde` as `u`
     * plus a combining diaeresis — and compare unequal to the composed spelling that is written in
     * the link. Lowercased because the sync client and the filesystem do not agree about case
     * either, and an image that fails to appear because of a capital letter would look like a
     * missing file.
     */
    private fun childrenOf(tree: Uri, folder: Uri): Map<String, Uri> {
        val id = runCatching { DocumentsContract.getDocumentId(folder) }.getOrNull() ?: return emptyMap()
        listings[id]?.let { return it }

        val children = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        }.getOrNull() ?: return emptyMap()
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        val out = HashMap<String, Uri>()
        runCatching { resolver.query(children, columns, null, null, null) }.getOrNull()?.use {
            while (it.moveToNext()) {
                val childId = it.getString(0) ?: continue
                val name = it.getString(1) ?: continue
                out[Vault.normalizedName(name).lowercase()] =
                    DocumentsContract.buildDocumentUriUsingTree(tree, childId)
            }
        }
        listings[id] = out
        return out
    }

    /** Drop the cached folder listings, so a synced-in image is found on the next look. */
    fun forget() {
        listings.clear()
        bitmaps.evictAll()
    }

    companion object {
        /** Whether a link target is a URL rather than something in the folder. */
        fun isAbsoluteUrl(path: String): Boolean =
            Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://""").containsMatchIn(path) || path.startsWith("mailto:")

        /**
         * A link target as a filename.
         *
         * Markdown percent-encodes spaces, and the export leaned on it heavily:
         * `Wer%20geht%20vor/Pasted%20Graphic%2012.pdf`. Decoding is done per path segment so that an
         * encoded slash — which would be part of a name, not a separator — cannot invent a folder.
         */
        fun decode(path: String): String = path.split('/').joinToString("/") { part ->
            runCatching { URLDecoder.decode(part, "UTF-8") }.getOrDefault(part)
        }
    }
}
