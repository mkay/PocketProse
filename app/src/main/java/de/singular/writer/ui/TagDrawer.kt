// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import de.singular.writer.markdown.Tags

/**
 * The tag tree, as a side panel.
 *
 * **No `#` anywhere.** A tag reads `lyrics/snippet`, the way a folder path does, because the hash is
 * storage and the audience for this app has no reason to meet it. The slash stays: it is the one
 * piece of structure that is genuinely in the data, it is legible to anyone who has seen a file
 * path, and hiding it would make `lyrics/snippet` and `snippet` look like the same tag.
 *
 * Alphabetical at every level — see [Tags.tree] for why frequency order was rejected. Counts sit on
 * the right and carry the weight instead.
 *
 * **An empty tree says why it is empty.** It showed the heading and then nothing until 2026-09-09,
 * which explains itself to nobody for any reason — and there are two reasons. See [EmptyTags].
 */
@Composable
fun TagDrawer(
    tree: List<Tags.Node>,
    totalNotes: Int,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    // Null where a long-press should do nothing — the settings screen borrows this drawer to pick a
    // start view, and renaming the archive from inside a preference is not what that gesture means
    // there.
    onRename: ((String) -> Unit)? = null,
    // How many notes still have tags written into their text — the reason the tree is empty, when it
    // is empty for that reason. Null in the settings screen's borrowed copy, which is a picker and
    // has no business offering to rewrite the folder from inside a preference.
    tagsInText: Int? = null,
    onMoveTags: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyColumn {
            item {
                Text(
                    text = stringResource(R.string.drawer_tags),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                )
            }
            item {
                DrawerRow(
                    label = stringResource(R.string.drawer_all_notes),
                    count = totalNotes,
                    depth = 0,
                    selected = selected == null,
                    onClick = { onSelect(null) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            // Above the tags, not below them, and never instead of them.
            //
            // Keying this on an empty tree was wrong for a day: one tag added by hand — or by a sync
            // landing a single note — filled the tree and took the notice with it, leaving a drawer
            // that showed one tag out of 25 and nothing to say the other 24 were sitting in the
            // words. A folder with one tag showing looks like the truth in a way a blank panel never
            // does, so that was the worse of the two states. The condition is therefore the one the
            // sentence itself makes: tags are still written in the text.
            //
            // It sat under the tree until 2026-09-10, which read better — "here are your tags, and
            // here are the ones that are not here yet" — and worked worse. This archive has 24 tags
            // and the drawer is a list you scroll, so a notice at the end is a notice below the fold
            // for exactly the folder that most needs it. Above the tree it costs a scroll on every
            // open until the move is made; that price is paid by somebody who has been told why they
            // are paying it, which the old position could not manage at all.
            //
            // Under the All notes row rather than over it: that row is the drawer's way out of a
            // filter and must not move down for anything.
            if (tree.isEmpty() || (tagsInText != null && tagsInText > 0)) {
                item { EmptyTags(tagsInText, onMoveTags) }
            }
            items(tree, selected, onSelect, onRename)
        }
    }
}

/**
 * What the drawer says when tags are missing from it.
 *
 * Two different facts wear the same empty list, and the difference is the whole point of this
 * existing. A folder genuinely without tags is a folder someone has not filed yet. A folder from an
 * editor that kept tags as `#hashtags` in the body has tags — 25 of them, on 165 of its 168 notes —
 * and every one of them is invisible here. Shown the same blank panel, both look like the app not
 * working.
 *
 * The second case does not need the list to be empty, only incomplete, so this appears above a tree
 * with tags in it too. See the call site for why above and not below.
 *
 * **The offer belongs here as well as in the banner, and this one does not expire.** The banner is
 * an interruption and is dismissed for good, because re-asking to rewrite somebody's whole folder is
 * the one nag that cannot be shrugged off. This is not an interruption: the user opened the drawer
 * to filter by a tag and found none, and this is the answer to the question they just asked. It
 * costs nothing to anyone who is reading rather than filtering, and it disappears on its own the
 * moment there is nothing left to move.
 */
@Composable
private fun EmptyTags(tagsInText: Int?, onMoveTags: (() -> Unit)?) {
    val offering = tagsInText != null && tagsInText > 0
    // The library's banner, worn by the drawer: the same sentence, the same count beneath it in the
    // same quieter style, the same single action right-aligned on a row of its own — so somebody
    // who has met one recognises the other rather than reading them as two different offers.
    //
    // The ground had to be found rather than copied. The banner is `surfaceContainerLow` because
    // that is a step *down* from the library's page; the drawer is already that exact colour, so
    // the same token here is no block at all. `surfaceContainer` is the one step that moves away
    // from `surfaceContainerLow` in both schemes — lighter in dark (2F2A2A to 3E3739), darker in
    // light (F7F4F4 to F2EEEE) — because the two ramps run in opposite directions and there is no
    // token that means "darker" in both.
    //
    // Inset and cornered rather than a full-bleed band. A band across a 300dp panel reads as a
    // section of the drawer, which is what the tag tree will be when there is one; a block that
    // stops short of the edges reads as a notice sitting in the panel, which is what it is. That
    // distinction earns its keep now that the notice sits above the tree and has rows under it.
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(ControlShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
    ) {
        Text(
            text = if (offering) {
                pluralStringResource(R.plurals.drawer_tags_in_text, tagsInText, tagsInText)
            } else {
                stringResource(R.string.drawer_no_tags)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (offering) {
            // The count is in the sentence above, so what follows is the reason and the remedy —
            // quieter, because the fact is the thing being answered and this is the footnote to it.
            Text(
                text = stringResource(R.string.drawer_tags_in_text_why),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // No "No thanks" beside it, unlike the banner. There is nothing here to dismiss: this
            // is the answer to a question the user asked by opening the drawer, and it goes when
            // the folder no longer needs it.
            if (onMoveTags != null) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    // Always "tags" here, whatever else the folder needs moved. This is the tag
                    // drawer answering why its list is short; titles are not its subject and naming
                    // them on this button would answer a question nobody asked here. The confirm it
                    // opens states the full scope, which is where the full scope belongs.
                    TextButton(onClick = onMoveTags) {
                        Text(
                            stringResource(
                                R.string.move_tags_banner_action,
                                stringResource(R.string.move_tags_what_tags),
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One node and, if it is open, its children.
 *
 * Written as an extension on the lazy scope rather than as a nested composable so that a tree of 25
 * rows is 25 list items rather than one item containing a column — which matters not for
 * performance at this size but for the scroll position surviving a rotation.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.items(
    nodes: List<Tags.Node>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onRename: ((String) -> Unit)?,
    depth: Int = 0,
) {
    nodes.forEach { node ->
        item(key = node.path) {
            // Open by default when something inside is the current filter, so selecting a child and
            // reopening the drawer does not hide what is selected.
            var expanded by rememberSaveable(node.path) {
                mutableStateOf(selected != null && selected.startsWith("${node.path}/"))
            }
            Column {
                DrawerRow(
                    label = node.segment,
                    count = node.total,
                    depth = depth,
                    selected = selected == node.path,
                    expandable = node.children.isNotEmpty(),
                    expanded = expanded,
                    onExpandToggle = { expanded = !expanded },
                    onClick = { onSelect(node.path) },
                    onLongClick = onRename?.let { { it(node.path) } },
                )
                if (expanded) {
                    Column {
                        node.children.forEach { child ->
                            ChildRows(child, selected, onSelect, onRename, depth + 1)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A child and its own children.
 *
 * The archive nests exactly one level, so this recursion is never more than one deep in practice —
 * but it is written to recurse anyway, because `CLAUDE.md`'s rule is that the tree comes from path
 * segments, and a rule that only works to a fixed depth is not that rule.
 */
@Composable
private fun ChildRows(
    node: Tags.Node,
    selected: String?,
    onSelect: (String?) -> Unit,
    onRename: ((String) -> Unit)?,
    depth: Int,
) {
    var expanded by rememberSaveable(node.path) {
        mutableStateOf(selected != null && selected.startsWith("${node.path}/"))
    }
    DrawerRow(
        label = node.segment,
        count = node.total,
        depth = depth,
        selected = selected == node.path,
        expandable = node.children.isNotEmpty(),
        expanded = expanded,
        onExpandToggle = { expanded = !expanded },
        onClick = { onSelect(node.path) },
        onLongClick = onRename?.let { { it(node.path) } },
    )
    if (expanded) node.children.forEach { ChildRows(it, selected, onSelect, onRename, depth + 1) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerRow(
    label: String,
    count: Int,
    depth: Int,
    selected: Boolean,
    onClick: () -> Unit,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onExpandToggle: () -> Unit = {},
    // The All notes row passes none: there is no such tag to rename.
    onLongClick: (() -> Unit)? = null,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val content =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface

    Surface(color = background, shape = RoundedCornerShape(6.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onLongClick == null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    },
                )
                .padding(start = 12.dp + (depth * 18).dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        ) {
            if (expandable) {
                val turn by animateFloatAsState(if (expanded) 0f else -90f, label = "chevron")
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.drawer_collapse else R.string.drawer_expand,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(turn)
                        .clickable(onClick = onExpandToggle),
                )
            } else {
                Spacer18()
            }
            Text(
                text = label,
                // bodyLarge, the same size the note list sets a title in. The drawer was a step
                // down at bodyMedium, which made the app's second-most-used screen its smallest
                // type — and `CLAUDE.md` asks for large readable type throughout, not only where
                // the writing is.
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = content,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count.toString(),
                // One step under the label rather than two: a count is secondary, but at 12sp it
                // was small print beside a tag someone is meant to read at a glance.
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) content else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Keeps a childless row's label in line with an expandable one's. */
@Composable
private fun Spacer18() = androidx.compose.foundation.layout.Spacer(Modifier.size(20.dp))
