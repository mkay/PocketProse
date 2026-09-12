// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import de.singular.writer.markdown.Live
import de.singular.writer.markdown.Scratch
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import de.singular.writer.markdown.FormatActions
import de.singular.writer.markdown.ImageRef
import de.singular.writer.markdown.LinkRef
import de.singular.writer.markdown.Segment
import de.singular.writer.markdown.Segments
import de.singular.writer.markdown.Tags
import de.singular.writer.vault.Attachments
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * A note as the editor holds it: its segments, and a live buffer for each editable one.
 *
 * The note is cut at its image lines (see [Segments]) so pictures can be drawn between text fields,
 * a text field being unable to contain a composable. 165 of the archive's 168 notes have no images
 * and therefore exactly one buffer, which is the same thing the editor had before this existed.
 *
 * [body] reassembles the note. Segments that are not text hand back their original bytes untouched,
 * so an image line survives being scrolled past exactly as it was written.
 *
 * The one thing the editor does not show is the blank line under the frontmatter — see
 * [opensOnBlankLine].
 *
 * [name] is the file this document was built from, and it is checked before every write. A document
 * is an editor's worth of unsaved state; writing one into a different note's file would put one
 * song into another's, and the only way to be sure that cannot happen is to carry the identity
 * around with the state rather than to reason about which composition holds what.
 */
class NoteDocument(val name: String, body: String, tags: List<String>, title: String) {

    /**
     * Whether the note's body opens on the blank line that separates it from the frontmatter.
     *
     * 167 of the archive's 168 notes do, because a note's body begins immediately after the closing
     * `---` and every note in the folder has a gap there. Shown as written, that gap is an empty
     * first line sitting in the text field above the words — and the editor keeps the title in a
     * field of its own, so it is not separating anything from anything. It made sense while a note's
     * name was a heading in the body; with the name lifted out it is a blank line at the top of a
     * song.
     *
     * So exactly one leading newline is hidden here and exactly one is put back in [body]. The
     * symmetry is the whole of it, and it is the same bargain `Note.keepTrailingNewline` strikes at
     * the other end of the file: a property the file already had is restored, never imposed. A note
     * that arrived without the gap is shown and saved without it, and a blank line the author
     * actually types is a *second* one, kept as typed — they see one, the file holds two, and
     * opening a note and closing it still writes nothing.
     *
     * This hides no writing. The frontmatter's own separator is the only line it can ever take, and
     * it is a byte of structure rather than a byte of the song — unlike the tag lines the app used
     * to hide, which were somebody's text.
     */
    private val opensOnBlankLine: Boolean = body.startsWith("\n")

    val segments: List<Segment> = Segments.split(if (opensOnBlankLine) body.substring(1) else body)

    /**
     * The note's title, live, as the field at the top of the page holds it.
     *
     * A buffer like the prose ones and saved on the same occasions, so retyping a title costs no
     * more writes than retyping a line does. It is the frontmatter's `title` and never a heading in
     * the body: no note in the archive carries an ATX heading and this must not write the first one.
     *
     * A blank one is somebody halfway through retyping, not an instruction to erase the title —
     * `Note.withTags` is where that is decided, and it leaves the old one alone.
     */
    val titleBuffer: TextFieldState = TextFieldState(title)

    /** The title as it stands, for the save path. */
    fun title(): String = titleBuffer.text.toString()

    /** Whether any stretch of the note has a line the draft view would leave out. See `Scratch`. */
    fun hasScratch(): Boolean = segments.indices.any { i ->
        segments[i] is Segment.Prose && Scratch.LINE.containsMatchIn(bufferAt(i).text)
    }

/**
     * The tags on this note, as the chip row shows them and the sheet edits them.
     *
     * Every tag the note carries, in either of the two places the archive keeps them — there is one
     * list here because there is one thing a reader means by "this note's tags". Which of the two
     * places a given tag currently sits in is `Note.withTags`' business, and the author's never.
     */
    var tags: List<String> by mutableStateOf(tags)
        private set

    /** Replace the editable set, folding new input to lowercase as `Tags.normalize` requires. */
    fun retag(newTags: List<String>) {
        tags = newTags.map(Tags::normalize).distinct()
    }

    /**
     * Put a tag on this note, or take it off.
     *
     * Nothing reaches disk here. The set is written on the same occasions the body is — leaving the
     * editor, or the app going to the background — so a mind changed twice inside the sheet costs
     * no writes at all, and `Note.withTags` still makes a set that ended where it started a no-op.
     *
     * A new tag goes on the end. The order is the file's own and the app has no business sorting a
     * list the user only added to.
     */
    fun toggleTag(tag: String) {
        val normalized = Tags.normalize(tag)
        retag(if (normalized in tags) tags - normalized else tags + normalized)
    }


    private val buffers: Map<Int, TextFieldState> = segments.withIndex()
        .filter { it.value is Segment.Prose }
        .associate { (i, segment) -> i to TextFieldState(segment.raw) }

    fun bufferAt(index: Int): TextFieldState = buffers.getValue(index)

    /**
     * Image lines as they stand after pictures were removed from them, by segment index.
     *
     * The segment list is fixed for the life of the document — the buffers are keyed by it — so a
     * removal does not take the segment out; it records the line's new bytes here, and [body]
     * writes those instead. The editor then saves and rebuilds, exactly as it does after inserting
     * a picture, and the rebuilt document has no such line to begin with.
     */
    private val rewritten = mutableStateMapOf<Int, String>()

    /** The image line at [index] as it should currently be shown, or null once nothing is left. */
    fun imagesAt(index: Int): Segment.Images? {
        val segment = segments[index] as Segment.Images
        val raw = rewritten[index] ?: return segment
        return Segments.split(raw).filterIsInstance<Segment.Images>().firstOrNull()
    }

