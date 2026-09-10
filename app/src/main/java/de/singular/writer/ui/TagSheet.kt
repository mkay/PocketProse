// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import de.singular.writer.markdown.Tags

/**
 * The sheet that opens from a note's chip row: every tag in the folder, checkable, plus a field for
 * one that does not exist yet.
 *
 * **One list, one meaning.** A tag is either on this note or it is not; there is no second class of
 * tag here and no explanation of where a tag is stored. An earlier version marked tags the note's
 * text carried but its `tags:` list did not, and said so in a sentence nobody could act on. Which of
 * the two places a tag sits in is `Note.withTags`' problem, and it keeps both in step by itself.
 *
 * **A sheet rather than an `✕` on each chip.** Removing a tag rewrites a file — the `tags:` list and
 * the note's own hashtag line, in one save — and a control that does that on a mis-tap, sitting
 * directly under the text somebody is writing in, is the wrong shape for the consequence. Here the
 * whole change is made deliberately and in one place, and dismissing the sheet writes nothing.
 *
 * **The folder's own tags, not a free-text field first.** 24 tags cover 168 notes and `lyrics/snippet`
 * alone covers 131, so the overwhelmingly common act is filing a note under something that already
 * exists. Typing a new one is possible and is deliberately the second thing on the sheet.
 *
 * Ordered the way the drawer orders them — alphabetically, with the ones already on this note first,
 * because those are what the reader came to check. No `#` anywhere: a tag reads `lyrics/snippet`.
 *
 * **Chips rather than a checklist, since 2026-09-10.** A list of 24 rows is a scroll for something
 * the reader wants to see all of at once, and it put the field for a new tag either at the top of a
 * list they were scrolling away from or below a list they never reached. As chips the whole folder
 * fits above the fold, and the note's own tags read as the same objects the chip row under the
 * editor shows — which is what they are.
 *
 * **The × is a marker, not a second target.** The whole chip toggles; the cross says what a tap will
 * do. Two targets in one chip is how a mis-tap removes a tag from a note, and this sheet exists in
 * the first place because that gesture was judged the wrong shape for the consequence.
 *
 * **The groups are fixed when the sheet opens.** Ticking a chip does not move it into the group
 * above; it fills in place. A chip that jumped between groups would reflow every chip after it and
 * land the next tap on a different tag — the same reason the ordering was already frozen per opening
 * rather than re-sorted per tap. The cost is that "On this note" names the group as it stood when
 * the sheet opened, so a chip unticked during this visit sits there outlined. That is the smaller
 * lie: the reader can see it is outlined, and nothing moved under their thumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSheet(
    selected: List<String>,
    known: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val on = selected.toSet()

    // On this note first, then the rest of the folder. Within each group alphabetical, so a tag is
    // where it was last time — see Tags.tree for why frequency order was rejected there too.
    //
    // Sorted once per opening rather than on every tap: a row that jumped to the top the instant it
    // was ticked would make a second tap land on whatever slid into its place.
    val folder = remember(known) {
        val start = on
        (known + start).sortedWith(
            compareByDescending<String> { it in start }.then(String.CASE_INSENSITIVE_ORDER),
        )
    }

    // A tag typed into the field is not in `known`: the index only learns a tag when the file is
    // saved, which is after the sheet is gone. So the sheet remembers what was typed into it and
    // shows those rows itself — otherwise the field emptied and nothing appeared to have happened,
    // and the tag only turned up back in the list view.
    //
    // They are drawn under the field, outside the scrolling list, rather than as its first rows.
    // Inside the list they landed at the top of something that is usually scrolled somewhere else,
    // so the row existed but was off-screen, and scrolling the list back to it fights the list's own
    // habit of holding its position by key when an item is inserted above. Out here the row is where
    // it was typed and cannot be scrolled away from. Few enough to sit unbounded: this is what one
    // sitting at the sheet has invented, not the folder's tag list.
    //
    // Kept in a list of its own rather than derived from `selected`, so unticking one leaves the row
    // where it is instead of making it vanish with no way to tick it again. Held for as long as the
    // sheet is open; dismissing it drops the list, by which time `known` has the tag.
    val typedHere = remember(known) { mutableStateListOf<String>() }

    // The two groups, fixed for as long as the sheet is open — see the note on the class. `mine` is
    // what the note carried on the way in, plus anything invented here; `rest` is the folder.
    val mine = remember(known) { folder.filter { it in on }.toMutableStateList() }
    val rest = remember(known) { folder.filterNot { it in on } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .padding(bottom = 12.dp)
                // Capped rather than free: the sheet must not grow past the point where the note
                // behind it disappears. All 24 of this folder's tags fit inside it as chips, where
                // as rows they did not come close.
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 12.dp),
            ) {
                // The dialogs' heading, worn by the sheet: same mark, same weight, same order. A
                // sheet is not a dialog, but it is the same kind of moment and there is no reason
                // for it to introduce itself differently.
                Box(Modifier.weight(1f)) {
                    DialogHeading(
                        Icons.AutoMirrored.Outlined.Label,
                        stringResource(R.string.tag_sheet_title),
                    )
                }
                // Outlined rather than bare text. `primary` in this palette is a muted taupe sitting
                // a hair off `onSurfaceVariant`, so a plain text button beside a title read as a
                // second piece of the heading rather than as the way out.
                OutlinedButton(onClick = onDismiss, shape = ControlShape) {
                    Text(text = stringResource(R.string.tag_sheet_done))
                }
            }

            // At the top, where it cannot be pushed under the folder's own tags. It was under them
            // until 2026-09-10, which put the one control somebody came here to type in behind
            // everything they came here not to type.
            NewTagField(
                onAdd = { tag ->
                    // Typing a tag puts it on the note; it never takes one off, whatever state the
                    // chip was in. It joins the top group, so the answer to typing is always a
                    // filled chip in the first cluster rather than a change somewhere below.
                    if (tag !in typedHere) typedHere.add(tag)
                    if (tag !in mine) mine.add(0, tag)
                    if (tag !in on) onToggle(tag)
                },
            )

            if (mine.isNotEmpty()) {
                GroupLabel(stringResource(R.string.tag_sheet_on_note))
                TagChips(mine, on, onToggle)
            }
            if (rest.isNotEmpty()) {
                GroupLabel(stringResource(R.string.tag_sheet_from_folder))
                TagChips(rest, on, onToggle)
            }
        }
    }
}

/**
 * The same sheet for a selection of notes: every tag in the folder, switchable on for all of them
 * or off for all of them.
 *
 * **Three states, because a selection has three.** A tag is on every ticked note, on none of them,
 * or on some — and the third is the common case, since a selection is usually made to *fix* filing
 * rather than to admire it. A mixed chip shows its count, `radio 2/5`, and a tap on it puts the tag
 * on all five; a second tap takes it off all five. There is no way back to "some", which is the one
 * state nobody opened this sheet to produce.
 *
 * **Nothing is written until Apply.** The note's own [TagSheet] toggles live because each tap edits
 * a draft that the editor saves later as one file; here each tap would be a write across the whole
 * selection, so the sheet collects decisions and hands them over once. Dismissing hands over none.
 *
 * Grouped as the note's sheet groups: what the selection carries first — wholly or in part — then
 * the rest of the folder, both alphabetical and both fixed for as long as the sheet is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTagSheet(
    counts: Map<String, Int>,
    total: Int,
    known: Set<String>,
    onApply: (add: List<String>, remove: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // What the sheet has decided so far: true puts the tag on every note, false takes it off every
    // note, absent leaves the notes as they are. Only the decisions travel back — a chip that was
    // tapped on and then off again is an absence, not a removal.
    val decided = remember(known) { mutableStateMapOf<String, Boolean>() }

    val carried = remember(known, counts) {
        (counts.keys).sortedWith(String.CASE_INSENSITIVE_ORDER).toMutableStateList()
    }
    val rest = remember(known, counts) {
        (known - counts.keys).sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun stateOf(tag: String): ChipState {
        decided[tag]?.let { return if (it) ChipState.ALL else ChipState.NONE }
        val n = counts[tag] ?: 0
        return when {
            n == 0 -> ChipState.NONE
            n == total -> ChipState.ALL
            else -> ChipState.SOME
        }
    }

    // On wins from anywhere but "on": a mixed chip goes to all before it goes to none, because
    // filing is the likelier intent and taking a tag off notes that carry it deserves a plain
    // signal on screen first.
    fun toggle(tag: String) {
        decided[tag] = stateOf(tag) != ChipState.ALL
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .padding(bottom = 12.dp)
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 12.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    DialogHeading(
                        Icons.AutoMirrored.Outlined.Label,
                        pluralStringResource(R.plurals.retag_title, total, total),
                    )
                }
                // Filled where the note's sheet has an outlined Done: that one is a way out and
                // this one is the write.
                Button(
                    onClick = {
                        onApply(
                            decided.filterValues { it }.keys.toList(),
                            decided.filterValues { !it }.keys,
                        )
                    },
                    enabled = decided.isNotEmpty(),
                    shape = ControlShape,
                ) {
                    Text(text = stringResource(R.string.retag_apply))
                }
            }

            NewTagField(
                onAdd = { tag ->
                    // A tag typed here joins the top group, so the answer to typing is always a
                    // filled chip in the first cluster — the same promise the note's sheet makes.
                    if (tag !in carried && tag !in rest) carried.add(0, tag)
                    decided[tag] = true
                },
            )

            if (carried.isNotEmpty()) {
                GroupLabel(stringResource(R.string.retag_on_selection))
                SelectionChips(carried, counts, total, ::stateOf, ::toggle)
            }
            if (rest.isNotEmpty()) {
                GroupLabel(stringResource(R.string.tag_sheet_from_folder))
                SelectionChips(rest, counts, total, ::stateOf, ::toggle)
            }
        }
    }
}

/** Which of a selection's notes a tag is on. */
private enum class ChipState { NONE, SOME, ALL }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectionChips(
    tags: List<String>,
    counts: Map<String, Int>,
    total: Int,
    stateOf: (String) -> ChipState,
    onToggle: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        for (tag in tags) {
            when (stateOf(tag)) {
                ChipState.ALL -> TagChip(tag = tag, checked = true, onClick = { onToggle(tag) })
                ChipState.NONE -> TagChip(tag = tag, checked = false, onClick = { onToggle(tag) })
                // The count is the chip's label for as long as it is mixed: `radio 2/5` says exactly
                // what a tap will change, where a half-filled chip would only say "something".
                ChipState.SOME -> TagChip(
                    tag = tag,
                    checked = false,
                    detail = "${counts[tag]}/$total",
                    onClick = { onToggle(tag) },
                )
            }
        }
    }
}

