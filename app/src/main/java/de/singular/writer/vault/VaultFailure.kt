// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

/**
 * Why there is nothing to show, when there is nothing to show.
 *
 * Kept as an enum rather than a message so the UI decides the wording — and so the difference
 * between the first three stays visible in code. They are three genuinely different situations and
 * only one of them is a problem:
 *
 * [NO_FOLDER_CHOSEN] is a fresh install and should read as an invitation.
 * [FOLDER_UNREACHABLE] is a folder that was chosen and is now gone — an SD card pulled, a folder
 * deleted from a computer. That is worth alarming about, gently, and the user's notes are fine.
 * [FOLDER_EMPTY] is a perfectly good folder with nothing in it yet, which is not an error at all.
 */
enum class VaultFailure {
    NO_FOLDER_CHOSEN,
    FOLDER_UNREACHABLE,
    FOLDER_UNREADABLE,
    FOLDER_EMPTY,
}