    /** Take [ref]'s link off the image line at [index]. Nothing reaches disk until the next save. */
    fun removeImage(index: Int, ref: ImageRef) {
        val raw = rewritten[index] ?: segments[index].raw
        rewritten[index] = Segments.withoutImage(raw, ref)
    }

    /** The whole note again, as bytes to be written — including the gap [opensOnBlankLine] hid. */
    fun body(): String = (if (opensOnBlankLine) "\n" else "") +
        segments.withIndex().joinToString("") { (i, segment) ->
            when {
                segment is Segment.Prose -> buffers.getValue(i).text.toString()
                else -> rewritten[i] ?: segment.raw
            }
        }

    /** The index of the note's first editable stretch, which is where a placeholder belongs. */
    val firstProseIndex: Int = segments.indexOfFirst { it is Segment.Prose }

    /** Whether the note has no words in it at all — not whether it has no tags or no pictures. */
    val isEmpty: Boolean get() = buffers.values.all { it.text.isBlank() }

}

/**
 * A note, open for writing.
 *
 * **The design is what is absent.** No toolbar, no formatting controls, no word count, no mode
 * switch — tapping a note in the list puts the cursor in it and the page is the writing. The only
 * chrome is a back arrow, a menu holding the one irreversible thing, and an icon that opens the
 * note's details; the title is not chrome at all, but the first line of the note. Formatting appears
 * when text is selected and goes away again, which is the one moment it is wanted.
 *
 * The word count is still absent in the sense that matters — see [NoteInfoDialog]. A figure you open
 * a dialog to read is not the same object as one that sits in the corner counting while you write,
 * and the second is the one this screen was built without.
 *
 * The Markdown is invisible: see [MarkdownTransformation], which hides the markers and brings them
 * back under the cursor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    document: NoteDocument,
    attachments: Attachments,
    links: List<LinkRef>,
    missingLinks: Set<String>,
    knownTags: Set<String>,
    /**
     * The tag that puts a note at the top of the library — `Settings.pinTag`. The bar's pin button
     * is sugar over the tag sheet: it toggles this one tag on the document, and the save that
     * writes the body writes it, on the same terms as any other tag.
     */
    pinTag: String,
    onOpenLink: (LinkRef) -> Unit,
    editable: Boolean,
    /**
     * The note's own dates, straight from its frontmatter, for the details dialog and nothing else.
     *
     * Not on [NoteDocument], which is the editable state and is deliberately rebuilt only when the
     * note changes; these are facts about the file that no keystroke moves. Null for a note whose
     * frontmatter does not carry them, which is no note in the archive but is any plain text file
     * somebody drops into the folder.
     */
    created: Instant?,
    updated: Instant?,
    /**
     * Every name already in the folder, so the rename dialog can refuse a collision before writing.
     *
     * Handed down rather than looked up here: the editor holds one note and knows nothing about the
     * folder, and a dialog that queried the provider would be asking a question the index already
     * answered.
     */
    noteNames: Set<String>,
    /**
     * Put a picked image into the folder's `attachments/` and hand back the relative path, or null
     * if it could not be written. The copying is the vault's business; where the link lands is this
     * screen's, because only the editor knows where the cursor is.
     */
    onCopyImage: suspend (android.net.Uri) -> String?,
    /**
     * Called once a picture's link has been inserted or removed. **The note has to be saved and
     * reopened for the change to show**, and this is what asks for that — see the note on the
     * picker below.
     */
    onImagesChanged: () -> Unit,
    /** Rename the open note's file to this stem, `.md` excluded. */
    onRename: (String) -> Unit,
    /**
     * Show the Markdown as written — markers as characters, rules as dashes, picture lines as their
     * links — instead of what it means. `Settings.showSource`, toggled from the menu here.
     */
    showSource: Boolean,
    onShowSourceChange: (Boolean) -> Unit,
    /** Whether the format bar offers to add a picture. `Settings.imageButton`. */
    imageButton: Boolean,
    /**
     * Put the cursor in the note and raise the keyboard as it opens.
     *
     * True for a note that was just created and false for one being opened to read. A note is opened
     * far more often to look at than to add to, and an app that throws the keyboard up over half the
     * page every time you tap a lyric is an app you stop tapping lyrics in. A note you have this
     * second named and made is the one case where writing is certainly what comes next.
     */
    focusOnOpen: Boolean,
    onBack: () -> Unit,
    /** Hand the note's file to another app. The caller saves first; what leaves is what is on disk. */
    onShare: () -> Unit,
    onDelete: () -> Unit,
    message: String?,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    // Shown as given. It used to be wrapped in "Couldn't save: …" here, which was right for the one
    // caller that hands over a bare reason and wrong for every other: a failed delete came out as
    // "Couldn't save: the note could not be deleted", and a message the *library* had set — a
    // finished tag rename, a finished tag move — sat in the state until a note was opened and then
    // appeared here as "Couldn't save: Moved the tags in 165 notes". The sentence is now built where
    // the failure is known, and this only shows it.
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }
    val scheme = MaterialTheme.colorScheme
    // Which stretch of the note is being written in. The bar acts on this one — a note cut at its
    // images has several fields, and "the first with a selection" was a guess at which one the
    // writer meant.
    var focused by remember(document) { mutableStateOf<Int?>(null) }
    // The title is not one of those stretches: the bar never acts on it, and a selection in it gets
    // a strip of its own — see the popup below.
    var titleFocused by remember(document) { mutableStateOf(false) }
    // The draft view: the note with its scratch lines left out, for reading only — see `Scratch`
    // for the convention and `draftOf` below for what is shown. Per note and not persisted: it is
    // a look, taken and put down, not a mode the app stays in.
    var draft by remember(document) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val editorScope = rememberCoroutineScope()

    /**
     * Put an image link where the cursor is, on a line of its own.
     *
     * **On its own line is not a nicety.** `Segments.split` classifies a whole line as an image line,
     * so a link sharing a line with words would take the words into the picture segment and out of
     * the text the author can edit. The newlines here are added only where the text has not got them
     * already, so inserting at the end of an empty note does not open a gap above it.
     *
     * The fallback to the first stretch of prose is now belt and braces: the only way in is the
     * format bar, and that bar exists only while a field has focus, so `focused` is set by the time
     * anything can be picked. It stays because losing an image the user has already chosen — over a
     * focus race nobody can see — would be a worse failure than putting it in a defensible place.
     */
    fun insertImage(path: String) {
        val index = focused ?: document.firstProseIndex
        if (index < 0) return
        document.bufferAt(index).edit {
            val at = selection.max
            val text = asCharSequence()
            val before = if (at == 0 || text[at - 1] == '\n') "" else "\n"
            val after = if (at >= text.length || text[at] == '\n') "" else "\n"
            replace(at, at, "$before![]($path)\n$after")
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { picked ->
        if (picked != null) {
            editorScope.launch {
                val path = onCopyImage(picked) ?: return@launch
                insertImage(path)
                // **The picture cannot appear until the note is written and read back.** The editor
                // cuts a note into segments when it opens, and the buffers it types into were made
                // then; a link appearing inside one of them is text in a text field, not a new
                // segment. So this saves and asks for the document to be rebuilt, and the image
                // arrives a moment later where the raw link briefly was.
                //
                // Adding a picture is a change to the writing, so the save moving `updated` is
                // correct — unlike a tag, which is filing.
                onImagesChanged()
            }
        }
    }
    val opening = remember { FocusRequester() }
    LaunchedEffect(document) {
        if (focusOnOpen && editable) runCatching { opening.requestFocus() }
    }
    val transformation = remember(scheme) {
        MarkdownTransformation(
            // A revealed marker is dimmer than the words around it, so `**` reads as scaffolding
            // rather than as something the author typed on purpose.
            marker = scheme.onSurfaceVariant.copy(alpha = 0.55f),
            code = scheme.onSurfaceVariant,
        )
    }

    // Android's floating selection popup goes, and the app's own comes in its place — under the
    // selection rather than on top of it, in the app's colours, with the bar's buttons. See
    // SelectionPopup for the mechanism and NoTextToolbar for the two routes Compose has.
    //
    // A text field asks the newer `LocalTextContextMenuToolbarProvider`, which is where the
    // app's provider goes; the older `LocalTextToolbar` and the mouse-driven dropdown route are
    // still switched off.
    val popups = remember { SelectionPopupProvider() }
    CompositionLocalProvider(
        LocalTextToolbar provides NoTextToolbar,
        LocalTextContextMenuToolbarProvider provides popups,
        LocalTextContextMenuDropdownProvider provides NoTextContextMenu,
    ) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            // Empty on purpose. The title is in the note now, in the note's own face, scrolling with
            // it — so repeating it here would name the page twice, and the bar's job on a writing
            // screen is to hold the way out and nothing else. The tags left the top of this screen
            // for the same reason.
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.editor_back),
                    )
                }
            },
            actions = {
                // The pin, out in the bar and showing its state: it began as a menu entry and was
                // too hidden there, since whether a note is pinned is something to see, not only to
                // change. It writes one tag, on the same terms as the tag sheet, so it sits behind
                // the same gate — a note that cannot be written cannot be pinned.
                if (editable) {
                    val pinned = document.tags.any { Tags.isUnder(it, pinTag) }
                    IconButton(onClick = { document.toggleTag(pinTag) }) {
                        Icon(
                            painter = painterResource(
                                if (pinned) R.drawable.ic_keep_filled else R.drawable.ic_keep,
                            ),
                            contentDescription = stringResource(
                                if (pinned) R.string.unpin_note else R.string.pin_note,
                            ),
                        )
                    }
                }
                // The draft view, showing its state like the pin: an open eye while everything is
                // on the page, a closed one while the scratch lines are out. In the bar and not
                // the menu because it is reached for mid-writing, several times over one note.
                // Switching it on with nothing to leave out says how to mark a line, which is the
                // one place the convention is explained in the app, at the moment somebody
                // reaches for it.
                val hint = stringResource(R.string.draft_hint)
                IconButton(onClick = {
                    draft = !draft
                    if (draft) {
                        focusManager.clearFocus()
                        if (!document.hasScratch()) editorScope.launch { snackbar.showSnackbar(hint) }
                    }
                }) {
                    Icon(
                        imageVector = if (draft) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(
                            if (draft) R.string.show_all_lines else R.string.show_draft,
                        ),
                    )
                }
                // Out in the bar rather than in the menu: it writes nothing. The menu below holds
                // the irreversible thing, and mixing a look-only action into it would make opening
                // that menu feel like less than it is.
                var informing by remember(document) { mutableStateOf(false) }
                IconButton(onClick = { informing = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.editor_info),
                    )
                }
                // Opened from the details sheet, which closes behind it. Kept here rather than
                // inside that dialog so the rename outlives it — a dialog cannot host the thing
                // that replaces it.
                var renaming by remember(document) { mutableStateOf(false) }

                if (informing) {
                    NoteInfoDialog(
                        name = document.name,
                        // The note as it stands this second, unsaved keystrokes included — see
                        // NoteInfoDialog on why the count is of the note and not of the file.
                        body = document.body(),
                        created = created,
                        updated = updated,
                        // The same gate the writing is behind. A note whose bytes the parser cannot
                        // reproduce is not written to at all, and renaming it would be the one write
                        // that slipped past that — harmless to the content, but it would move a file
                        // the app has already admitted it does not understand.
                        onRename = if (editable) {
                            { informing = false; renaming = true }
                        } else {
                            null
                        },
                        onDismiss = { informing = false },
                    )
                }
                if (renaming) {
                    RenameNoteDialog(
                        name = document.name,
                        taken = noteNames,
                        onRename = { stem ->
                            renaming = false
                            onRename(stem)
                        },
                        onDismiss = { renaming = false },
                    )
                }

                // Two entries, behind a menu on purpose. Deleting is the only thing this app does
                // that cannot be undone, so it does not get a button of its own next to the back
                // arrow where a thumb already goes; sharing is rare enough to keep it company.
                var open by remember { mutableStateOf(false) }
                IconButton(onClick = { open = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.editor_menu),
                    )
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    // A look-only toggle in the menu with the two actions, and in the menu rather
                    // than the format bar: the bar exists only while a field is focused, and the
                    // reason to look at the source is usually before touching anything.
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.show_source)) },
                        leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                        trailingIcon = {
                            if (showSource) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            open = false
                            onShowSourceChange(!showSource)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.share_note)) },
                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                        onClick = {
                            open = false
                            onShare()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.delete_note)) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                        onClick = {
                            open = false
                            onDelete()
                        },
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface),
            // The editor's bar is the page's own colour, so it needs no ground of its own — but it
            // still takes the top inset, MainActivity having left it alone.
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        )

        if (!editable) {
            ReadOnlyNotice()
        }

        var editingTags by remember(document) { mutableStateOf(false) }

        // One scroller for the whole note, with the text fields inside it rather than each
        // scrolling on its own — a note is one page, and images have to move with the words around
        // them. The box around it is what the selection popup is positioned in: the page, so the
        // popup never sits over the bar or the keyboard.
        var page by remember { mutableStateOf<LayoutCoordinates?>(null) }
        Box(Modifier.weight(1f).onGloballyPositioned { page = it }) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            NoteTitleField(
                document = document,
                editable = editable && !draft,
                // The keyboard's key puts the title down: focus goes, the keyboard goes, the note
                // stays where it is. Moving the cursor into the note instead was tried and is
                // worse in both directions — a buffer's cursor sits at the end of its text, so the
                // page jumped to the foot of the note, and starting it at the top would mean the
                // next thing typed landed in front of the first line of the lyric.
                onDone = { focusManager.clearFocus() },
                onFocusChanged = { titleFocused = it },
            )

            document.segments.forEachIndexed { i, segment ->
                when (segment) {
                    is Segment.Prose -> {
                    // In the draft view the field shows a copy of the text with the scratch lines
                    // gone, and cannot be written in: hiding lines inside the live buffer would
                    // have the cursor walk over text that is not on screen and a Backspace delete
                    // a line nobody can see. The copy is built from the buffer as it stands and
                    // dropped when the view is, so the buffer is what saves either way.
                    val buffer = document.bufferAt(i)
                    val shown = if (!draft) buffer else remember(buffer.text.toString()) {
                        TextFieldState(Scratch.strip(buffer.text.toString()))
                    }
                    RuleLines(shown, enabled = !showSource) { rules ->
                    BasicTextField(
                        state = shown,
                        enabled = editable && !draft,
                        textStyle = LocalProseStyle.current.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        // Nothing hidden in the source view: the file, as characters.
                        outputTransformation = if (showSource) null else transformation,
                        // An empty note is a normal kind of note here — 40 of the archive's 168 are
                        // a title and a tag and nothing else — so the blank page says what it is for
                        // rather than looking like a screen that failed to load. Only the note's
                        // first field offers it; a gap between two images is not an invitation.
                        decorator = { field ->
                            Box {
                                if (document.isEmpty && i == document.firstProseIndex) {
                                    Text(
                                        text = stringResource(R.string.editor_empty),
                                        // The placeholder wears the prose style too, so choosing
                                        // a face does not make the empty page and the first line
                                        // typed onto it two different sizes.
                                        style = LocalProseStyle.current,
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                field()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            // Only the first stretch of text can be focused on opening; the cursor
                            // stays where the buffer puts it, which for a new note is its one empty
                            // line, under the blank line every note in the archive keeps below its
                            // frontmatter.
                            .then(
                                if (i == document.firstProseIndex) Modifier.focusRequester(opening)
                                else Modifier,
                            )
                            .onFocusChanged { state ->
                                if (state.isFocused) focused = i
                                else if (focused == i) focused = null
                            }
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .then(rules.modifier),
                        onTextLayout = rules.onTextLayout,
                    )
                    }
                    }

                    // In the source view a picture line is its link, as the file has it. Not a
                    // field: the segment has no buffer, and a view for looking is not the place
                    // to grow one.
                    is Segment.Images -> if (showSource) {
                        Text(
                            text = (document.imagesAt(i)?.raw ?: "").trimEnd('\n'),
                            style = LocalProseStyle.current,
                            color = scheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    } else document.imagesAt(i)?.let { images ->
                        NoteImages(
                            segment = images,
                            attachments = attachments,
                            onRemove = if (!editable) null else { ref ->
                                document.removeImage(i, ref)
                                // The same save-and-rebuild as inserting: the line is gone from
                                // the bytes, and the document built from them has no gap where
                                // the picture was.
                                onImagesChanged()
                            },
                        )
                    }

                    // Drawn nowhere here: the note's tags are gathered into one chip row at the
                    // foot, so a run in the middle of a note does not interrupt the writing with a
                    // second copy of what the foot already shows.
                }
            }
        }

        // The selection strip: what the bar offers for a selection, brought to the selection. Only
        // over a field that can be written to, and only while something is selected — the field
        // asks for it at the right moments, and the check here is for the moment between a
        // request and the selection collapsing.
        val selectedIn = focused?.let(document::bufferAt)?.takeIf { editable && !it.selection.collapsed }
        if (selectedIn != null) {
            SelectionPopup(provider = popups, anchor = page) {
                SelectionStrip(body = selectedIn)
            }
        }

        // The title's strip: the clipboard and nothing else, since the bar does not serve the title
        // and the formatting buttons would write markup into `title:`. Not gated on a selection —
        // the field asks for a menu at a collapsed cursor too, and that request is the only way
        // paste reaches a title, which the prose gets from the bar.
        if (titleFocused && editable) {
            SelectionPopup(provider = popups, anchor = page) {
                TitleStrip(title = document.titleBuffer)
            }
        }
        }

        if (editingTags) {
            TagSheet(
                selected = document.tags,
                known = knownTags,
                onToggle = document::toggleTag,
                onDismiss = { editingTags = false },
            )
        }

        AttachmentStrip(links = links, missing = missingLinks, onOpen = onOpenLink)

        NoticeHost(snackbar)

        // Formatting exists only while something is selected. A collapsed cursor is someone
        // writing; a selection is someone looking at a piece of text and considering it.
        // **One strip above the keyboard, holding one of two things.**
        //
        // Reading, it holds the note's tags. Writing, it holds the format bar. The same slot and the
        // same height, so nothing is ever stacked on anything and nothing jumps as you start typing
        // — and the signal is the one already there, a text field having focus or not having it.
        //
        // The tags were tried in both other places and both were worse. Pinned *above* the bar they
        // were a permanent band across the page, taking a line of what little the keyboard leaves.
        // Scrolled into the note they stopped being reachable at a glance, which is the whole use of
        // them. Swapping is what was left, and it turns out to be the honest answer: the strip is
        // about the note, and what you want to know about a note differs between reading and
        // writing.
        val writing = editable && focused != null
        val target = focused?.let(document::bufferAt)
        Box(Modifier.heightIn(min = 56.dp)) {
            if (writing && target != null) {
                FormatBar(
                    body = target,
                    onAddImage = if (!imageButton) null else {
                        {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                    },
                    onDone = { focusManager.clearFocus() },
                )
            } else {
                NoteTagBar(
                    // Display order only, the library row's — the frontmatter keeps its own.
                    tags = document.tags.sorted(),
                    enabled = editable,
                    onEdit = { editingTags = true },
                )
            }
        }
    }
    }
}

/** What [RuleLines] hands its field: where to draw, and how to learn where the lines are. */
private class RuleDrawing(
    val modifier: Modifier,
    val onTextLayout: Density.(() -> TextLayoutResult?) -> Unit,
)

/**
 * Draws a horizontal rule across the field wherever the text has one.
 *
 * `- - -` is hidden by the transformation like any other marker, which leaves an empty line where
 * the rule was; this draws a line through that empty line, at the width of the writing, so the
 * reader sees the divider every other Markdown reader would show rather than three dashes or
 * nothing. A rule the cursor is on is not drawn: its dashes are back on screen, dimmed, and the
 * line would sit on top of them.
 *
 * The positions come from [Live], in transformed coordinates — the same answer the transformation
 * paints from, computed once more here because an [OutputTransformation] cannot report back to the
 * composable that owns it without writing state during layout. The vertical bounds come from the
 * field's own [TextLayoutResult], read at draw time through the getter [BasicTextField] hands out,
 * so a wrap or a font change moves the line with the text. Nothing is drawn where there is no rule,
 * which is 147 of the archive's 168 notes.
 */
@Composable
private fun RuleLines(
    state: TextFieldState,
    /** False draws nothing — the source view, where the dashes are on screen. */
    enabled: Boolean,
    content: @Composable (RuleDrawing) -> Unit,
) {
    var layout by remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
    val colour = MaterialTheme.colorScheme.outlineVariant
    val text = state.text.toString()
    val selection = state.selection
    val rules = remember(text, selection, enabled) {
        if (!enabled) emptyList()
        else Live.of(text, selection.min..selection.max).rules.filter { !it.revealed }
    }
    val drawing = remember(rules, colour) {
        RuleDrawing(
            modifier = if (rules.isEmpty()) Modifier else Modifier.drawBehind {
                val result = layout?.invoke() ?: return@drawBehind
                val stroke = 1.dp.toPx()
                for (rule in rules) {
                    if (rule.offset > result.layoutInput.text.length) continue
                    var line = result.getLineForOffset(rule.offset)
                    // An indented line under the rule is a paragraph of its own, and the rule's
                    // empty row is then the last row of the paragraph before it, ending where the
                    // indent begins. The lookup resolves an offset on that seam to the paragraph
                    // that starts there, one row too low; the row that starts at the same offset
                    // and comes first is the rule's.
                    if (line > 0 && result.getLineStart(line - 1) == rule.offset) line--
                    val y = (result.getLineTop(line) + result.getLineBottom(line)) / 2
                    drawLine(colour, Offset(0f, y), Offset(size.width, y), stroke)
                }
            },
            onTextLayout = { getter -> layout = getter },
        )
    }
    content(drawing)
}

/**
 * The strip under a selection: the bar's selection buttons, where the selection is.
 *
 * The same buttons as the bar's, doing the same things to the same buffer, so there is one
 * vocabulary in two places rather than two vocabularies. The bar stays: it is where the cursor
 * actions and the way out live, and it is reachable with nothing selected.
 */
@Composable
private fun SelectionStrip(body: TextFieldState) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 3.dp,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            FormatIcon(R.drawable.ic_format_bold, R.string.format_bold, true) {
                body.wrapSelection("**")
            }
            FormatIcon(R.drawable.ic_format_italic, R.string.format_italic, true) {
                body.wrapSelection("*")
            }
            FormatIcon(R.drawable.ic_format_quote, R.string.format_quote, true) {
                body.wrapSelection("\"")
            }
            BarDivider()
            FormatIcon(R.drawable.ic_content_cut, R.string.format_cut, true) {
                clipboard.setText(AnnotatedString(body.selectedText()))
                body.replaceSelection("")
            }
            FormatIcon(R.drawable.ic_content_copy, R.string.format_copy, true) {
                clipboard.setText(AnnotatedString(body.selectedText()))
            }
            FormatIcon(R.drawable.ic_content_paste, R.string.format_paste, true) {
                clipboard.getText()?.text?.let(body::replaceSelection)
            }
        }
    }
}

