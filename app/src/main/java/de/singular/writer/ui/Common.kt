// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
