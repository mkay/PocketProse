// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.runtime.Composable
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