/**
 * The strip under the title's cursor or selection: the clipboard, in the same shape as
 * [SelectionStrip] and nothing from the format bar. Cut and copy need a selection; paste needs
 * only the cursor, and a pasted line break becomes a space, the rule [SingleLine] applies to
 * typed input — a programmatic edit goes past the input transformation.
 */
@Composable
private fun TitleStrip(title: TextFieldState) {
    val clipboard = LocalClipboardManager.current
    val selected = !title.selection.collapsed
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 3.dp,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            FormatIcon(R.drawable.ic_content_cut, R.string.format_cut, selected) {
                clipboard.setText(AnnotatedString(title.selectedText()))
                title.replaceSelection("")
            }
            FormatIcon(R.drawable.ic_content_copy, R.string.format_copy, selected) {
                clipboard.setText(AnnotatedString(title.selectedText()))
            }
            FormatIcon(R.drawable.ic_content_paste, R.string.format_paste, true) {
                clipboard.getText()?.text?.let { title.replaceSelection(oneLine(it)) }
            }
        }
    }
}

/**
 * The bar over the keyboard: what to do to the words, and the clipboard.
 *
 * **It appears on focus, not on selection**, and that is forced by suppressing Android's floating
 * popup — see [NoTextToolbar]. The popup was where pasting lived when nothing was selected, so a bar
 * that only showed for a selection would have removed pasting from the app. Focus is the more honest
 * rule anyway: the bar is for the field you are writing in, and it sits above the keyboard rather
 * than over the page, so it costs the writing nothing.
 *
 * **Everything here works at a cursor.** What needs a selection — bold, italic, quotes, cut, copy
 * — lives in the strip that appears under the selection, see [SelectionStrip]; it was here as
 * well until 2026-09-12, greyed out most of the time, three disabled buttons announcing a rule the
 * strip enforces by existing. Three groups with rules between them: undoing, what the bar puts
 * into the note, and the clipboard.
 *
 * **Every button writes something the parser reads back, or plain text.** Bold, italic and a
 * divider are all in `markdown/Blocks.kt` and `Inline.kt`; the quote button writes `"`, which is
 * not markup at all and needs no parser. A button writing anything else would put characters into
 * a lyric that come back as literal text, which is the failure this app exists to avoid. Numbered
 * lists, indentation and the two tag symbols have icons waiting in `res/drawable` and no parser
 * behind them yet.
 *
 * **Deliberately short.** Headings, bullets and a clear-formatting button were here and went on
 * 2026-09-11: a `#` or a `-` at the start of a line is one keystroke and the parser reads it, and
 * every emphasis button already toggles, so clearing was a second way to do what tapping Bold again
 * does. Six heading levels behind a menu was the most machinery in the bar for the least-used
 * mark in the archive.
 *
 * The row still scrolls rather than wrapping, so that a bar that is sometimes two storeys tall
 * never moves the writing up and down as you work.
 */
