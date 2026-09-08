// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Which theme to wear. SYSTEM follows the OS setting; the other two override it. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Which face a note is set in. SYSTEM is whatever the phone uses everywhere else; the other two are
 * serifs bundled with the app.
 *
 * Only note text follows this. The list, the drawer and this settings screen stay in the system
 * font on purpose: the choice is about the writing, and chrome in a serif reads as a theme rather
 * than as a reading setting.
 *
 * Order is the order of the chips, and SYSTEM is first because it is the default. New entries go on
 * the end: the name is what is stored, so reordering is free, but the chip row is read left to right
 * and a face that appears in the middle of it looks like it replaced something.
 */
enum class ProseFont { SYSTEM, LITERATA, EB_GARAMOND }

/**
 * How big note text is set, as a multiple of the size the chosen face is corrected to.
 *
 * A multiplier and not a size in sp. Each face already arrives at its own size — Garamond 22% above
 * the base, Literata 4% — and a stored number of points would fight that correction and would have
 * to be re-tuned for every face that is ever added. Multiplying leaves one number for the reader and
 * keeps the faces in the relation they were chosen in.
 *
 * The phone's own font scale multiplies underneath this, so somebody on a large accessibility
 * setting starts higher than NORMAL already puts them. That is why the range stops where it does:
 * 1.45 on top of a system scale of 1.3 is very large type indeed, and beyond it a line of lyric
 * stops fitting the width of a phone.
 */
enum class ProseSize(val scale: Float) {
    SMALL(0.85f),
    NORMAL(1f),
    LARGE(1.15f),
    LARGER(1.3f),
    LARGEST(1.45f),
}

/**
 * How much air there is between the lines, as a multiple of the leading the chosen face is
 * corrected to.
 *
 * A multiplier and not a ratio of the font size, which is what this looked like it wanted to be.
 * The faces do not share a ratio and should not: Roboto's own line box is 1.17 em against Literata's
 * 1.49, so the 1.5 that leaves Roboto comfortable would crush Literata, and the 1.66 the serifs need
 * would leave Roboto looking double-spaced. Those numbers in [proseStyleFor] are matched by eye, not
 * by arithmetic. Multiplying preserves that match at every step; setting a ratio here would throw it
 * away and make one of the three faces wrong at every setting.
 */
enum class ProseLeading(val scale: Float) {
    TIGHT(0.88f),
    NORMAL(1f),
    AIRY(1.14f),
}

/**
 * The app's own preferences — everything that is not the folder.
 *
 * Deliberately its own preference file rather than a corner of the `Vault`'s. The vault's prefs are
 * about one folder and are rewritten when the user points at another; these outlive that choice, and
 * a theme that reset itself when the folder changed would be a puzzle nobody could explain.
 *
 * The values are Compose state, so the screen that shows a setting recomposes the moment it is
 * changed, and the store is created once in `MainActivity`. Small enough to read on the main thread:
 * `SharedPreferences` loads its file on first touch and answers from memory after that, and this
 * file holds a single key.
 */
class Settings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Light, dark, or whatever the phone is doing.
     *
     * An unknown stored value falls back to SYSTEM rather than throwing: a preference file that
     * outlived a rename of these constants is not a reason to refuse to start.
     */
    private var mode by mutableStateOf(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME_MODE, null) }
            ?: ThemeMode.SYSTEM,
    )

    var themeMode: ThemeMode
        get() = mode
        set(value) {
            mode = value
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    /**
     * The face note text is set in. Same fallback rule as the theme: an unreadable stored value is
     * a preference to ignore, not a reason to refuse to start.
     */
    private var font by mutableStateOf(
        ProseFont.entries.firstOrNull { it.name == prefs.getString(KEY_PROSE_FONT, null) }
            ?: ProseFont.SYSTEM,
    )

    var proseFont: ProseFont
        get() = font
        set(value) {
            font = value
            prefs.edit().putString(KEY_PROSE_FONT, value.name).apply()
        }

    /** How big note text is set. Same fallback rule as the face. */
    private var size by mutableStateOf(
        ProseSize.entries.firstOrNull { it.name == prefs.getString(KEY_PROSE_SIZE, null) }
            ?: ProseSize.NORMAL,
    )

    var proseSize: ProseSize
        get() = size
        set(value) {
            size = value
            prefs.edit().putString(KEY_PROSE_SIZE, value.name).apply()
        }

    /** How much air there is between the lines. Same fallback rule again. */
    private var leading by mutableStateOf(
        ProseLeading.entries.firstOrNull { it.name == prefs.getString(KEY_PROSE_LEADING, null) }
            ?: ProseLeading.NORMAL,
    )

    var proseLeading: ProseLeading
        get() = leading
        set(value) {
            leading = value
            prefs.edit().putString(KEY_PROSE_LEADING, value.name).apply()
        }

    private companion object {
        const val PREFS = "settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_PROSE_FONT = "prose_font"
        const val KEY_PROSE_SIZE = "prose_size"
        const val KEY_PROSE_LEADING = "prose_leading"
    }
}
