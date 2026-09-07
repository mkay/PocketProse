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
 * The palette is a **taupe** neutral — a warm grey with a faint red-violet lean — at **deliberately
 * reduced contrast**, modelled on Bear's dark theme.
 *
 * The hue was chosen on 2026-09-07 from five candidates rendered side by side at identical
 * luminance, so the choice cost nothing in legibility: only the hue moved. It is hue 350 at very low
 * saturation, which reads as paper rather than as a colour, and is the furthest of the five from the
 * other three apps' palettes.
 *
 * ## The contrast decision
 *
 * This is the one thing in the file that looks like a mistake and is not. The author asked for
 * Bear's softer look on 2026-09-07, knowing it costs contrast, and the numbers below are matched to
 * measurements taken from a Bear screenshot rather than invented:
 *
 * | | Bear (measured) | here |
 * |---|---|---|
 * | dark page | `#2F3235` | `#363031` |
 * | dark title | 5.30:1 | 5.35:1 |
 * | dark body | 4.49:1 | 4.50:1 |
 *
 * Two moves make that look, and only doing one of them fails: the page **lifts off black** (Bear's
 * is 1.63:1 above it) *and* the type **comes down off white**. Lifting the page alone gives a washed
 * grey screen; dimming the type alone gives murk on black. `#363031` carries Bear's page
 * luminance exactly — same softness, our hue.
 *
 * What this gives up is real. The previous palette put body text at 18.11:1 on dark and 16.74:1 on
 * light; this is a quarter of that. Body type on dark now sits just under the 4.5:1 that WCAG asks
 * of small text, and on light just above it. **Do not "correct" these numbers as a tidy-up** — but
 * equally, do not push them further down. If a legibility complaint ever arrives, the lever is the
 * page: darkening it lifts every ratio at once without touching the type, which is the adjustment
 * that keeps the look.
 *
 * Light is reduced in the same character but held a little higher — title 6.53:1 against dark's
 * 5.35:1. A light page in sunlight is unforgiving in a way a dark page indoors is not, and the
 * reference screenshot was a dark one, so there is nothing to match against there.
 */

/**
 * The pages. Dark lifts off black by design (see above); light sits a hair off true white, which
 * was confirmed on the Fairphone on 2026-09-07 as reading like paper rather than like a dirty white.
 */
private val PageDark = Color(0xFF363031)
private val PageLight = Color(0xFFFBFAFA)

/**
 * Type, in two weights.
 *
 * `onSurface` is a note's title and the words in the editor. `onSurfaceVariant` is everything that
 * supports them — the excerpt in a list row, the date, a tag, the empty-note placeholder.
 * The gap between them is deliberately narrow (5.24 against 4.43 on dark) because at this contrast
 * level a wide split would make the secondary tier illegible rather than merely quieter.
 */
private val InkDark = Color(0xFFACA5A6) // 5.35:1 on PageDark
private val InkDarkMuted = Color(0xFFA09698) // 4.50:1
private val InkLight = Color(0xFF64585A) // 6.53:1 on PageLight
private val InkLightMuted = Color(0xFF786A6C) // 4.94:1

/**
 * The accent — and it is **barely a colour**, which is the decision, not a failure of nerve.
 *
 * Chosen on 2026-09-07 from four candidates rendered at identical luminance, so this cost nothing in
 * contrast: only the chroma moved. It is hue 18 at 12% saturation, far enough down that nobody would
 * name it as clay — it reads as a warm grey — but far enough up that a filled button is visibly
 * warmer than plain ink rather than looking like a mistake.
 *
 * The louder candidates were rejected on the same ground each time: the screen is 90% the user's own
 * words, and every degree of chroma the app spends is spent competing with them. The one *quieter*
 * candidate — no accent at all, the page's own taupe one step up — remains the purest reading of
 * "elegantly neutral", and is where to go if this ever starts to feel like a colour.
 *
 * Dark is `#AE9E98` at **5.01:1**, deliberately just *under* the title's 5.35:1. That ordering is
 * the point: at reduced contrast the brightest thing on the screen is where the eye lands first, and
 * here that has to be the writing. Light is `#73625A` at **5.56:1**, under its title's 6.53:1 — the
 * same ordering.
 *
 * The hue stays at 18 rather than the page's 350 so a filled control does not read as merely the
 * page lit up; at this saturation that is a lean, not a colour.
 *
 * Where it appears is a very short list: the format bar over a selection, a chosen tag, a text
 * cursor. If it starts showing up elsewhere, that is the drift worth catching in review.
 */
private val AccentDark = Color(0xFFAE9E98) // 5.01:1 on PageDark
private val AccentLight = Color(0xFF73625A) // 5.56:1 on PageLight

/** Content on the accent: 5.87:1 on the dark one, 5.80:1 on the light one. */
private val OnAccentDark = Color(0xFF2A2523)
private val OnAccentLight = Color(0xFFFFFFFF)