@OptIn(ExperimentalFoundationApi::class) // undoState
@Composable
private fun FormatBar(
    body: TextFieldState,
    /** Null hides the button — `Settings.imageButton`, off by default. */
    onAddImage: (() -> Unit)?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().imePadding(),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            // Undo lives in the keyboard on Android, when it lives anywhere — one keyboard has it
            // in its top row, another has nothing — and every button in this bar rewrites the whole
            // buffer, so a mis-tap over a selection is a five-line change. The history is the
            // field's own.
            FormatVector(Icons.AutoMirrored.Filled.Undo, R.string.format_undo, body.undoState.canUndo) {
                body.undoState.undo()
            }
            FormatVector(Icons.AutoMirrored.Filled.Redo, R.string.format_redo, body.undoState.canRedo) {
                body.undoState.redo()
            }

            BarDivider()

            // An indent, written as a blockquote — see `Block.Quote` for why that is the right
            // Markdown for it. Line-based, so it needs no selection.
            FormatIcon(R.drawable.ic_format_indent_increase, R.string.format_indent, true) {
                body.quoteLines()
            }
            FormatIcon(R.drawable.ic_horizontal_rule, R.string.format_rule, true) {
                body.insertRule()
            }
            // Beside the rule, because the two do the same kind of thing: put a block on a line of
            // its own at the cursor. It lived in the overflow menu for an afternoon, which was worse
            // in two ways — it read as a rare administrative act rather than as part of writing, and
            // the menu is reachable with nothing focused, so the insertion point had to be guessed.
            // Here there is always a cursor, because the bar only exists when there is one.
            if (onAddImage != null) {
                FormatIcon(R.drawable.ic_add_photo, R.string.add_image, true, onAddImage)
            }

            // A lyric repeats. Select-copy-Enter-paste is four steps for one intention.
            FormatIcon(R.drawable.ic_repeat_line, R.string.format_duplicate, true) {
                body.duplicateLines()
            }

            BarDivider()

            FormatIcon(R.drawable.ic_content_paste, R.string.format_paste, true) {
                clipboard.getText()?.text?.let(body::replaceSelection)
            }
        }

        // **Outside the scrolling row**, so a way out cannot be scrolled off the screen. The one
        // that ends writing has to be where it always is.
        //
        // It puts the keyboard away and brings the tags back, which until now needed the system back
        // gesture — the same gesture that leaves the note, so there was no way to stop typing
        // without risking losing your place.
        BarDivider()
        IconButton(onClick = onDone, modifier = Modifier.padding(end = 4.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.format_done),
            )
        }
      }
    }
}

