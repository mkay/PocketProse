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
 * What the library orders its notes by.
 *
 * UPDATED is the list as it has always been and stays the default — what a writing app owes the top
 * of its list is the thing you were last working on. The other two answer questions recency cannot:
 * where is the one I can name, and which is the long one.
 *
 * WORDS counts words and not bytes. A file's size counts its frontmatter and its image lines, so a
 * chord sheet with eight pictures would outrank a longer song, and the number would match nothing
 * the reader has ever been shown. Words are what the details sheet already tells them.
 */
enum class SortBy { UPDATED, TITLE, WORDS }

/** Which end of the order comes first. DESC is newest, Z, longest. */
enum class SortOrder { DESC, ASC }

/**
 * How much of a note a row shows.
 *
 * FULL is title, excerpt, date and tags — the row the app is judged on, because the excerpt is the
 * note in the writer's own words rather than metadata about it. COMPACT drops the excerpt and the
 * date and keeps the tags, for the reader who is looking rather than reading: a title alone cannot
 * tell four notes called "Wer geht vor?" apart and their tags can.
 */
enum class RowDensity { FULL, COMPACT }

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

    /**
     * The tag the library opens filtered by, or null for all notes.
     *
     * The odd one out here: the other four are enums, where an unreadable stored value is simply
     * wrong and falls back to a default. A tag is free text whose validity lives in the vault, not
     * in this file, and it can stop being valid without anybody touching this setting — a tag
     * renamed on the desktop, or a folder that has not finished syncing. So nothing is validated
     * here. `MainActivity` already drops a filter whose tag is not in the index, and that check
     * covers this one for free.
     *
     * The stored string deliberately survives that. A tag missing at this moment is not the same as
     * a tag the user is done with, and silently rewriting the preference because a sync was slow
     * would lose a choice they made on purpose.
     *
     * Stores the full path — `album/debut`, not `debut` — because that is what filtering matches
     * on: a path selects its own notes and everything nested under it.
     */
    /** What the library sorts by. Same fallback rule as the theme. */
    private var sort by mutableStateOf(
        SortBy.entries.firstOrNull { it.name == prefs.getString(KEY_SORT_BY, null) } ?: SortBy.UPDATED,
    )

    var sortBy: SortBy
        get() = sort
        set(value) {
            sort = value
            prefs.edit().putString(KEY_SORT_BY, value.name).apply()
        }

    /** Which end of that order comes first. */
    private var order by mutableStateOf(
        SortOrder.entries.firstOrNull { it.name == prefs.getString(KEY_SORT_ORDER, null) }
            ?: SortOrder.DESC,
    )

    var sortOrder: SortOrder
        get() = order
        set(value) {
            order = value
            prefs.edit().putString(KEY_SORT_ORDER, value.name).apply()
        }

    /** How much of a note a row shows. */
    private var density by mutableStateOf(
        RowDensity.entries.firstOrNull { it.name == prefs.getString(KEY_ROW_DENSITY, null) }
            ?: RowDensity.FULL,
    )

    var rowDensity: RowDensity
        get() = density
        set(value) {
            density = value
            prefs.edit().putString(KEY_ROW_DENSITY, value.name).apply()
        }

    private var start by mutableStateOf(prefs.getString(KEY_START_TAG, null))

    var startTag: String?
        get() = start
        set(value) {
            start = value
            prefs.edit().putString(KEY_START_TAG, value).apply()
        }

    /**
     * The tag whose notes sit at the top of the library, whatever the sort.
     *
     * A tag and not a flag of its own, because a flag would have to live somewhere: in this store,
     * where it dies with the app and never reaches the other devices the folder is synced to, or in
     * the frontmatter as a key nobody else reads. A tag is written into the file the way every other
     * tag is — it syncs, it survives an uninstall as a perfectly ordinary tag, another editor sees it
     * for what it is, and taking it off is unpinning. Adding one is a frontmatter-only edit, so
     * `updated` stays put.
     *
     * A default rather than a constant. The name is one literal across every device and language —
     * localising it would split a folder into `pinned` and `angepinnt` the first time two phones with
     * different locales each pinned something — but which literal is the user's to choose, and the
     * tag drawer's rename follows it, see `MainActivity`. It costs the folder nothing until the first
     * pin: an unused default imposes no convention on anyone's files.
     */
    private var pin by mutableStateOf(prefs.getString(KEY_PIN_TAG, null) ?: DEFAULT_PIN_TAG)

    var pinTag: String
        get() = pin
        set(value) {
            pin = value
            prefs.edit().putString(KEY_PIN_TAG, value).apply()
        }

    /**
     * Whether the user has waved away the offer to move their inline tags into their notes.
     *
     * Permanent, and it has to be: the offer is about the user's own filing, which is theirs. A
     * banner that came back on every launch would be the app nagging about a decision it was already
     * given. The way back is the row in Settings, which appears only while there is still something
     * to move.
     *
     * Stored here rather than with the vault's folder preference even though it is a fact about one
     * folder, because the settings screen is where it is undone. Pointing the app at a second folder
     * that also has inline tags will therefore find this already set — the cost of the simpler
     * store, and the settings row is the answer to it.
     */
    private var declinedMove by mutableStateOf(prefs.getBoolean(KEY_MOVE_TAGS_DECLINED, false))

    var moveTagsDeclined: Boolean
        get() = declinedMove
        set(value) {
            declinedMove = value
            prefs.edit().putBoolean(KEY_MOVE_TAGS_DECLINED, value).apply()
        }

    /**
     * Whether the editor shows the Markdown as written — `**`, `> `, `- - -` as characters — rather
     * than hiding the markers and drawing what they mean.
     *
     * Toggled from the editor's own menu and nowhere else, and remembered: someone who wants to see
     * the markup wants to see it in every note, and a switch that reset on every open would be a
     * nag. It changes nothing about the files, only about what is drawn over them.
     */
    private var source by mutableStateOf(prefs.getBoolean(KEY_SHOW_SOURCE, false))

    var showSource: Boolean
        get() = source
        set(value) {
            source = value
            prefs.edit().putBoolean(KEY_SHOW_SOURCE, value).apply()
        }

    /**
     * Whether the format bar offers to add a picture. Off by default: the archive's pictures were an
     * experiment the author does not repeat, the system picker offers full-size photos and nothing
     * else, and a lyric rarely wants one. Pictures already in a note render either way.
     */
    private var pictures by mutableStateOf(prefs.getBoolean(KEY_IMAGE_BUTTON, false))

    var imageButton: Boolean
        get() = pictures
        set(value) {
            pictures = value
            prefs.edit().putBoolean(KEY_IMAGE_BUTTON, value).apply()
        }

    private companion object {
        const val PREFS = "settings"
        const val KEY_SHOW_SOURCE = "show_source"
        const val KEY_IMAGE_BUTTON = "image_button"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_PROSE_FONT = "prose_font"
        const val KEY_PROSE_SIZE = "prose_size"
        const val KEY_PROSE_LEADING = "prose_leading"
        const val KEY_START_TAG = "start_tag"
        const val KEY_PIN_TAG = "pin_tag"
        const val DEFAULT_PIN_TAG = "pinned"
        const val KEY_SORT_BY = "sort_by"
        const val KEY_SORT_ORDER = "sort_order"
        const val KEY_ROW_DENSITY = "row_density"
        const val KEY_MOVE_TAGS_DECLINED = "move_tags_declined"
    }
}
