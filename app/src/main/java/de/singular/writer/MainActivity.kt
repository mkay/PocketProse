// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import de.singular.writer.ui.DrawerWidth
import de.singular.writer.markdown.Migration
import de.singular.writer.markdown.Segments
import de.singular.writer.markdown.Tags
import de.singular.writer.ui.moveTagsWhat
import de.singular.writer.ui.MoveTagsDialog
import de.singular.writer.ui.RenameTagDialog
import de.singular.writer.ui.EditorScreen
import de.singular.writer.ui.NoteDocument
import de.singular.writer.ui.ConflictDialog
import de.singular.writer.ui.DeleteDialog
import de.singular.writer.ui.DeleteSelectionDialog
import de.singular.writer.ui.NewNoteDialog
import de.singular.writer.ui.LibraryScreen
import de.singular.writer.ui.PocketProseTheme
import de.singular.writer.ui.SearchDialog
import de.singular.writer.ui.SettingsScreen
import de.singular.writer.ui.SupportDialog
import de.singular.writer.ui.TagDrawer
import de.singular.writer.ui.isDark
import de.singular.writer.vault.Attachments
import de.singular.writer.vault.IndexedNote
import de.singular.writer.vault.IndexDump
import de.singular.writer.vault.NoteIndex
import de.singular.writer.vault.CreateResult
import de.singular.writer.vault.DeleteResult
import de.singular.writer.vault.Filters
import de.singular.writer.vault.MigrationResult
import de.singular.writer.vault.RenameResult
import de.singular.writer.vault.RenameNoteResult
import de.singular.writer.vault.SaveResult
import de.singular.writer.vault.Vault
import de.singular.writer.vault.VaultFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The whole app, for now.
 *
 * Extends `AppCompatActivity` rather than `ComponentActivity` despite the UI being entirely Compose,
 * and that is deliberate: `AppCompatDelegate.setApplicationLocales` is what will back the language
 * row in Settings, and below API 33 AppCompat is what stores and re-applies that choice. minSdk is
 * 26, so that lower branch covers 26 to 32. Don't "simplify" this to ComponentActivity — the other
 * three apps all learned the same thing.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Created here rather than inside the composition: it outlives a recreation, and choosing
        // a theme causes one.
        val settings = Settings(this)
        setContent {
            val dark = isDark(settings.themeMode)
            // Keep the system bar icons legible against whichever theme is in effect. The
            // enableEdgeToEdge default only tracks the OS setting, so without this, forcing the
            // light theme on a dark phone leaves white icons on a white page.
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            PocketProseTheme(
                settings.themeMode,
                settings.proseFont,
                settings.proseSize,
                settings.proseLeading,
            ) {
                // The Surface is full-bleed and the *content* takes the insets, not the other
                // way round. Padding the Surface itself stops the page colour below the status
                // bar and lets the bare activity window show through — which on a light theme is
                // a black strip across the top. A writing app is a page edge to edge or it is
                // not a page.
                //
                // The **top** inset is deliberately not taken here. Each screen's header takes it
                // itself, so the header's own ground runs up behind the status bar instead of
                // leaving a band of page colour above it — a seam across the full width of the
                // phone, which is exactly what padding the Surface used to produce lower down.
                val sides = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                )
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.windowInsetsPadding(sides).consumeWindowInsets(sides)) {
                        PocketProseApp(settings)
                    }
                }
            }
        }
    }
}

