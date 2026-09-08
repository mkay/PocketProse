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

    private companion object {
        const val PREFS = "settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_PROSE_FONT = "prose_font"
    }
}
