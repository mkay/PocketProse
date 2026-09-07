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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import de.singular.writer.ui.DrawerWidth
import de.singular.writer.ui.EditorScreen
import de.singular.writer.ui.ConflictDialog
import de.singular.writer.ui.LibraryScreen
import de.singular.writer.ui.PocketProseTheme
import de.singular.writer.ui.TagDrawer
import de.singular.writer.vault.IndexedNote
import de.singular.writer.vault.IndexDump
import de.singular.writer.vault.NoteIndex
import de.singular.writer.vault.SaveResult
import de.singular.writer.vault.Vault
import de.singular.writer.vault.VaultFailure
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
        setContent {
            PocketProseTheme {
                // The Surface is full-bleed and the *content* takes the insets, not the other
                // way round. Padding the Surface itself stops the page colour below the status
                // bar and lets the bare activity window show through — which on a light theme is
                // a black strip across the top. A writing app is a page edge to edge or it is
                // not a page.
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(
                        Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .consumeWindowInsets(WindowInsets.safeDrawing),
                    ) {
                        PocketProseApp()
                    }
                }
            }
        }
    }
}

@Composable
private fun PocketProseApp() {
    val context = LocalContext.current
    val vault = remember { Vault(context) }
    val scope = rememberCoroutineScope()

    var index by remember { mutableStateOf(NoteIndex(emptyList())) }
    var folderName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<VaultFailure?>(VaultFailure.NO_FOLDER_CHOSEN) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<String?>(null) }

    // The note being written in, if any. Held as the uri rather than the IndexedNote so a refresh
    // underneath us re-resolves it rather than pinning a stale copy.
    var openNoteUri by remember { mutableStateOf<String?>(null) }
    val openNote: IndexedNote? = remember(index, openNoteUri) {
        openNoteUri?.let { uri -> index.notes.firstOrNull { it.file.uri.toString() == uri } }
    }
    // One buffer per note. Keyed on the uri so switching notes starts a fresh one, and not on the
    // content, so a background refresh does not throw away what is being typed.
    val editorState = remember(openNoteUri) { TextFieldState(openNote?.note?.body.orEmpty()) }

    // A conflict holds the editor open with the user's text intact until they choose. Never
    // overwrite, never merge — see SaveResult.Conflict.
    var conflict by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() = scope.launch {
        val (loaded, failure) = vault.readAll()
        index = loaded
        error = failure
        folderName = vault.rootName()
        // A tag that no longer exists after a sync would otherwise filter the list down to nothing
        // with no way to tell why.
        if (selectedTag != null && index.allTags.none { it == selectedTag || it.startsWith("$selectedTag/") }) {
            selectedTag = null
        }
        // Debug builds only, and app-private — see IndexDump.
        IndexDump.write(context, loaded)
    }

    /**
     * Save the open note, if it needs saving.
     *
     * Called on leaving the editor and on the app going to the background — not on a timer and not
     * on every keystroke. `Note.withBody` makes an unchanged body a no-op on disk, so the common
     * case of opening a note and closing it writes nothing at all, which is what keeps the
     * archive's dates intact.
     */
    suspend fun saveOpenNote(): Boolean {
        val note = openNote ?: return true
        return when (val result = vault.save(note, editorState.text.toString())) {
            is SaveResult.Unchanged, is SaveResult.Refused -> true
            is SaveResult.Saved -> {
                // The uri changes: the swap in Vault.save deletes the old document and renames the
                // temp into its place, and the provider hands the result a new document id. Without
                // re-pointing here, backgrounding the app mid-note would leave this pointing at a
                // document that no longer exists — the editor would close by itself on return, and
                // anything typed after that would have nowhere to go.
                openNoteUri = result.uri.toString()
                refresh()
                true
            }
            is SaveResult.Conflict -> { conflict = true; false }
            is SaveResult.Failed -> { message = result.reason; false }
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)


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

    // Re-read on every return to the foreground. The folder is synced by Nextcloud, so it changes
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
        if (query.isBlank()) byTag else byTag.filter { it in index.search(query).toSet() }
    }

    // Closing the drawer or leaving search is what Back should do before it leaves the app.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && searching) {
        searching = false
        query = ""
    }
    BackHandler(enabled = !drawerState.isOpen && !searching && selectedTag != null) { selectedTag = null }

    if (openNote != null) {
        fun leave() = scope.launch { if (saveOpenNote()) openNoteUri = null }

        EditorScreen(
            title = openNote.title,
            body = editorState,
            // The gate from phase 2: a note whose bytes the parser cannot reproduce is never
            // written, because writing it would corrupt it. It is false for no note in the archive.
            editable = openNote.roundTrips,
            onBack = { leave() },
            message = message,
            onMessageShown = { message = null },
        )
        BackHandler { leave() }

        if (conflict) {
            ConflictDialog(
                onKeepBoth = {
                    scope.launch {
                        val result = vault.saveCopy(openNote, editorState.text.toString())
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
                    // The folder lives at the bottom of the drawer rather than in the top bar: it is
                    // a thing you do once and then never again, and it was cluttering the one screen
                    // that should be about the writing.
                    TextButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            pickFolder.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    ) {
                        Text(text = stringResource(R.string.action_change_folder))
                    }
                }
            }
        },
    ) {
        LibraryScreen(
            notes = shown,
            folderName = folderName,
            error = error,
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
        )
    }
}
