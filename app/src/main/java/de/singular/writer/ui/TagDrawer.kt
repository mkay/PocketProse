// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
 */
@Composable
fun TagDrawer(
    tree: List<Tags.Node>,
    totalNotes: Int,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
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
            items(tree, selected, onSelect)
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
                )
                if (expanded) {
                    Column {
                        node.children.forEach { child ->
                            ChildRows(child, selected, onSelect, depth + 1)
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
private fun ChildRows(node: Tags.Node, selected: String?, onSelect: (String?) -> Unit, depth: Int) {
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
    )
    if (expanded) node.children.forEach { ChildRows(it, selected, onSelect, depth + 1) }
}

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
                .clickable(onClick = onClick)
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
