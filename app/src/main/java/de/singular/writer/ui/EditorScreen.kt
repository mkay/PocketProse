// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import de.singular.writer.markdown.FormatActions
import de.singular.writer.markdown.LinkRef
import de.singular.writer.markdown.Segment
import de.singular.writer.markdown.Segments
import de.singular.writer.markdown.Tags
import de.singular.writer.vault.Attachments

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
 * [name] is the file this document was built from, and it is checked before every write. A document
 * is an editor's worth of unsaved state; writing one into a different note's file would put one
 * song into another's, and the only way to be sure that cannot happen is to carry the identity
 * around with the state rather than to reason about which composition holds what.
 */
class NoteDocument(val name: String, body: String, tags: List<String>) {
    val segments: List<Segment> = Segments.split(body)

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

    /** The whole note again, as bytes to be written. */
    fun body(): String = segments.withIndex().joinToString("") { (i, segment) ->
        if (segment is Segment.Prose) buffers.getValue(i).text.toString() else segment.raw
    }

    /** The index of the note's first editable stretch, which is where a placeholder belongs. */
    val firstProseIndex: Int = segments.indexOfFirst { it is Segment.Prose }

    /** Whether the note has no words in it at all — not whether it has no tags or no pictures. */
    val isEmpty: Boolean get() = buffers.values.all { it.text.isBlank() }

    /** Where the cursor is, for the format bar — the first buffer that has a selection. */
    fun anySelection(): TextFieldState? = buffers.values.firstOrNull { !it.selection.collapsed }

    /**
     * Any editable buffer at all, or null when the note has none.
     *
     * For the format bar, which keeps composing while it animates away after the selection has gone
     * and so needs *something* to hold. It used to reach for `bufferAt(0)`, which assumed the note
     * opens with text — and 66 tag runs in the archive sit at the head of their note, so segment 0
     * is a run of tags with no buffer behind it. Deselecting in one of those crashed the app.
     *
     * Null is a real answer: a body that is nothing but an image line has no text to edit.
     */
    fun anyBuffer(): TextFieldState? = buffers.values.firstOrNull()
}

