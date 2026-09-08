// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.singular.writer.ProseFont
import de.singular.writer.R
import de.singular.writer.ThemeMode

/**
 * The two halves of the settings, and the page that is not a setting at all.
 *
 * The split is by *when you come here*, matching the other three apps. **Editor** is what happens
 * while you are writing — the things you would change with a song half-finished on the screen.
 * **System** is the app's own set-up: how it looks, and which folder it is looking at. One will be
 * visited when something about writing reads wrong; the other is visited twice.
 *
 * Editor is empty today and is here anyway. The first editor setting is coming, and a tab row that
 * grows a tab later moves everything the user had learned the position of; a tab that is present
 * from the start and fills up costs nobody anything. It says so in plain words rather than showing
 * a blank page — see [EditorSettings].
 *
 * **About** is the odd one, and is a tab because the tab row is the only always-visible strip on
 * this screen. As a row at the foot of System it would be a destination buried inside one of two
 * halves, with nothing about the word "System" saying that the app's version and its bug tracker
 * are in there. A tab is one tap from either half and says its own name whichever half is showing.
 */
private enum class SettingsTab(@StringRes val title: Int) {
    EDITOR(R.string.settings_tab_editor),
    SYSTEM(R.string.settings_tab_system),
    ABOUT(R.string.settings_tab_about),
}

/**
 * How the app behaves, as opposed to what is written in it. A full screen shown over the library,
 * reached from the foot of the tag drawer; [onClose] backs out to it.
 *
 * The header is built the way the library's is — the page's own colour, taking the status bar inset
 * itself — rather than with a `Scaffold`, so the top of the screen is one continuous page. See the
 * note in `MainActivity` about why the Surface does not take that inset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    proseFont: ProseFont,
    onProseFontChange: (ProseFont) -> Unit,
    folderName: String?,
    onChooseFolder: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    // Local state: back from here leaves the settings altogether, so nothing a level up needs to
    // know which tab was showing.
    var tab by rememberSaveable { mutableStateOf(SettingsTab.EDITOR) }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
            PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = Color.Transparent) {
                SettingsTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        text = { Text(stringResource(entry.title)) },
                    )
                }
            }
        }
        // Each tab scrolls on its own rather than sharing one scroller: About brings its own layout
        // and its own scroll — it is a page, not a column of rows — and a shared scroll state would
        // carry its offset across a tab switch, landing you halfway down the one you arrived at.
        when (tab) {
            SettingsTab.EDITOR -> SettingsPage {
                EditorSettings(proseFont = proseFont, onProseFontChange = onProseFontChange)
            }
            SettingsTab.SYSTEM -> SettingsPage {
                SystemSettings(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    folderName = folderName,
                    onChooseFolder = onChooseFolder,
                )
            }

            SettingsTab.ABOUT -> AboutScreen()
        }
    }
}

/** The ground a settings tab stands on: the whole tab, scrolling, clear of the bottom. */
@Composable
private fun SettingsPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        content = content,
    )
}

/** What happens while you are writing. So far: the face a note is set in. */
@Composable
private fun EditorSettings(proseFont: ProseFont, onProseFontChange: (ProseFont) -> Unit) {
    SettingsSectionLabel(R.string.settings_section_writing)
    ProseFontChips(
        font = proseFont,
        onSelect = onProseFontChange,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    SettingsCaption(R.string.settings_font_caption)
}

@Composable
private fun SystemSettings(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    folderName: String?,
    onChooseFolder: () -> Unit,
) {
    SettingsSectionLabel(R.string.settings_section_appearance)
    ThemeModeChips(
        mode = themeMode,
        onSelect = onThemeModeChange,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    SettingsSectionLabel(R.string.settings_section_folder)
    // Moved here from the foot of the tag drawer, where it sat beneath the tags as the only thing
    // in the app that was neither a tag nor a note. It is the definition of a set-once option, and
    // the drawer is a place you open while looking for something to read.
    SettingActionRow(
        label = R.string.action_change_folder,
        // The folder's own name, so the row says which folder is about to be swapped out rather
        // than only offering to swap it. Before one is chosen there is no name to give.
        subtitle = folderName ?: stringResource(R.string.settings_folder_none),
        icon = Icons.Default.FolderOpen,
        onClick = onChooseFolder,
    )
    SettingsCaption(R.string.settings_folder_caption)
}

@Composable
private fun SettingsSectionLabel(@StringRes text: Int) {
    Text(
        stringResource(text).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** The line under a setting that says what it means, where the label alone is not enough. */
@Composable
private fun SettingsCaption(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, end = 16.dp, bottom = 4.dp),
    )
}

/** A single-select row of Follow system / Light / Dark chips. */
@Composable
private fun ThemeModeChips(
    mode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val label = mapOf(
            ThemeMode.SYSTEM to R.string.theme_system,
            ThemeMode.LIGHT to R.string.theme_light,
            ThemeMode.DARK to R.string.theme_dark,
        )
        ThemeMode.entries.forEach { m ->
            FilterChip(
                selected = mode == m,
                onClick = { onSelect(m) },
                label = { Text(stringResource(label.getValue(m))) },
                shape = ControlShape,
            )
        }
    }
}

/**
 * The face a note is set in. Chips rather than a list, the same shape the theme uses one section
 * away: two choices that are instantly reversible, where a menu would hide one behind a tap.
 *
 * The chips are labelled by name and not set in the face they choose. A one-word sample is not
 * enough of either font to judge, and a chip row where each chip is a different size is a mess —
 * the note behind the settings is the preview, and it is one tap away.
 */
@Composable
private fun ProseFontChips(
    font: ProseFont,
    onSelect: (ProseFont) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val label = mapOf(
            ProseFont.SYSTEM to R.string.font_system,
            ProseFont.LITERATA to R.string.font_literata,
        )
        ProseFont.entries.forEach { f ->
            FilterChip(
                selected = font == f,
                onClick = { onSelect(f) },
                label = { Text(stringResource(label.getValue(f))) },
                shape = ControlShape,
            )
        }
    }
}

/**
 * A settings row that does something rather than holding a value: icon, label, and a line saying
 * what the press is about.
 *
 * [subtitle] is a string rather than a resource because the one row here fills it with the folder's
 * own name, which is not in our string table at all.
 */
@Composable
private fun SettingActionRow(
    @StringRes label: Int,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