/** Separates what changes the words from what moves them. */
@Composable
private fun BarDivider() = VerticalDivider(
    color = MaterialTheme.colorScheme.outlineVariant,
    modifier = Modifier.height(24.dp).padding(horizontal = 6.dp),
)

@Composable
private fun FormatIcon(icon: Int, label: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            painter = painterResource(icon),
            // The label a word would have carried, so a screen reader hears the verb rather than a
            // file name.
            contentDescription = stringResource(label),
        )
    }
}

/** [FormatIcon] for an icon from the Material set rather than from `res/drawable`. */
@Composable
private fun FormatVector(icon: ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(imageVector = icon, contentDescription = stringResource(label))
    }
}

/** Applies [FormatActions.duplicate] to the lines the selection covers, moving it to the copy. */
private fun TextFieldState.duplicateLines() {
    val range = selection
    val result = FormatActions.duplicate(text.toString(), range.min, range.max)
    edit {
        replace(0, length, result.text)
        selection = TextRange(result.selectionStart, result.selectionEnd)
    }
}

/** The characters the selection covers. Empty when the cursor is collapsed. */
private fun TextFieldState.selectedText(): String =
    text.substring(selection.min, selection.max)

/** Replace the selection — or insert at the cursor — and leave the caret after what was put in. */
private fun TextFieldState.replaceSelection(with: String) {
    val range = selection
    edit {
        replace(range.min, range.max, with)
        selection = TextRange(range.min + with.length)
    }
}

