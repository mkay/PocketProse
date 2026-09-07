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
import de.singular.writer.ui.DrawerWidth
import de.singular.writer.ui.LibraryScreen
import de.singular.writer.ui.PocketProseTheme
import de.singular.writer.ui.TagDrawer
import de.singular.writer.vault.IndexDump
import de.singular.writer.vault.NoteIndex
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

    val drawerState = rememberDrawerState(DrawerValue.Closed)

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
            if (event == Lifecycle.Event.ON_START) refresh()
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
            // Phase 4. Tapping a note does nothing yet, deliberately: the editor is the one screen
            // that writes, and it is not being wired up before it can do so safely.
            onOpenNote = {},
        )
    }
}