/**
 * A selected surface — the drawer's current tag, and Material's `secondaryContainer` generally.
 *
 * Barely off the page on purpose (1.20:1 on both themes). At this contrast level a selection
 * wash that announces itself would be the loudest thing on a screen whose whole point is to be
 * quiet; the label going to full weight is what actually says "this one".
 */
private val SelectedDark = Color(0xFF423C3A) // 1.19:1 off the page
private val OnSelectedDark = Color(0xFFDCD1CC) // 7.24:1 on SelectedDark
private val SelectedLight = Color(0xFFE8E5E4) // 1.20:1 off the page
private val OnSelectedLight = Color(0xFF584A45) // 6.76:1 on SelectedLight

/**
 * The light theme. Reduced in the same character as dark, held a little higher — see the file note.
 *
 * The container ramp is compressed into a tenth of a stop (1.11 to 1.19 against the page), which is
 * all a tinted surface should do here: a tag chip needs to be findable, not framed.
 */
private val PocketLightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = OnAccentLight,
    primaryContainer = SelectedLight,
    onPrimaryContainer = OnSelectedLight,
    secondaryContainer = SelectedLight,
    onSecondaryContainer = OnSelectedLight,
    background = PageLight,
    onBackground = InkLight,
    surface = PageLight,
    onSurface = InkLight,
    surfaceVariant = Color(0xFFEBE5E6),
    onSurfaceVariant = InkLightMuted,
    surfaceContainerLowest = Color(0xFFFEFEFE),
    surfaceContainerLow = Color(0xFFF7F4F4),
    surfaceContainer = Color(0xFFF2EEEE),
    surfaceContainerHigh = Color(0xFFEBE5E6),
    surfaceContainerHighest = Color(0xFFE5DDDF),
    outline = Color(0xFFAEA0A2), // 2.41:1 — a drawn edge, not type
    outlineVariant = Color(0xFFE5DDDF), // 1.28:1 — the divider between rows
)

/**
 * The dark theme, stated in full rather than left to Material.
 *
 * Material's own dark surfaces are near-black and faintly violet, which is wrong here twice over:
 * this page is deliberately lifted well off black, and the whole palette is warm. Leaving the
 * defaults in place would put the one cool thing in the app underneath everything else.
 */
private val PocketDarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = OnAccentDark,
    primaryContainer = SelectedDark,
    onPrimaryContainer = OnSelectedDark,
    secondaryContainer = SelectedDark,
    onSecondaryContainer = OnSelectedDark,
    background = PageDark,
    onBackground = InkDark,
    surface = PageDark,
    onSurface = InkDark,
    surfaceVariant = Color(0xFF463E3F),
    onSurfaceVariant = InkDarkMuted,
    surfaceContainerLowest = Color(0xFF2B2727),
    surfaceContainerLow = Color(0xFF2F2A2A),
    surfaceContainer = Color(0xFF3E3739),
    surfaceContainerHigh = Color(0xFF463E3F),
    surfaceContainerHighest = Color(0xFF4E4546),
    outline = Color(0xFF6D6364), // 2.23:1
    outlineVariant = Color(0xFF463F40), // 1.26:1
)

/**
 * The paper a picture in a note is mounted on — **the one colour in the app that ignores the theme.**
 *
 * The archive's chord diagrams are 89% transparent PNGs whose ink is `#111111`: they were drawn in
 * Bear against a white page and carry no background of their own. Dropped straight onto this app's
 * dark page they read at **1.46:1**, which is to say they vanish. Measured, after watching them do
 * exactly that on the Fairphone.
 *
 * Inverting them was the other option and is wrong: the app cannot know whether an image is line art
 * or a photograph, and inverting a photograph is a great deal worse than mounting one. So every
 * picture sits on paper, and that ink reads **14.15:1** on it. An opaque image covers the mount and
 * shows only a hairline of it, which is what a photograph in a frame looks like anyway.
 *
 * The value is the **light scheme's own `surfaceContainerHighest`**, not a white invented for the
 * purpose — the deepest step of that ramp, so it is recognisably this app's paper rather than a
 * bright rectangle punched through the page. Being the deepest step is also what makes it work on
 * the light theme, where a whiter mount sat at 1.14:1 against the page and read as nothing at all;
 * this reads 1.28:1 and looks like a mount.
 *
 * Deliberately not a theme token, because it must not follow the theme: a mount that darkened along
 * with the page would be back to the problem it exists to solve. If the light ramp ever moves, move
 * this with it by hand and re-check both numbers.
 */
val ImagePaper = Color(0xFFE5DDDF)

/** Controls use a gentle corner rather than the fully-rounded Material default, as in the others. */
val ControlShape = RoundedCornerShape(5.dp)

/**
 * How wide the tag drawer is.
 *
 * Material's default is 360dp, which on the Fairphone's 393dp-wide screen covers all but a sliver
 * and reads as a page rather than as a panel over one. 300dp leaves the list visibly behind it,
 * which is what tells you the drawer is a temporary thing you are looking past.
 */
val DrawerWidth = 300.dp

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