/**
 * A note, open for writing.
 *
 * **The design is what is absent.** No toolbar, no formatting controls, no word count, no mode
 * switch — tapping a note in the list puts the cursor in it and the page is the writing. The only
 * chrome is a back arrow and the title. Formatting appears when text is selected and goes away
 * again, which is the one moment it is wanted.
 *
 * The Markdown is invisible: see [MarkdownTransformation], which hides the markers and brings them
 * back under the cursor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    title: String,
    document: NoteDocument,
    attachments: Attachments,
    links: List<LinkRef>,
    missingLinks: Set<String>,
    knownTags: Set<String>,
    onOpenLink: (LinkRef) -> Unit,
    editable: Boolean,
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
    onDelete: () -> Unit,
    message: String?,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.save_failed, message.orEmpty())
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(failed)
            onMessageShown()
        }
    }
    val scheme = MaterialTheme.colorScheme
    // Which stretch of the note is being written in. The bar acts on this one — a note cut at its
    // images has several fields, and "the first with a selection" was a guess at which one the
    // writer meant.
    var focused by remember(document) { mutableStateOf<Int?>(null) }
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

    // No floating selection popup anywhere in the editor: it lands on top of the line being
    // selected, and this screen has a bar of its own for the same job.
    //
    // Both of Compose's mechanisms are replaced. A text field asks the newer
    // `LocalTextContextMenuToolbarProvider`, so overriding `LocalTextToolbar` alone did nothing and
    // the popup kept appearing. See NoTextToolbar.
    CompositionLocalProvider(
        LocalTextToolbar provides NoTextToolbar,
        LocalTextContextMenuToolbarProvider provides NoTextContextMenu,
        LocalTextContextMenuDropdownProvider provides NoTextContextMenu,
    ) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    // No style override: the bar's own titleLarge, which is what a screen title is
                    // meant to look like. Shrinking it to titleMedium made the note read as a
                    // subordinate detail rather than as the thing being edited.
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.editor_back),
                    )
                }
            },
            actions = {
                // One entry, and it is behind a menu on purpose. Deleting is the only thing this app
                // does that cannot be undone, so it does not get a button of its own next to the
                // back arrow where a thumb already goes.
                var open by remember { mutableStateOf(false) }
                IconButton(onClick = { open = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.editor_menu),
                    )
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.delete_note)) },
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
        // them.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            document.segments.forEachIndexed { i, segment ->
                when (segment) {
                    is Segment.Prose -> BasicTextField(
                        state = document.bufferAt(i),
                        enabled = editable,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        outputTransformation = transformation,
                        // An empty note is a normal kind of note here — 40 of the archive's 168 are
                        // a title and a tag and nothing else — so the blank page says what it is for
                        // rather than looking like a screen that failed to load. Only the note's
                        // first field offers it; a gap between two images is not an invitation.
                        decorator = { field ->
                            Box {
                                if (document.isEmpty && i == document.firstProseIndex) {
                                    Text(
                                        text = stringResource(R.string.editor_empty),
                                        style = MaterialTheme.typography.bodyLarge,
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
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                    )

                    is Segment.Images -> NoteImages(segment, attachments)

                    // Drawn nowhere here: the note's tags are gathered into one chip row at the
                    // foot, so a run in the middle of a note does not interrupt the writing with a
                    // second copy of what the foot already shows.
                    is Segment.Tags -> Unit
                }
            }

            // The note's tags, **inside the scroller**, after the last word.
            //
            // They were pinned above the format bar, which made them a permanent strip across the
            // bottom of the page: on a short note the writing had a band of chips under it that
            // never moved, and with the keyboard up they took a line of what was left. Tags are part
            // of the note rather than a control over it, so they scroll with it and are reached by
            // reaching the end — which is also where they sit in the file.
            NoteTagBar(
                tags = document.tags,
                enabled = editable,
                onEdit = { editingTags = true },
            )
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

        SnackbarHost(snackbar)

        // Formatting exists only while something is selected. A collapsed cursor is someone
        // writing; a selection is someone looking at a piece of text and considering it.
        // Held across the exit animation: the bar is still composed while it slides away, by which
        // time the field it belonged to may have lost focus.
        val target = focused?.let(document::bufferAt) ?: document.anyBuffer()
        AnimatedVisibility(
            visible = editable && focused != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            if (target != null) FormatBar(target)
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
 * Two groups with a rule between them: what changes the words, then what moves them. Everything
 * needing a selection is disabled without one rather than hidden — buttons that come and go under a
 * thumb are worse than buttons visibly not yet available.
 *
 * **Every button writes something the parser reads back.** Bold, italic, three heading levels, a
 * bullet and a divider are all in `markdown/Blocks.kt` and `Inline.kt`; clearing takes those same
 * marks off. A button writing anything else would put characters into a lyric that come back as
 * literal text, which is the failure this app exists to avoid. Quote, numbered lists, indentation
 * and the two tag symbols have icons waiting in `res/drawable` and no parser behind them yet.
 *
 * The row scrolls rather than wrapping: eleven buttons do not fit any phone, and a bar that is
 * sometimes two storeys tall moves the writing up and down as you work.
 */
