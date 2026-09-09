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
import de.singular.writer.markdown.Segments
import de.singular.writer.ui.EditorScreen
import de.singular.writer.ui.NoteDocument
import de.singular.writer.ui.ConflictDialog
import de.singular.writer.ui.DeleteDialog
import de.singular.writer.ui.NewNoteDialog
import de.singular.writer.ui.LibraryScreen
import de.singular.writer.ui.PocketProseTheme
import de.singular.writer.ui.SettingsScreen
import de.singular.writer.ui.SupportDialog
import de.singular.writer.ui.TagDrawer
import de.singular.writer.ui.isDark
import de.singular.writer.vault.Attachments
import de.singular.writer.vault.IndexedNote
import de.singular.writer.vault.IndexDump
import de.singular.writer.vault.NoteIndex
import de.singular.writer.vault.CreateResult
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
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    // Seeded from the start-view setting rather than from null. Nothing validates it here: the tag
    // may have been renamed or may not have synced yet, and `refresh` below already drops a filter
    // whose tag is not in the index — which runs before the first list is drawn, so an impossible
    // start tag shows the whole library rather than an empty one.
    var selectedTag by remember { mutableStateOf(settings.startTag) }
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
    val document = remember(openNote?.file?.name) {
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
            if (selectedTag != null && index.allTags.none { it == selectedTag || it.startsWith("$selectedTag/") }) {
                selectedTag = null
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
            is SaveResult.Failed -> { message = result.reason; false }
        }
    }

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
            is CreateResult.Failed -> message = result.reason
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
            selectedTag = null
            query = ""
            searching = false
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
    val shown = remember(index, selectedTag, query) {
        val byTag = selectedTag?.let(index::withTag) ?: index.notes
        // The search runs **once**, not once per note. It used to sit inside the filter, where
        // `it in index.search(query).toSet()` scanned all 168 bodies and built a fresh set for every
        // note it was checking — 168 full scans a keystroke, which is why typing was unusable.
        if (query.isBlank()) {
            byTag
        } else {
            val matches = index.search(query).toSet()
            byTag.filter { it in matches }
        }
    }

    // Closing the drawer or leaving search is what Back should do before it leaves the app.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && searching) {
        searching = false
        query = ""
    }
    BackHandler(enabled = !drawerState.isOpen && !searching && selectedTag != null) { selectedTag = null }

    // Settings is a full screen over the library rather than a destination beside it: it is not a
    // place you navigate *to* while reading, it is a detour. Checked before the editor so that a
    // note left open underneath is still open on the way back.
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
                        if (result is SaveResult.Failed) message = result.reason else {
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
                        selected = selectedTag,
                        onSelect = {
                            selectedTag = it
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.weight(1f, fill = false),
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
            query = query,
            onQueryChange = { query = it },
            searching = searching,
            onSearchingChange = {
                searching = it
                if (!it) query = ""
            },
            selectedTag = selectedTag,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onChooseFolder = { pickFolder.launch(null) },
            onOpenNote = { openNoteUri = it.file.uri.toString() },
            onNewNote = { naming = true },
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
