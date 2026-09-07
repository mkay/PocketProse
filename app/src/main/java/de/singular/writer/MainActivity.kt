// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import de.singular.writer.ui.LibraryScreen
import de.singular.writer.ui.PocketProseTheme
import de.singular.writer.vault.NoteFile
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

    var files by remember { mutableStateOf(emptyList<NoteFile>()) }
    var folderName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<VaultFailure?>(VaultFailure.NO_FOLDER_CHOSEN) }

    fun refresh() = scope.launch {
        val listing = vault.list()
        files = listing.files
        error = listing.error
        folderName = vault.rootName()
    }

    /**
     * `OpenDocumentTree` rather than a path. The app never proposes a location — no `Documents/`
     * default, no folder of its own — because the folder already exists and belongs to the user.
     */
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null && vault.setRoot(uri)) refresh()
    }

    // Re-read on every return to the foreground. The folder is synced by Nextcloud, so it changes
    // underneath us while the app is not looking; phase 2 makes this cheap by comparing content,
    // but even now it is what keeps the list from being a snapshot of whenever the app launched.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { refresh() }

    LibraryScreen(
        files = files,
        folderName = folderName,
        error = error,
        onChooseFolder = { pickFolder.launch(null) },
    )
}