/** Shown above a note the app will not risk writing. See `IndexedNote.roundTrips`. */
@Composable
private fun ReadOnlyNotice() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.editor_readonly_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.editor_readonly_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** Applies [FormatActions.wrap] to this buffer, keeping the selection on the words. */
private fun TextFieldState.wrapSelection(marker: String) {
    val range = selection
    if (range.collapsed) return
    val result = FormatActions.wrap(text.toString(), range.min, range.max, marker)
    edit {
        replace(0, length, result.text)
        selection = TextRange(result.selectionStart, result.selectionEnd)
    }
}

/** Applies [FormatActions.quote] to the lines the selection covers, keeping them covered. */
private fun TextFieldState.quoteLines() {
    val range = selection
    val result = FormatActions.quote(text.toString(), range.min, range.max)
    edit {
        replace(0, length, result.text)
        selection = TextRange(result.selectionStart, result.selectionEnd)
    }
}

/** Applies [FormatActions.rule], which puts a divider on a line of its own after this one. */
private fun TextFieldState.insertRule() {
    val result = FormatActions.rule(text.toString(), selection.min)
    edit {
        replace(0, length, result.text)
        selection = TextRange(result.selectionStart, result.selectionEnd)
    }
}


/**
 * The note's title, at the top of the page and part of it.
 *
 * **In the document rather than in the bar.** A title is what a note *is* in this archive — 40 of
 * the 168 are a title and a tag and nothing else — so it belongs on the page in the page's own face,
 * scrolling away with the words under it, not pinned above them as chrome. The tags left the top of
 * this screen for the same reason. It is the last piece of the note that could not be edited where
 * it is read.
 *
 * Set in the prose face at 1.5×, so it follows the reader's face, size and spacing settings and
 * reads as the same document as the lyric below it rather than as a label attached to it.
 *
 * **It is frontmatter, never a heading.** Nothing here writes a `#` line into the body; the value
 * goes to `title:`, and `Note.withTags` decides whether that is a change worth a write. No note in
 * the archive carries an ATX heading and this must not be the one that adds the first.
 *
 * **No newline can enter it.** `title:` is one scalar line, and a newline in it would write a block
 * that the next read cannot parse — the note would come back with a title ending mid-word and a
 * stray key after it. Pasting two lines here joins them with a space instead.
 */