@Composable
private fun FormatBar(body: TextFieldState, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val selected = !body.selection.collapsed
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().imePadding(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            FormatIcon(R.drawable.ic_format_bold, R.string.format_bold, selected) {
                body.wrapSelection("**")
            }
            FormatIcon(R.drawable.ic_format_italic, R.string.format_italic, selected) {
                body.wrapSelection("*")
            }
            HeadingControl(body, enabled = true)
            FormatIcon(R.drawable.ic_format_list_bulleted, R.string.format_bullet, true) {
                body.prefixLine("- ")
            }
            FormatIcon(R.drawable.ic_horizontal_rule, R.string.format_rule, true) {
                body.insertRule()
            }
            FormatIcon(R.drawable.ic_remove_selection, R.string.format_clear, selected) {
                body.clearFormatting()
            }

            BarDivider()

            FormatIcon(R.drawable.ic_content_cut, R.string.format_cut, selected) {
                clipboard.setText(AnnotatedString(body.selectedText()))
                body.replaceSelection("")
            }
            FormatIcon(R.drawable.ic_content_copy, R.string.format_copy, selected) {
                clipboard.setText(AnnotatedString(body.selectedText()))
            }
            FormatIcon(R.drawable.ic_content_paste, R.string.format_paste, true) {
                clipboard.getText()?.text?.let(body::replaceSelection)
            }
        }
    }
}

/**
 * The heading control: one button wearing the level of the line under the cursor, and a menu of the
 * five it offers.
 *
 * Five buttons in a row said the same thing in six times the space, and the row already scrolls. One
 * that *shows* the current level says something the five could not: what this line is. A plain line
 * shows H2, which is what tapping through would give it.
 *
 * Picking the level a line already has takes the heading off — the same toggling the rest of the bar
 * does — and the tick beside it in the menu is what says so.
 *
 * H1 is not offered. The parser reads all six, and the archive uses `##` and nothing else; a level
 * above the note's own title is a heading with nothing to be a heading of.
 */
@Composable
private fun HeadingControl(body: TextFieldState, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    val level = FormatActions.headingLevelAt(body.text.toString(), body.selection.min)
    val shown = if (level in LEVELS) level else LEVELS.first

    Box {
        FormatIcon(headingIcon(shown), headingLabel(shown), enabled) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LEVELS.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(headingLabel(candidate))) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(headingIcon(candidate)),
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (candidate == level) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                            )
                        }
                    },
                    onClick = {
                        open = false
                        body.setHeading(candidate)
                    },
                )
            }
        }
    }
}

/** The levels the bar offers. Six exist; the archive uses one. */
private val LEVELS = 2..6

private fun headingIcon(level: Int): Int = when (level) {
    2 -> R.drawable.ic_format_h2
    3 -> R.drawable.ic_format_h3
    4 -> R.drawable.ic_format_h4
    5 -> R.drawable.ic_format_h5
    else -> R.drawable.ic_format_h6
}

private fun headingLabel(level: Int): Int = when (level) {
    2 -> R.string.format_heading_2
    3 -> R.string.format_heading_3
    4 -> R.string.format_heading_4
    5 -> R.string.format_heading_5
    else -> R.string.format_heading_6
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

/** Applies [FormatActions.heading], which swaps the line's mark rather than stacking another. */
private fun TextFieldState.setHeading(level: Int) {
    val result = FormatActions.heading(text.toString(), selection.min, level)
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

/** Applies [FormatActions.clear] to the selection, keeping the words it leaves behind selected. */
private fun TextFieldState.clearFormatting() {
    val range = selection
    if (range.collapsed) return
    val result = FormatActions.clear(text.toString(), range.min, range.max)
    edit {
        replace(0, length, result.text)
        selection = TextRange(result.selectionStart, result.selectionEnd)
    }
}

/** Applies [FormatActions.prefixLine] to the line the selection starts on. */
private fun TextFieldState.prefixLine(prefix: String) {
    val result = FormatActions.prefixLine(text.toString(), selection.min, prefix)
    edit {
        replace(0, length, result.text)
        selection = TextRange(result.selectionStart, result.selectionEnd)
    }
}

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
        title = { Text(text = stringResource(R.string.new_note_title)) },
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
        title = { Text(text = stringResource(R.string.delete_note_confirm, title)) },
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
        title = { Text(text = stringResource(R.string.conflict_title)) },
        text = { Text(text = stringResource(R.string.conflict_body)) },
        confirmButton = {
            TextButton(onClick = onKeepBoth) { Text(text = stringResource(R.string.conflict_keep_both)) }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text(text = stringResource(R.string.conflict_discard)) }
        },
    )
}
