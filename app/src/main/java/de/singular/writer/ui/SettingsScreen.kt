// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.singular.writer.ProseFont
import de.singular.writer.ProseLeading
import de.singular.writer.ProseSize
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
    proseSize: ProseSize,
    onProseSizeChange: (ProseSize) -> Unit,
    proseLeading: ProseLeading,
    onProseLeadingChange: (ProseLeading) -> Unit,
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
                EditorSettings(
                    proseFont = proseFont,
                    onProseFontChange = onProseFontChange,
                    proseSize = proseSize,
                    onProseSizeChange = onProseSizeChange,
                    proseLeading = proseLeading,
                    onProseLeadingChange = onProseLeadingChange,
                )
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

/** What happens while you are writing: the face a note is set in, and how it is set. */
@Composable
private fun EditorSettings(
    proseFont: ProseFont,
    onProseFontChange: (ProseFont) -> Unit,
    proseSize: ProseSize,
    onProseSizeChange: (ProseSize) -> Unit,
    proseLeading: ProseLeading,
    onProseLeadingChange: (ProseLeading) -> Unit,
) {
    SettingsSectionLabel(R.string.settings_section_writing)
    // The face keeps a sample of its own on every row, and that is not a duplicate of the paragraph
    // below. Choosing a face is a comparison — three of them, side by side, at a glance — and one
    // shared preview would turn it into tap, look, remember, tap. Size and leading are not
    // comparisons: nobody can judge one step against another in the abstract, only whether what is
    // in front of them reads comfortably, and leading is invisible on the single line a face row
    // can hold. So they get the paragraph, and the faces keep their line.
    ProseFont.entries.forEach { font ->
        ProseFontOption(
            font = font,
            selected = font == proseFont,
            onSelect = { onProseFontChange(font) },
        )
    }
    SettingsCaption(R.string.settings_font_caption)

    SettingsSectionLabel(R.string.settings_section_size)
    ProsePreview(font = proseFont, size = proseSize, leading = proseLeading)
    SettingsChoiceLabel(R.string.settings_text_size)
    ProseSizeChips(
        size = proseSize,
        onSelect = onProseSizeChange,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    SettingsChoiceLabel(R.string.settings_line_spacing)
    ProseLeadingChips(
        leading = proseLeading,
        onSelect = onProseLeadingChange,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    SettingsCaption(R.string.settings_size_caption)
}

/**
 * The paragraph that shows what the two settings below it do.
 *
 * A real [Text] at the real [proseStyleFor], so the lines break where they will break in a note.
 * Drawing it by hand would make this a picture of what the settings screen believes the editor does.
 * 12dp of margin and 8dp of padding, so the text sits at the same 20dp from the edge as prose does
 * in the editor and the wrap points are the ones a note would have.
 *
 * **On a panel of its own.** Unpainted it read as the settings page's own body copy — a paragraph
 * of German under a heading, which is exactly what a caption looks like — and a sample nobody
 * recognises as a sample is not a preview of anything. The tint says "this is the thing being
 * changed" before a word of it is read.
 *
 * **A fixed height, and the text clipped to it.** The paragraph grows and shrinks; the box must not.
 * A preview that changed the page's height would move the chips out from under the thumb that is
 * stepping through the sizes — the control would run away from the reader at every tap. Tall enough
 * for three lines at the largest setting and rather more at the smallest, which is what it takes to
 * see leading at all.
 */
@Composable
private fun ProsePreview(font: ProseFont, size: ProseSize, leading: ProseLeading) {
    val panel = MaterialTheme.colorScheme.surfaceVariant
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(ControlShape)
            .background(panel)
            .height(148.dp)
            .clipToBounds(),
    ) {
        Text(
            text = stringResource(R.string.settings_prose_preview),
            style = proseStyleFor(font, size, leading),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        // The paragraph outruns the box at every setting, and a hard edge across the middle of a
        // sentence reads as something gone wrong rather than as a sample carrying on. The fade says
        // "more of this", and it holds for whatever paragraph ends up in the string — which is the
        // point, since the real one is not written yet.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(28.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, panel))),
        )
    }
}

/**
 * A single-select row of Small … Largest chips.
 *
 * A [FlowRow] rather than a [Row]: five words do not fit the width of a phone, and a plain row
 * answered by squeezing "Largest" into a column of four letters. Wrapping to a second line is what
 * a row of chips is supposed to do when it runs out of width.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProseSizeChips(
    size: ProseSize,
    onSelect: (ProseSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val label = mapOf(
            ProseSize.SMALL to R.string.size_small,
            ProseSize.NORMAL to R.string.size_normal,
            ProseSize.LARGE to R.string.size_large,
            ProseSize.LARGER to R.string.size_larger,
            ProseSize.LARGEST to R.string.size_largest,
        )
        ProseSize.entries.forEach { step ->
            FilterChip(
                selected = size == step,
                onClick = { onSelect(step) },
                label = { Text(stringResource(label.getValue(step))) },
                shape = ControlShape,
            )
        }
    }
}

/** A single-select row of Tight / Normal / Airy chips. */
@Composable
private fun ProseLeadingChips(
    leading: ProseLeading,
    onSelect: (ProseLeading) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val label = mapOf(
            ProseLeading.TIGHT to R.string.leading_tight,
            ProseLeading.NORMAL to R.string.leading_normal,
            ProseLeading.AIRY to R.string.leading_airy,
        )
        ProseLeading.entries.forEach { step ->
            FilterChip(
                selected = leading == step,
                onClick = { onSelect(step) },
                label = { Text(stringResource(label.getValue(step))) },
                shape = ControlShape,
            )
        }
    }
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

/** The name of one choice, where a section holds more than one row of chips. */
@Composable
private fun SettingsChoiceLabel(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 10.dp),
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
 * One face, offered the way a font is actually chosen: by looking at it.
 *
 * This was a chip row first, and a chip row cannot do the job. "System" and "Literata" side by side
 * say nothing about what either one is — not that one is a serif, not what its serif looks like —
 * and a font name is only meaningful to someone who already knows the font. So each option names
 * the face, says what kind it is, and then sets a line of text in it.
 *
 * The sample is drawn at the real prose style, [proseStyleFor], including the size and leading each
 * face is corrected by. A preview at some tidy settings-screen size would be a preview of a
 * different thing than the one the button turns on.
 */
@Composable
private fun ProseFontOption(font: ProseFont, selected: Boolean, onSelect: () -> Unit) {
    val kind = when (font) {
        ProseFont.SYSTEM -> R.string.font_kind_sans
        ProseFont.LITERATA, ProseFont.EB_GARAMOND -> R.string.font_kind_serif
    }
    val name = when (font) {
        ProseFont.SYSTEM -> R.string.font_system
        ProseFont.LITERATA -> R.string.font_literata
        ProseFont.EB_GARAMOND -> R.string.font_eb_garamond
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(ControlShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(name),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(kind),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.font_preview),
                style = proseStyleFor(font),
                color = MaterialTheme.colorScheme.onSurface,
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