/** The small heading over a cluster of chips. Quiet, because it labels rather than says. */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

/** One cluster: every tag in it, wrapping. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChips(tags: List<String>, on: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        for (tag in tags) {
            TagChip(tag = tag, checked = tag in on, onClick = { onToggle(tag) })
        }
    }
}

/**
 * The field for a tag the folder does not have yet.
 *
 * Lowercased in the field as it is typed, not on the way out, and a leading `#` is quietly dropped
 * for anyone who types one out of habit. The archive was deliberately case-folded once already and a
 * `Lyrics` beside the existing `lyrics` would split a tag in the drawer with no UI that makes it
 * look like anything but a bug.
 *
 * **The fold is visible while it happens.** It ran only on commit until 2026-09-10, so somebody who
 * typed a capital saw their capital, saved, and found it lowered afterwards — the app correcting
 * them behind their back over something it had never mentioned. `Tags.typed` folds the field
 * instead, and the keyboard is told not to capitalise in the first place, which is where most of
 * those capitals came from: a phone IME capitalises the first letter of a text field by default,
 * so the user was not even the one who typed it.
 */
@Composable
private fun NewTagField(onAdd: (String) -> Unit) {
    var typed by remember { mutableStateOf("") }
    val tag = Tags.normalize(typed)
    val accepted = Tags.accepts(tag)
    // Why it is being refused, or null while there is nothing to refuse. Said out loud rather than
    // greying the button and leaving the reader to guess, and *not* fixed silently: swallowing the
    // space as it is typed would be the same quiet correction the lowercasing above stopped doing.
    val refusal = when {
        tag.isEmpty() || accepted -> null
        ' ' in tag -> R.string.tag_no_spaces
        else -> R.string.tag_no_hash
    }
    val submit = {
        if (accepted) {
            onAdd(tag)
            typed = ""
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = Tags.typed(it) },
            label = { Text(text = stringResource(R.string.tag_sheet_new)) },
            singleLine = true,
            shape = ControlShape,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            isError = refusal != null,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = submit, enabled = accepted) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.tag_sheet_add),
            )
        }
    }
        refusal?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

/**
 * One tag, as a chip. Tap it to put it on the note or take it off.
 *
 * On is filled and carries a cross; off is an outline. The two states differ in ground and not only
 * in a mark, because a cluster of 24 has to be readable at a glance — which of these are mine is the
 * question somebody opens this sheet to answer, and a small tick beside each of 24 items answers it
 * slowly.
 *
 * **The cross is not a button.** The whole chip is one target and the cross only says what a tap
 * will do. A separate × inside a chip is a 16dp target for the one gesture here that takes a tag off
 * somebody's note, sitting a few pixels from the target that puts one on.
 *
 * The chip is sized for a thumb rather than for the text — the note's own chip row is `labelSmall`
 * in a 6dp box because it is being read, and this one is being aimed at.
 */
@Composable
private fun TagChip(tag: String, checked: Boolean, onClick: () -> Unit, detail: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = ControlShape,
        color = if (checked) scheme.secondaryContainer else Color.Transparent,
        contentColor = if (checked) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        border = if (checked) null else BorderStroke(1.dp, scheme.outline),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 12.dp, end = if (checked) 8.dp else 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
