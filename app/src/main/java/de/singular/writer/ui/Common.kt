// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A note's date, in the reader's own language and calendar.
 *
 * Medium style — "2 May 2026", "2. Mai 2026" — rather than numeric. A row has space for it, and a
 * numeric date is the one format where a German and an English reader disagree about what
 * `05/02/2026` means. Localised through the device's configuration rather than through
 * `Locale.getDefault()`, so it follows the in-app language picker that phase 7 adds and not just
 * the system one.
 *
 * Formatted in code, so it never appears in `strings.xml` — the convention noted at the top of that
 * file. Dates and timecodes arrive at the UI as text.
 */
@Composable
fun rememberDateFormatter(): (Instant) -> String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
    return { instant -> formatter.format(instant) }
}

/**
 * A note's date **and** time of day, for the one screen that has room to be exact.
 *
 * The list shows a date alone, which is the right grain for scanning; the note's own details are
 * where "when did I last touch this" gets a real answer, and two edits on the same afternoon are
 * indistinguishable without the clock. Short time rather than medium — seconds are in the file but
 * nobody reads a lyric by the second.
 *
 * Local zone, like [rememberDateFormatter]. The frontmatter is UTC and stays UTC; showing a writer
 * their own evening as the following morning would be technically true and useless.
 */
@Composable
fun rememberDateTimeFormatter(): (Instant) -> String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
    return { instant -> formatter.format(instant) }
}

/**
 * The heading every dialog in the app wears: an icon, then the words, on one line.
 *
 * **One definition because there were nine.** The dialogs grew one at a time as each feature landed
 * and each brought its own bare `Text`, so nothing held them to a shape — and the first one to gain
 * an icon would have made the other eight look unfinished. Handing them a composable rather than a
 * convention is the only version of this that survives the next dialog somebody adds.
 *
 * **Not Material's `icon` slot**, which stacks the icon above the title and centres both. Centred
 * headings are a different app from this one: every screen here is left-aligned and reads as a page
 * rather than as an announcement. The icon goes in the `title` slot with the words instead.
 *
 * The icon is 24dp against a heading of about the same, so it reads as a mark beside the sentence
 * and not as a button. [tint] exists for the one dialog that needs to say something with colour
 * before it says it in words — see `DeleteDialog` — and defaults to the heading's own ink.
 *
 * A dialog with no honest icon passes null and keeps a plain heading. That is not a gap to be filled
 * later: a decorative glyph on a dialog that has nothing to depict is worse than the bare title it
 * replaced, and it would teach the reader that the icons here mean nothing.
 */
@Composable
fun DialogHeading(
    icon: ImageVector?,
    text: String,
    tint: Color = Color.Unspecified,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (tint == Color.Unspecified) LocalContentColor.current else tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(text)
    }
}

/**
 * The app's own snackbar, in the app's own colours.
 *
 * Material builds a snackbar from `inverseSurface` and `inverseOnSurface`, and `Theme.kt` states
 * both schemes in full without ever setting those two — so until 2026-09-09 every message the app
 * showed fell through to Material's defaults and arrived as a near-white bar with a violet cast, the
 * one cool object in an entirely warm app.
 *
 * The fix could have been to define the inverse roles and keep Material's convention, where a
 * transient message is deliberately the opposite of the page so it cannot be mistaken for content.
 * That was considered and not taken. This palette's whole argument is against slabs — it is why the
 * top bar refuses even a tint and why the tag-move banner is recessed rather than lifted — and a
 * bright rectangle would be the loudest thing in the app, spent on the most incidental information
 * it has. A snackbar already announces itself by sliding in and leaving again.
 *
 * So: a container off the same ramp as every other raised surface, ordinary ink, and an `outline`
 * edge to hold it off the list underneath. It stays quiet in both schemes, which the inverted
 * version does not — inverted, light mode gets a near-black bar.
 *
 * One definition, used by both hosts. Two snackbars styled separately would drift, and the whole
 * reason this was worth fixing is that a message should look like it came from this app.
 *
 * **Built from the low-level overload on purpose.** The convenient one — `Snackbar(snackbarData =
 * …)` — applies `modifier.padding(12.dp)` to whatever it is handed *before* passing it to the
 * surface underneath, so a border given to it is drawn 12dp out from the bar it is meant to edge:
 * a wide empty outline around a floating slab, which is the opposite of holding it off the list.
 * That shipped for a day and was caught on a screenshot. Here the margin is applied first and the
 * border second, so the edge is on the bar.
 */
@Composable
fun NoticeHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            // Order is the fix: margin outside, border on the surface itself. The 12dp is Material's
            // own snackbar margin, restored by hand because building it this way skips the step that
            // would have added it.
            modifier = Modifier
                .padding(12.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, ControlShape),
            action = data.visuals.actionLabel?.let { label ->
                {
                    TextButton(onClick = { data.performAction() }) { Text(label) }
                }
            },
            shape = ControlShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionContentColor = MaterialTheme.colorScheme.primary,
        ) {
            Text(data.visuals.message)
        }
    }
}

/**
 * One of several answers to a question, as a chip: the search panel's Any / With / Without, the
 * settings' theme and size rows, and the duplicates switch.
 *
 * Material's `FilterChip` was used first and could not be read in this palette. Its selected state
 * is a container a shade off the panel it sits on and its unselected state is an outline at 2.4:1,
 * so on the search dialog three words sat in a row and nothing said which one was on — the reader
 * had to know that the faintly lighter one was the answer. This wears the tag sheet's language
 * instead, which the drawer's picked row and a ticked list row wear too: **on is a filled
 * `secondaryContainer` with a check, off is an outline**. Two things differ, ground and mark, so
 * the state survives a dim screen and a quick glance.
 *
 * The check is laid out only when on. The chips are usually sized to their words, so a reserved
 * slot would leave every unselected chip with a hole in it; where a row shares its width equally
 * the label is centred anyway and the mark shifts it by half its own width, which is less
 * noticeable than a hole.
 */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = ControlShape,
        color = if (selected) scheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, scheme.outline),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(end = 0.dp),
                )
                Box(Modifier.size(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}
