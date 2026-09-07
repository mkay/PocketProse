// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.singular.writer.ThemeMode

/**
 * The whole palette is Tailwind's **stone** ramp, which is a warm neutral — a grey with earth in it
 * rather than a brown. That is the brief: "elegantly neutral in shades of earth-coloured grey".
 *
 * The other three apps each hang a saturated accent off a neutral ground, because each of them has a
 * state worth shouting about — a take running, a chord landing, a loop armed. **This one has none.**
 * A writing app's screen is 90% page, the user is looking at their own words, and every colour the
 * app adds is competing with them. So the accent here is deliberately the quietest of the four, and
 * the surfaces do nearly all of the work.
 *
 * All ratios below are measured, not estimated. Recompute them if you move a value.
 */

/**
 * The accent: a **clay**, four rungs apart between the themes.
 *
 * Light is clay-600 `#8A5A3C`, **5.57:1** on the page, and this app takes the number the other
 * three could not. TitleTrack's amber sits at 3.2:1 as a deliberate cost, because a gold that
 * clears 4.5:1 has gone brown and stopped being the colour the mark is. Clay has no such problem —
 * it *is* brown, so the legible step and the right step are the same step. There is no argument to
 * have here and no compromise to defend.
 *
 * Dark is clay-400 `#C4926E` at **7.23:1**, lighter for the same reason every dark accent is: the
 * ground moved away from it.
 *
 * Where this accent actually appears is a very short list — the format bar that pops up over a
 * selection, a chosen tag in the drawer, a text cursor. It is not a colour the app wears. If it
 * starts showing up in more places, that is the drift worth catching in review.
 */
private val ClayOnLight = Color(0xFF8A5A3C) // clay-600
private val ClayOnDark = Color(0xFFC4926E) // clay-400

/**
 * Content sitting *on* the accent, and the two themes answer differently because their accents are
 * on opposite sides of mid.
 *
 * White on clay-600 reads **5.82:1**; stone-950 on clay-400 reads **7.23:1**. Each theme takes
 * whichever its own accent can carry, which is the same rule TitleTrack arrived at.
 */
private val OnClayLight = Color(0xFFFFFFFF)
private val OnClayDark = Color(0xFF0C0A09) // stone-950

/**
 * The accent's ends, for a tinted container: pale clay as the ground on a light screen and as the
 * content on a dark one, deep clay the other way round. The pairing reads **9.89:1** on light and
 * **11.63:1** on dark, and each container sits barely off its own page — 1.20:1 and 1.36:1 — which
 * is all a tinted surface should do here.
 */
private val ClayPale = Color(0xFFF0E4DA)
private val ClayDeep = Color(0xFF3A2418)
private val OnClayPale = Color(0xFF4A2E1D)

/**
 * **The light page is stone-50 `#FAFAF9`, not white, and that is the one value in this file worth
 * arguing about.**
 *
 * TitleTrack went the other way on 2026-07-29 and the reasoning is on its `TrackLightColors`: a page
 * 1.05:1 off true white is below the threshold at which a hue reads as a decision, so all it buys is
 * looking slightly unclean beside a white system surface — a dialog, the keyboard, the shade. This
 * page is 1.04:1 off white and is wide open to exactly that objection.
 *
 * It is taken anyway, for two reasons. The brief asks for earth-coloured grey, and a light theme
 * whose largest area is pure white has not delivered it — the warmth would live only in a ramp the
 * editor screen barely shows, since a distraction-free editor is page and type and nothing else.
 * And stone is a **grey**, where the cream TitleTrack rejected (`#FEF9F1`, a yellow at 1.05:1) was a
 * hue trying to be invisible; a desaturated warm grey next to white reads as paper rather than as a
 * smudge, which is the distinction that argument turns on.
 *
 * That is a claim about how it looks on glass, so **check it on the device before trusting it**. If
 * it reads dirty beside the keyboard on the Fairphone, the fix is `#FFFFFF` here and nothing else —
 * the ramp above already carries the warmth and does not move.
 */
private val PageLight = Color(0xFFFAFAF9) // stone-50
private val PageDark = Color(0xFF0C0A09) // stone-950

/**
 * Body type: stone-900 on light at **16.74:1**, stone-100 on dark at **18.11:1**. Secondary type —
 * the date and tag line under a note, the placeholder on a note with nothing in it — is stone-600 at
 * **7.30:1** and stone-400 at **7.83:1**.
 *
 * Both secondaries are well clear of 4.5:1 on purpose. They carry the library list's small print at
 * a size a phone shows in daylight, and this is the app's most-read screen after the editor.
 */
private val PocketLightColors = lightColorScheme(
    primary = ClayOnLight,
    onPrimary = OnClayLight,
    primaryContainer = ClayPale,
    onPrimaryContainer = OnClayPale,
    secondaryContainer = ClayPale,
    onSecondaryContainer = OnClayPale,
    background = PageLight,
    onBackground = Color(0xFF1C1917),
    surface = PageLight,
    onSurface = Color(0xFF1C1917), // stone-900
    surfaceVariant = Color(0xFFE7E5E4), // stone-200
    onSurfaceVariant = Color(0xFF57534E), // stone-600
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5F4), // stone-100
    surfaceContainer = Color(0xFFF0EFEE),
    surfaceContainerHigh = Color(0xFFE7E5E4), // stone-200
    surfaceContainerHighest = Color(0xFFDEDBD9),
    outline = Color(0xFFA8A29E), // stone-400
    outlineVariant = Color(0xFFD6D3D1), // stone-300
)

/**
 * The dark theme is stated in full rather than left to Material, which is the opposite of what
 * TitleTrack settled on — and for the opposite reason. There the accent had turned warm and the
 * retint was closing a gap that no longer existed. Here the *whole palette* is the warm neutral and
 * the accent is the quiet part, so leaving Material's faintly violet greys in place would put the
 * one cool thing in the app underneath everything else.
 *
 * The ramp climbs stone-950 → `#14100E` → stone-900 → stone-800 → `#33302C`, landing at 1.04, 1.13,
 * 1.30 and 1.51 against the page. The top step is a real step because a dark ramp has room to spend;
 * the light one is compressed into a third of that range because white does not.
 */
private val PocketDarkColors = darkColorScheme(
    primary = ClayOnDark,
    onPrimary = OnClayDark,
    primaryContainer = ClayDeep,
    onPrimaryContainer = ClayPale,
    secondaryContainer = ClayDeep,
    onSecondaryContainer = ClayPale,
    background = PageDark,
    onBackground = Color(0xFFF5F5F4),
    surface = PageDark,
    onSurface = Color(0xFFF5F5F4), // stone-100
    surfaceVariant = Color(0xFF292524), // stone-800
    onSurfaceVariant = Color(0xFFA8A29E), // stone-400
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF14100E),
    surfaceContainer = Color(0xFF1C1917), // stone-900
    surfaceContainerHigh = Color(0xFF292524), // stone-800
    surfaceContainerHighest = Color(0xFF33302C),
    outline = Color(0xFF78716C), // stone-500
    outlineVariant = Color(0xFF44403C), // stone-700
)

/** Controls use a gentle corner rather than the fully-rounded Material default, as in the others. */
val ControlShape = RoundedCornerShape(5.dp)

/** Whether [mode] means dark right now — resolving SYSTEM against the OS setting. */
@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PocketProseTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDark(mode)) PocketDarkColors else PocketLightColors,
        content = content,
    )
}