@Composable
private fun NoteTitleField(
    document: NoteDocument,
    editable: Boolean,
    onDone: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val prose = LocalProseStyle.current
    val style = prose.copy(
        fontSize = prose.fontSize * 1.5f,
        lineHeight = prose.lineHeight * 1.5f,
        fontWeight = FontWeight.Medium,
        color = scheme.onSurface,
    )
    BasicTextField(
        state = document.titleBuffer,
        enabled = editable,
        textStyle = style,
        cursorBrush = SolidColor(scheme.primary),
        inputTransformation = SingleLine,
        // The key on a wrapping field would be Enter, and Enter in a title means the writer is
        // finished with it — not that they want a second line, which this field cannot have. So the
        // keyboard offers Done and it puts the title down. [SingleLine] stays as the backstop for
        // text arriving some other way, a paste of two lines being the ordinary case.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = { onDone() },
        decorator = { field ->
            Box {
                if (document.titleBuffer.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.new_note_label),
                        style = style,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                field()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/**
 * Keeps a field to one line without making it scroll sideways.
 *
 * `TextFieldLineLimits.SingleLine` would also forbid the newline, and would wrap nothing: a 69
 * character title — the longest in the archive — would run off the side of the phone with no way to
 * see its end. So the field wraps like prose and the newline is taken out of the input instead.
 * A pasted line break becomes a space, which is what somebody pasting two lines into a title meant.
 * The keyboard's own key never arrives here: it is an IME action, and it puts the title down.
 */
private val SingleLine = InputTransformation {
    val text = asCharSequence()
    for (i in text.length - 1 downTo 0) {
        if (text[i] == '\n' || text[i] == '\r') replace(i, i + 1, " ")
    }
}

/** [SingleLine]'s rule for text that does not pass through it: a paste into the title. */
private fun oneLine(text: String): String = text.replace('\n', ' ').replace('\r', ' ')

/**
 * The dialog that starts a note: a title, and nothing else.
 *
 * A title rather than a blank page, because a title is what a note *is* in this archive. 40 of the
 * 168 notes have no body at all — they are ideas filed under a tag — so for a good share of what
 * gets written here, this dialog is the whole note. It is also the only moment the app chooses a
 * filename, and it needs something to choose from.
 *
 * The field takes the focus on opening and the keyboard's action key creates, so capturing a line
 * that just occurred to somebody is: tap, type, done.
 */
@Composable
fun NewNoteDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val ready = typed.isNotBlank()
    val submit = { if (ready) onCreate(typed.trim()) else Unit }

    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogHeading(Icons.Outlined.NoteAdd, stringResource(R.string.new_note_title))
        },
        text = {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(text = stringResource(R.string.new_note_label)) },
                singleLine = true,
                shape = ControlShape,
                // Sentence capitalisation, because a title starts with a capital and nobody should
                // have to reach for shift to write one. Sentences rather than Words: German
                // capitalises its nouns and the keyboard cannot know which they are, so capitalising
                // every word would be wrong more often than right.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = ready) {
                Text(text = stringResource(R.string.new_note_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * The dialog that folds a selection into one new note: a title, and a sentence about what happens.
 *
 * **The title is the one question a merge cannot answer for itself.** Four notes titled
 * `Wer geht vor?` merge under `Wer geht vor?` and nobody needs to be asked — so the field comes
 * prefilled with the shared title when every selected note has the same one. Three snippets with
 * three different titles are another matter: taking the first would claim the other two were drafts
 * of it, and the file is named after the title at creation and never renamed after, so a wrong
 * guess is a wrong filename for good. The field is prefilled with the first note's title in list
 * order, and the user sees what the new file will be called before it exists.
 *
 * The sentence under the field says the rest of what the merge decides: the parts follow the list's
 * order, and it is a creation. What becomes of the originals is the checkbox's to say — the
 * sentence promises nothing either way, since a line reading "the notes are kept" above a ticked
 * box reading "delete them" is two claims and one truth.
 *
 * Deleting the originals is offered, off by default, because folding four copies of
 * `Wer geht vor?` into one and then ticking four rows to delete them is the same decision made
 * twice. It is still a decision: the box is the explicit yes `CLAUDE.md` asks for, and the
 * confirming button changes its wording with it, so nobody agrees to a deletion under a button
 * that says "Merge". The deleting happens after the merge has landed, never before.
 */
@Composable
fun MergeNotesDialog(
    count: Int,
    prefill: String,
    onMerge: (title: String, deleteOriginals: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf(prefill) }
    var deleteOriginals by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val ready = typed.isNotBlank()
    val submit = { if (ready) onMerge(typed.trim(), deleteOriginals) else Unit }

    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogHeading(
                ImageVector.vectorResource(R.drawable.ic_stack_group),
                pluralStringResource(R.plurals.merge_notes_title, count, count),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(text = stringResource(R.string.new_note_label)) },
                    singleLine = true,
                    shape = ControlShape,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Text(
                    text = stringResource(R.string.merge_notes_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // The whole row toggles, not only the box — same as the export's switch.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { deleteOriginals = !deleteOriginals },
                ) {
                    Checkbox(
                        checked = deleteOriginals,
                        onCheckedChange = { deleteOriginals = it },
                    )
                    Text(
                        text = pluralStringResource(R.plurals.merge_notes_delete, count, count),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = ready) {
                Text(
                    text = stringResource(
                        if (deleteOriginals) R.string.merge_notes_confirm_delete
                        else R.string.merge_notes_confirm,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * The one confirmation in the app that guards something irreversible.
 *
 * `CLAUDE.md` forbids deleting anything without an explicit yes, and this is where that yes is
 * asked for. It names the note, because "delete note" on a screen full of somebody's writing is not
 * specific enough to agree to, and it says what actually happens rather than reassuring: the file
 * leaves the folder, and this app cannot bring it back. Whether the sync client kept a copy is not
 * something the app knows, so it does not imply that it did.
 */
@Composable
fun DeleteDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // The one heading in the app that carries colour. `error` is spent here and nowhere else,
        // on the one thing this app does that it cannot undo — a red mark on any other dialog would
        // spend it, and then this one would look like the rest.
        title = {
            DialogHeading(
                icon = Icons.Outlined.DeleteOutline,
                text = stringResource(R.string.delete_note_confirm, title),
                tint = MaterialTheme.colorScheme.error,
            )
        },
        text = { Text(text = stringResource(R.string.delete_note_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringResource(R.string.delete_note)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * The same confirmation, for a selection rather than for one note.
 *
 * Separate from [DeleteDialog] rather than a parameter on it, because the two ask different
 * questions. That one names the note, which is the whole of what makes a single delete agreeable:
 * "delete note" over somebody's writing is not specific enough to say yes to. This one cannot name
 * twelve, so it names the number instead — and the number is the decision, since agreeing to remove
 * two is not the same act as agreeing to remove twelve.
 *
 * Same heading, same error tint, same plain sentence about what happens and what this app cannot do
 * about it afterwards. Nothing here implies the sync client kept a copy; that is not something the
 * app knows.
 */
@Composable
fun DeleteSelectionDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogHeading(
                icon = Icons.Outlined.DeleteOutline,
                text = pluralStringResource(R.plurals.delete_notes_confirm, count, count),
                tint = MaterialTheme.colorScheme.error,
            )
        },
        text = { Text(text = pluralStringResource(R.plurals.delete_notes_body, count, count)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringResource(R.string.delete_note)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * What to do when a note changed on another device while it was open here.
 *
 * Two choices and no third. Overwriting is not offered because `CLAUDE.md` forbids it, and merging
 * is not offered because an app that merges two versions of a lyric silently invents a third. Keep
 * both is the safe default and is listed first; discarding is destructive and says so plainly.
 *
 * Not dismissible by tapping outside: the decision is about the user's own writing, and dismissing
 * it by accident would leave them in an editor whose contents cannot be saved.
 */
@Composable
fun ConflictDialog(onKeepBoth: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        // Two versions of one note, which is what the fork depicts. Not a sync icon: the app does
        // not talk to the sync client and should not draw as though it did.
        title = {
            DialogHeading(Icons.Outlined.CallSplit, stringResource(R.string.conflict_title))
        },
        text = { Text(text = stringResource(R.string.conflict_body)) },
        confirmButton = {
            TextButton(onClick = onKeepBoth) { Text(text = stringResource(R.string.conflict_keep_both)) }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text(text = stringResource(R.string.conflict_discard)) }
        },
    )
}