@Composable
private fun PocketProseApp(settings: Settings) {
    val context = LocalContext.current
    val vault = remember { Vault(context) }
    val scope = rememberCoroutineScope()

    var index by remember { mutableStateOf(NoteIndex(emptyList())) }
    // Seeded from what is already known, so the first frame is right rather than corrected a moment
    // later. `rootWasSet` and `cachedRootName` are preference reads and cost nothing; only the note
    // list needs a provider, and `loading` covers that gap.
    var folderName by remember { mutableStateOf(vault.cachedRootName) }
    var error by remember {
        mutableStateOf(if (vault.rootWasSet) null else VaultFailure.NO_FOLDER_CHOSEN)
    }
    var loading by remember { mutableStateOf(vault.rootWasSet) }
    // The three questions as one value. `searchOpen` is only whether the dialog is up — the filters
    // outlive it, which is the point: you close the dialog to look at what you asked for.
    var filters by remember { mutableStateOf(Filters()) }
    var searchOpen by remember { mutableStateOf(false) }
    // Seeded from the start-view setting rather than from null. Nothing validates it here: the tag
    // may have been renamed or may not have synced yet, and `refresh` below already drops a filter
    // whose tag is not in the index — which runs before the first list is drawn, so an impossible
    // start tag shows the whole library rather than an empty one.
    // The start tag seeds the filter's tag, which is the same tag the drawer sets. One filter, two
    // ways in — see `Filters` and `SearchDialog`.
    LaunchedEffect(Unit) { filters = filters.copy(tag = settings.startTag) }
    // The tag a long-press in the drawer opened the rename dialog on, if any.
    var renaming by remember { mutableStateOf<String?>(null) }
    // True while a rename is writing. It only picks the words for the loading drift — `loading`
    // itself is what puts the drift on screen.
    var renameRunning by remember { mutableStateOf(false) }
    // True while the inline-tag move is writing. Same job as `renameRunning`: it only picks the
    // words for the drift.
    var movingTags by remember { mutableStateOf(false) }
    // Whether the confirm for that move is up.
    var offeringMove by remember { mutableStateOf(false) }

    // What this folder still has written into its notes' text — tags, titles or both — and how much.
    //
    // Recomputed whenever the index is, which is once per folder read — one regex over text already
    // parsed, and cheap enough that there is nothing to cache beyond this. It is what the banner,
    // the confirm and the settings row all count by, so all three agree by construction.
    val inlineTags = remember(index) {
        Migration.survey(index.notes.map { it.note }).takeIf { it.worthOffering }
    }
    var showSettings by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }

    // The note being written in, if any. Held as the uri rather than the IndexedNote so a refresh
    // underneath us re-resolves it rather than pinning a stale copy.
    var openNoteUri by remember { mutableStateOf<String?>(null) }
    val openNote: IndexedNote? = remember(index, openNoteUri) {
        openNoteUri?.let { uri -> index.notes.firstOrNull { it.file.uri.toString() == uri } }
    }
    // One document per note, keyed on the note's **file name**.
    //
    // Not on the uri, which was the original key and was wrong in a way that cost a tag: `Vault.save`
    // swaps a note by deleting the old document and renaming a temp into its place, so the provider
    // hands back a new id for the same file. Keying on that rebuilt the document after every save —
    // and rebuilt it from an `index` that the refresh had not yet replaced, so `openNote` was
    // momentarily null and the document came back **empty**. A second save in that window wrote an
    // empty tag list, and would have written an empty body.
    //
    // The name survives the swap, being what the temp is renamed to. Not on the content either, so
    // a background refresh does not throw away what is being typed.
    // Bumped when the note's *structure* has changed underneath the editor — today only by an image
    // being inserted. The document is otherwise keyed on the file name alone, deliberately: it must
    // survive a save, which swaps the document id, and a background refresh, which must not throw
    // away what is being typed.
    //
    // An image is the one edit that changes how the note is cut up. `Segments.split` makes a segment
    // out of an image line, and the buffers were built when the note opened — so the link lands in a
    // text field as text and stays text until the document is made again. Bumping this is what makes
    // it a picture, and it is only ever bumped *after* a save has returned, so the rebuild reads an
    // index that already holds the new bytes. Rebuilding before that is what once emptied a document
    // and cost a tag; see the note below.
    var documentGeneration by remember { mutableStateOf(0) }
    val document = remember(openNote?.file?.name, documentGeneration) {
        NoteDocument(
            name = openNote?.file?.name.orEmpty(),
            body = openNote?.note?.body.orEmpty(),
            tags = openNote?.note?.tags.orEmpty(),
            title = openNote?.note?.title.orEmpty(),
        )
    }
    val attachments = remember { Attachments(vault, context) }

    // Which of a note's links point at nothing. Resolved once per note rather than per frame: the
    // archive has 24 dead links and finding that out is a provider query each.
    val links = remember(openNoteUri) { openNote?.let { Segments.linksIn(it.note.body) }.orEmpty() }
    var missingLinks by remember(openNoteUri) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(openNoteUri, links) {
        missingLinks = links
            .filterNot { Attachments.isAbsoluteUrl(it.target) }
            .filter { attachments.resolve(it.target) == null }
            .map { it.target }
            .toSet()
    }

    // A conflict holds the editor open with the user's text intact until they choose. Never
    // overwrite, never merge — see SaveResult.Conflict.
    var conflict by remember { mutableStateOf(false) }
    // The two things that need a yes before they happen: making a file and removing one.
    var naming by remember { mutableStateOf(false) }
    // Set only by createNote, so the keyboard comes up for a note that was just made and for no
    // other. Cleared on leaving, or reopening that same note later would raise it again.
    var focusNewNote by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // The folder is read once at a time, however many things ask for it.
    //
    // Two things ask at startup — the initial `LaunchedEffect` and the lifecycle's `ON_START`, which
    // fires as soon as the observer is registered — and before this they both ran, so every launch
    // read all 168 notes twice. Coalescing is better than deleting one of the callers: the pair of
    // them is what makes the first read certain, and a quick background-and-return should not queue
    // a second pass either.
    var reading by remember { mutableStateOf<Job?>(null) }

    fun refresh(): Job {
        reading?.let { if (it.isActive) return it }
        return scope.launch {
            val (loaded, failure) = vault.readAll()
            loading = false
            index = loaded
            error = failure
            folderName = vault.rootName()
            // A synced-in image would otherwise stay missing until the app was restarted.
            attachments.forget()
            // A tag that no longer exists after a sync would otherwise filter the list down to nothing
            // with no way to tell why.
            val tag = filters.tag
            if (tag != null && index.allTags.none { Tags.isUnder(it, tag) }) {
                filters = filters.copy(tag = null)
            }
            // Debug builds only, and app-private — see IndexDump.
            IndexDump.write(context, loaded)
        }.also { reading = it }
    }

    /**
     * Save the open note, if it needs saving.
     *
     * Called on leaving the editor and on the app going to the background — not on a timer and not
     * on every keystroke. `Note.withTags` makes an unchanged note a no-op on disk — unchanged in
     * its tags as well as in its words — so the common case of opening a note and closing it writes
     * nothing at all, which is what keeps the archive's dates intact.
     */
    suspend fun saveOpenNote(): Boolean {
        val note = openNote ?: return true
        // Never write one note's editor into another note's file. This should be impossible, the
        // document being keyed on the very name compared here — but the cost of being wrong is
        // somebody's only copy of a song, so it is checked rather than reasoned about.
        if (document.name != note.file.name) return true
        return when (
            val result = vault.save(note, document.body(), document.tags, newTitle = document.title())
        ) {
            is SaveResult.Unchanged, is SaveResult.Refused -> true
            is SaveResult.Saved -> {
                // The uri changes: the swap in Vault.save deletes the old document and renames the
                // temp into its place, and the provider hands the result a new document id. Without
                // re-pointing here, backgrounding the app mid-note would leave this pointing at a
                // document that no longer exists — the editor would close by itself on return, and
                // anything typed after that would have nowhere to go.
                //
                // **The index is updated in place, not re-read.** Pointing at the new uri while the
                // old index was still in place left it resolving to no note at all, which closed the
                // editor on its own and — before the document was keyed on the file name — silently
                // emptied it. Waiting for a full re-read fixed that and cost three or four seconds
                // on every back tap, which is the wrong price for news the app already has: it
                // wrote the bytes and verified the hash itself.
                index = index.replacing(note, result.uri, result.text, result.hash)
                openNoteUri = result.uri.toString()
                true
            }
            is SaveResult.Conflict -> { conflict = true; false }
            // Wrapped here rather than at the snackbar: this is the one message in the app that
            // arrives as a bare fragment, and the screen showing it cannot know that.
            is SaveResult.Failed -> { message = context.getString(R.string.save_failed, result.reason); false }
        }
    }

    /**
     * Rename the open note's file, and keep the editor pointed at it.
     *
     * **The save comes first, and a failed save cancels the rename.** What is typed is still under
     * the old name at this moment; renaming out from under it would leave the pending write aimed at
     * a file that no longer exists, and the editor holding the only copy of the words. A conflict or
     * a refusal stops here with the note untouched, which is the same answer leaving the editor gets.
     *
     * The note is then re-resolved rather than taken from the composition: `saveOpenNote` re-points
     * `openNoteUri` when it writes, so the `openNote` this lambda closed over may already be the
     * previous document. Reading the state again is the only way to be sure which file is being
     * renamed — and this is the app's one gesture that acts on a file by identity while the identity
     * is in motion.
     *
     * Index first, then the uri, exactly as in `saveOpenNote`: pointing at the new document while
     * the old index is still in place resolves to no note at all, which closes the editor by itself.
     * Updated in place rather than re-read, because a rename is news the app already has — the bytes
     * did not change, only what they are called.
     */
    fun renameNote(stem: String) = scope.launch {
        if (!saveOpenNote()) return@launch
        val uri = openNoteUri ?: return@launch
        val note = index.notes.firstOrNull { it.file.uri.toString() == uri } ?: return@launch
        when (val result = vault.renameNote(note, stem)) {
            is RenameNoteResult.Renamed -> {
                index = index.renamed(note, result.uri, result.name)
                openNoteUri = result.uri.toString()
            }
            // The dialog refuses both of these before the write; reaching them means the folder
            // changed underneath the dialog, which is a sync doing its job rather than a bug.
            RenameNoteResult.Unchanged -> Unit
            RenameNoteResult.Taken -> message = context.getString(R.string.rename_note_taken)
            is RenameNoteResult.Failed ->
                message = context.getString(R.string.rename_note_failed, result.reason)
        }
    }

    /**
     * What the picker calls the image it handed over, or null when it will not say.
     *
     * Only ever a suggestion — `Vault.addAttachment` sanitises it, numbers it against what is already
     * in `attachments/`, and falls back to a name of its own. A camera roll usually offers something
     * like `IMG_20260910_112233.jpg`; a screenshot app sometimes offers nothing at all.
     */
    fun displayNameOf(uri: android.net.Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val noOpener = stringResource(R.string.attachment_no_app)
    val deleteFailed = stringResource(R.string.delete_failed)

    /**
     * Make a note and open it.
     *
     * The index is refreshed before the editor is pointed at the new file, so the note it resolves
     * to is the one that was just written rather than nothing at all — the same ordering the save
     * path needs, and for the same reason.
     */
    fun createNote(title: String) = scope.launch {
        when (val result = vault.create(title)) {
            is CreateResult.Failed -> message = context.getString(R.string.create_failed, result.reason)
            is CreateResult.Made -> {
                refresh().join()
                focusNewNote = true
                openNoteUri = result.uri.toString()
            }
        }
    }


    /**
     * `OpenDocumentTree` rather than a path. The app never proposes a location — no `Documents/`
     * default, no folder of its own — because the folder already exists and belongs to the user.
     */
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null && vault.setRoot(uri)) {
            filters = Filters()
            searchOpen = false
            refresh()
        }
    }

    // Re-read on every return to the foreground. The folder is synced by Syncthing, so it changes
    // underneath us while the app is not looking; readAll compares content rather than timestamps,
    // so a refresh that finds nothing changed re-parses nothing.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> refresh()
                // Leaving the app is the other moment a note must reach disk. Without this, a note
                // written and then swiped away would be lost.
                Lifecycle.Event.ON_STOP -> scope.launch { saveOpenNote() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { refresh() }

    // Tag first, then text: filtering a tag's notes by a word is the useful order, and it also means
    // a search inside a tag does not silently leave the tag.
    val shown = remember(index, filters, settings.sortBy, settings.sortOrder) {
        // One pass over the notes for all three questions — see `NoteIndex.matching`. The text used
        // to be checked with `it in index.search(query).toSet()` *inside* a filter, which scanned
        // all 168 bodies and built a fresh set for every note it checked: 168 full scans a
        // keystroke, and typing was unusable. One predicate, one pass.
        val found = index.matching(filters)
        // Sorted last, over what is actually on screen. The index keeps its own recency order —
        // `replacing` and `renamed` rebuild it and have no business knowing what the reader last
        // picked in a menu — so the chosen order is a view onto the filtered list, not a property
        // of the folder.
        index.sorted(found, settings.sortBy, settings.sortOrder)
    }

    // Multi-select, held here rather than in the library because Back has to be able to leave it and
    // the Back handlers live in this composable. A mode you cannot back out of is a trap.
    var selecting by remember { mutableStateOf(false) }
    // Note uris, never titles. Four files in the archive are titled "Wer geht vor?" and three more
    // share another title, so a selection keyed on the name would delete a different note than the
    // one that was ticked.
    val selected = remember { mutableStateListOf<String>() }
    var deletingSelection by remember { mutableStateOf(false) }

    fun endSelecting() {
        selecting = false
        selected.clear()
    }

    // A note that left the folder — synced away, or deleted by the batch below — must not stay in
    // the selection as a uri nothing resolves to. Pruned against the index rather than cleared, so a
    // refresh landing mid-selection does not throw away ticks the user made.
    LaunchedEffect(index) {
        if (selecting) {
            val alive = index.notes.mapTo(mutableSetOf()) { it.file.uri.toString() }
            selected.retainAll { it in alive }
        }
    }

    // Closing the drawer or leaving search is what Back should do before it leaves the app.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    // Before search and before the tag filter: a selection is the most modal thing on this screen,
    // and Back out of it must not first close a search that is also open underneath.
    BackHandler(enabled = !drawerState.isOpen && selecting) { endSelecting() }
    // One handler for all three, because they are one question now. Back out of a filtered list
    // and you are looking at the folder — not at a list still narrowed by whichever criterion
    // happened to be second in a chain.
    BackHandler(enabled = !drawerState.isOpen && !selecting && !filters.isEmpty) {
        filters = Filters()
    }

    // Settings is a full screen over the library rather than a destination beside it: it is not a
    // place you navigate *to* while reading, it is a detour. Checked before the editor so that a
    // note left open underneath is still open on the way back.
    if (searchOpen) {
        SearchDialog(
            filters = filters,
            onFiltersChange = { filters = it },
            // Counted off the index rather than off `shown`, so the number is the same one whatever
            // the sort is doing. It is recomputed per keystroke over 168 notes, which is the same
            // pass the list itself makes.
            matches = shown.size,
            tree = index.tagTree,
            totalNotes = index.size,
            onDismiss = { searchOpen = false },
        )
    }

    if (deletingSelection) {
        DeleteSelectionDialog(
            count = selected.size,
            onConfirm = {
                deletingSelection = false
                scope.launch {
                    val notes = index.notes.filter { it.file.uri.toString() in selected }
                    when (val result = vault.deleteAll(notes)) {
                        is DeleteResult.Deleted -> {
                            message = context.resources.getQuantityString(
                                R.plurals.delete_notes_done,
                                result.count,
                                result.count,
                            )
                            endSelecting()
                            refresh()
                        }
                        // Half the folder moved and half did not, which is a real outcome rather
                        // than an error — see DeleteResult.Partial. The selection is *kept*, so
                        // trying again acts on what is left instead of making the user re-tick it.
                        is DeleteResult.Partial -> {
                            message = context.getString(
                                R.string.delete_notes_partial,
                                result.deleted,
                                result.remaining.first(),
                            )
                            refresh()
                        }
                        is DeleteResult.Failed -> {
                            message = context.getString(R.string.save_failed, result.reason)
                        }
                    }
                }
            },
            onDismiss = { deletingSelection = false },
        )
    }

    if (naming) {
        NewNoteDialog(
            onCreate = { title ->
                naming = false
                createNote(title)
            },
            onDismiss = { naming = false },
        )
    }

    if (showSettings) {
        SettingsScreen(
            themeMode = settings.themeMode,
            onThemeModeChange = { settings.themeMode = it },
            proseFont = settings.proseFont,
            onProseFontChange = { settings.proseFont = it },
            proseSize = settings.proseSize,
            onProseSizeChange = { settings.proseSize = it },
            proseLeading = settings.proseLeading,
            onProseLeadingChange = { settings.proseLeading = it },
            startTag = settings.startTag,
            onStartTagChange = { settings.startTag = it },
            tagTree = index.tagTree,
            totalNotes = index.size,
            folderName = folderName,
            onChooseFolder = { pickFolder.launch(null) },
            moveTags = inlineTags,
            onMoveTags = {
                // Out of the settings and onto the library, so the confirm is read in front of the
                // notes it is about rather than on top of a preferences page.
                showSettings = false
                offeringMove = true
            },
            onClose = { showSettings = false },
        )
        return
    }

    if (openNote != null) {
        fun leave() = scope.launch {
            if (saveOpenNote()) {
                focusNewNote = false
                openNoteUri = null
            }
        }

        EditorScreen(
            document = document,
            attachments = attachments,
            links = links,
            missingLinks = missingLinks,
            onOpenLink = { link ->
                scope.launch {
                    val intent = if (Attachments.isAbsoluteUrl(link.target)) {
                        attachments.openUrl(link.target)
                    } else {
                        val uri = attachments.resolve(link.target) ?: return@launch
                        attachments.openExternally(uri, attachments.mimeTypeOf(uri))
                    }
                    // No handler for a .band file is an ordinary outcome, not a crash.
                    runCatching { context.startActivity(intent) }
                        .onFailure { message = noOpener }
                }
            },
            // The gate from phase 2: a note whose bytes the parser cannot reproduce is never
            // written, because writing it would corrupt it. It is false for no note in the archive.
            knownTags = index.allTags,
            editable = openNote.roundTrips,
            created = openNote.created,
            updated = openNote.updated,
            noteNames = index.notes.map { it.file.name }.toSet(),
            onRename = { renameNote(it) },
            onCopyImage = { picked ->
                vault.addAttachment(picked, displayNameOf(picked)).also {
                    if (it == null) message = context.getString(R.string.add_image_failed)
                }
            },
            onImageInserted = {
                scope.launch {
                    // Saved first, then rebuilt. The other order hands the editor a document built
                    // from an index that has not seen the link yet, which is the empty-document bug
                    // the comment on `documentGeneration` is about.
                    if (saveOpenNote()) {
                        attachments.forget()
                        documentGeneration++
                    }
                }
            },
            focusOnOpen = focusNewNote,
            onBack = { leave() },
            onDelete = { deleting = true },
            message = message,
            onMessageShown = { message = null },
        )
        BackHandler { leave() }

        if (deleting) {
            DeleteDialog(
                title = openNote.title,
                onConfirm = {
                    deleting = false
                    scope.launch {
                        if (vault.delete(openNote)) {
                            // Out of the note first, then out of the index. Leaving it the other way
                            // round shows an editor whose file is already gone.
                            openNoteUri = null
                            refresh()
                        } else {
                            message = deleteFailed
                        }
                    }
                },
                onDismiss = { deleting = false },
            )
        }

        if (conflict) {
            ConflictDialog(
                onKeepBoth = {
                    scope.launch {
                        val result = vault.saveCopy(
                            openNote,
                            document.body(),
                            document.tags,
                            newTitle = document.title(),
                        )
                        conflict = false
                        if (result is SaveResult.Failed) {
                            message = context.getString(R.string.save_failed, result.reason)
                        } else {
                            refresh()
                            openNoteUri = null
                        }
                    }
                },
                onDiscard = {
                    conflict = false
                    openNoteUri = null
                    scope.launch { refresh() }
                },
            )
        }
        return
    }

    if (offeringMove && inlineTags != null) {
        MoveTagsDialog(
            survey = inlineTags,
            onDismiss = {
                offeringMove = false
                // Dismissing the confirm is not dismissing the offer. Somebody who opened it to
                // read the count and thought better of it has said "not now" — which is what the
                // button says — and the banner staying is what lets them come back to it. The
                // banner's own "No thanks" is where the final no lives.
            },
            onConfirm = {
                offeringMove = false
                scope.launch {
                    // Same borrowing of `loading` as the rename, and for the same reason: this
                    // rewrites more notes than a rename does, every row's excerpt is changing
                    // underneath the list, and a still screen for that long reads as an app that has
                    // died. `LoadingSheets` waits 220ms, so a small folder still finishes without a
                    // flash.
                    drawerState.close()
                    movingTags = true
                    loading = true
                    message = when (val result = vault.migrateInline(index)) {
                        is MigrationResult.Moved -> {
                            // Taken as answered either way. The folder now has nothing to move, so
                            // the banner would go on its own — but a Partial leaves notes behind,
                            // and that case wants the row in settings rather than the banner back
                            // over the list unprompted.
                            settings.moveTagsDeclined = true
                            context.resources.getQuantityString(
                                R.plurals.move_tags_done,
                                result.notes,
                                result.notes,
                                // The survey the offer was made from, so the sentence afterwards
                                // names what the sentence before it promised.
                                context.getString(moveTagsWhat(inlineTags)),
                            )
                        }
                        is MigrationResult.Stale ->
                            context.getString(R.string.move_tags_stale, result.notes.first())
                        is MigrationResult.Refused ->
                            context.getString(R.string.move_tags_refused, result.notes.first())
                        is MigrationResult.Partial -> {
                            settings.moveTagsDeclined = true
                            context.getString(
                                R.string.move_tags_partial,
                                result.migrated,
                                result.remaining.size,
                            )
                        }
                        // Nothing carried one, so nothing to say. Only reachable if a sync cleared
                        // the folder's hashtags between the survey and the tap.
                        MigrationResult.NothingToMove -> null
                    }
                    movingTags = false
                    refresh()
                }
            },
        )
    }

    renaming?.let { tag ->
        RenameTagDialog(
            tag = tag,
            known = index.allTags,
            counting = { index.withTag(it).size },
            onDismiss = { renaming = null },
            onRename = { target ->
                renaming = null
                scope.launch {
                    // Out of the drawer and onto the list before anything is written, so the drift
                    // is what the user is looking at rather than something behind a panel.
                    //
                    // `loading` is the library's own "nothing to draw yet" flag and this borrows it:
                    // renaming `lyrics` rewrites 154 notes and takes about five seconds, which is
                    // long enough that a still list reads as an app that has died. The list is also
                    // genuinely not drawable in that window — every row's tags are being rewritten
                    // underneath it. `LoadingSheets` waits 220ms before it appears, so a rename over
                    // two notes still finishes without a flash.
                    drawerState.close()
                    renameRunning = true
                    loading = true
                    message = when (val result = vault.renameTag(index, tag, target)) {
                        is RenameResult.Renamed -> {
                            // The list lands on the tag that was just renamed, whatever it was
                            // showing before. The drawer is where filtering happens, so a row
                            // touched there is the row the user is thinking about — and after a
                            // rename it is the one thing they want to look at, to see that it
                            // worked. Following only the tag already selected was the first
                            // attempt, and it left the list sitting on some unrelated filter from
                            // earlier in the session, which reads as the rename having gone
                            // somewhere else entirely.
                            filters = filters.copy(tag = target)
                            // The start view is a stored preference and not a place the user is
                            // standing, so it moves only when it was pointing at this tag.
                            settings.startTag?.let { start ->
                                if (Tags.isUnder(start, tag)) {
                                    settings.startTag = Tags.rename(listOf(start), tag, target).single()
                                }
                            }
                            context.resources.getQuantityString(R.plurals.rename_tag_done, result.count, result.count)
                        }
                        is RenameResult.Stale ->
                            context.getString(R.string.rename_tag_stale, result.notes.first())
                        is RenameResult.Refused ->
                            context.getString(R.string.rename_tag_refused, result.notes.first())
                        is RenameResult.Partial ->
                            context.getString(R.string.rename_tag_partial, result.renamed, result.remaining.size)
                        // Nothing carried it, so nothing to say: the drawer will simply not show it.
                        RenameResult.NoSuchTag -> null
                    }
                    // `refresh` puts `loading` back down when the folder has been re-read, so the
                    // list returns already showing the rename rather than blinking through a stale
                    // copy of itself.
                    renameRunning = false
                    refresh()
                }
            },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.width(DrawerWidth),
            ) {
                Column {
                    TagDrawer(
                        tree = index.tagTree,
                        totalNotes = index.size,
                        selected = filters.tag,
                        onSelect = {
                            filters = filters.copy(tag = it)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.weight(1f, fill = false),
                        onRename = { renaming = it },
                        // Only the tag half. A folder whose titles are in its bodies still fills
                        // this drawer perfectly well; it is body *tags* that leave it blank, and
                        // this line is here to explain a blank drawer rather than to advertise.
                        tagsInText = inlineTags?.tagged,
                        onMoveTags = {
                            scope.launch { drawerState.close() }
                            offeringMove = true
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // Settings lives at the bottom of the drawer, where the folder button used to
                    // be. Same reasoning as before — the top bar belongs to the writing — with the
                    // folder now one of the things *inside* here rather than the only thing in the
                    // drawer that was neither a tag nor a note. Support sits under it, in the
                    // ascending order of "about the app" the other three use.
                    DrawerActionRow(
                        icon = Icons.Filled.Settings,
                        label = stringResource(R.string.settings_title),
                        onClick = {
                            scope.launch { drawerState.close() }
                            showSettings = true
                        },
                    )
                    DrawerActionRow(
                        icon = Icons.Filled.Favorite,
                        label = stringResource(R.string.drawer_support),
                        onClick = {
                            scope.launch { drawerState.close() }
                            showSupport = true
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
    ) {
        LibraryScreen(
            notes = shown,
            folderName = folderName,
            error = error,
            loading = loading,
            loadingSays = when {
                renameRunning -> R.string.rename_tag_working
                movingTags -> R.string.move_tags_working
                else -> R.string.library_loading
            },
            loadingCaption = when {
                renameRunning -> R.string.rename_tag_caption
                movingTags -> R.string.move_tags_caption
                else -> null
            },
            filters = filters,
            onOpenSearch = { searchOpen = true },
            onClearFilters = { filters = Filters() },
            sortBy = settings.sortBy,
            sortOrder = settings.sortOrder,
            onSortChange = { by, order ->
                settings.sortBy = by
                settings.sortOrder = order
            },
            density = settings.rowDensity,
            onDensityChange = { settings.rowDensity = it },
            selecting = selecting,
            selected = selected.toSet(),
            onToggleSelect = { note ->
                val uri = note.file.uri.toString()
                if (uri in selected) selected.remove(uri) else selected.add(uri)
            },
            onStartSelecting = { note ->
                selecting = true
                // Entered by long-press, the note pressed is already ticked — the gesture said
                // "this one" and then some. Entered from the menu, nothing is.
                selected.clear()
                note?.let { selected.add(it.file.uri.toString()) }
                // A selection over a search is a selection of what the search found, which reads as
                // fewer notes than the bar's count implies. Leaving search on entry keeps the two
                // modes from stacking.
                searchOpen = false
            },
            onSelectAll = {
                selected.clear()
                // Everything currently on screen, not everything in the folder. A tag filter is a
                // statement about which notes are being worked with, and "all" inside it means all
                // of those.
                shown.forEach { selected.add(it.file.uri.toString()) }
            },
            onEndSelecting = { endSelecting() },
            onDeleteSelected = { deletingSelection = true },
            // Not while a search or a tag filter is on: the banner counts the whole folder, and a
            // sentence about 165 notes over a list of three reads as being about the three.
            moveTags = inlineTags?.takeIf { !settings.moveTagsDeclined && filters.isEmpty },
            onMoveTags = { offeringMove = true },
            onDismissMoveTags = {
                settings.moveTagsDeclined = true
                // Said once, here. Dismissing is permanent — the banner does not come back on the
                // next launch — and the way back is a row in Settings the user has had no reason to
                // go looking for. The × this replaced was self-evidently final in a way a button
                // labelled next to "Move them" is not, so the consequence is spoken instead.
                message = context.getString(R.string.move_tags_dismissed)
            },
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onChooseFolder = { pickFolder.launch(null) },
            onOpenNote = { openNoteUri = it.file.uri.toString() },
            onNewNote = { naming = true },
            message = message,
            onMessageShown = { message = null },
        )
    }

    // Outside the drawer, so it survives the drawer closing under it on the way here.
    if (showSupport) SupportDialog(onDismiss = { showSupport = false })
}

/**
 * A row at the foot of the tag drawer: icon, then label, left-aligned like every tag above it.
 *
 * A `TextButton` was the first shape and centred its content, which put the one row that is not a
 * tag on a different axis from the twenty-four that are — the eye reads that as a footer belonging
 * to some other screen. Geometry deliberately matches `TagDrawer`'s own rows: the same 8dp inset,
 * the same 12dp inner padding, the same `bodyLarge`.
 */
@Composable
private fun DrawerActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
