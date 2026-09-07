#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Rename the archive's percent tags: 100% -> 100, 50% -> 50, 75% -> 75.

A one-time migration of the notes themselves, not a feature of the app. Run it once over a copy of
the folder, check the diff, then let it reach the phone the way any other edit does.

## Why

`%` is not a character a hashtag can contain, so these three tags could never be written inline the
way every other tag in the archive is. That left them frontmatter-only, and it is also why the
export lost 21 of them: it could not read `#100%` in a body as a tag, so it wrote no `tags:` entry
for it. The app then had to carry a special case on every screen to show a tag it could not index.

Dropping the `%` removes the cause. `100` is an ordinary tag: it can be written inline, indexed,
chipped and removed like any other. Nothing else in the archive is affected — measured, the 33
percent tags are the only `#digit` sequences in all 168 notes.

## What it does, exactly

1. In bodies, `#100%` and the wrapped `#100%#` both become `#100`. Only where the `#` starts a line
   or follows whitespace, which is the app's own tag rule; a `%` anywhere else is left alone.
2. In frontmatter, the `tags:` entries `"100%"`, `"50%"` and `"75%"` become `"100"`, `"50"`, `"75"`,
   keeping the line's own indentation and quoting.
3. Where a body carries the tag and the frontmatter does not — 21 notes, all of them the export's
   omission — the entry is added to the `tags:` list. This is the point of the migration: afterwards
   the two representations agree on every note, as they already do for every other tag.

`updated` is deliberately **not** touched. This is a rename of something the author already wrote,
not a new edit, and the archive's dates are the thing it is kept for.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

TAGS = ("100", "50", "75")

# `#100%` or the wrapped `#100%#`, at a line start or after whitespace — the app's own rule for what
# counts as an inline tag, so nothing mid-word is touched.
IN_BODY = re.compile(r"(^|\s)#(\d+)%#?", re.MULTILINE)


def split(text: str) -> tuple[str, str]:
    """The frontmatter block including its closing delimiter, and the body after it."""
    if not text.startswith("---\n"):
        return "", text
    end = text.index("\n---\n", 3)
    return text[: end + 5], text[end + 5 :]


def rename_in_frontmatter(head: str) -> str:
    return re.sub(r'("|\')(\d+)%\1', lambda m: f"{m.group(1)}{m.group(2)}{m.group(1)}", head)


def declared(head: str) -> list[str]:
    return re.findall(r'-\s*"([^"]*)"', head)


def add_to_tags(head: str, missing: list[str]) -> str:
    """Append entries to the `tags:` list, copying the indentation and quoting already in use."""
    lines = head.split("\n")
    at = next(i for i, line in enumerate(lines) if line.startswith("tags:"))
    existing = []
    i = at + 1
    while i < len(lines) and (lines[i].startswith(" ") or lines[i].startswith("\t")):
        existing.append(lines[i])
        i += 1
    if not existing:  # `tags: []`, or no list at all
        lines[at] = "tags:"
        existing = []
        i = at + 1
    indent = re.match(r"[ \t]*", existing[0]).group(0) if existing else "  "
    added = [f'{indent}- "{tag}"' for tag in missing]
    return "\n".join(lines[:i] + added + lines[i:])


def migrate(text: str) -> str:
    head, body = split(text)
    in_body = sorted({m.group(2) for m in IN_BODY.finditer(body)})
    new_body = IN_BODY.sub(lambda m: m.group(1) + "#" + m.group(2), body)
    new_head = rename_in_frontmatter(head)
    missing = [t for t in in_body if t in TAGS and t not in declared(new_head)]
    if missing:
        new_head = add_to_tags(new_head, missing)
    return new_head + new_body


def main(folder: Path, apply: bool) -> int:
    changed = 0
    for path in sorted(folder.glob("*.md")):
        text = path.read_text(encoding="utf-8")
        new = migrate(text)
        if new == text:
            continue
        changed += 1
        print(f"{'wrote' if apply else 'would change'}: {path.name}")
        if apply:
            path.write_text(new, encoding="utf-8")
    print(f"\n{changed} of {len(list(folder.glob('*.md')))} notes affected")
    return 0


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if a != "--apply"]
    if len(args) != 1:
        print(__doc__)
        print("usage: rename-percent-tags.py <folder> [--apply]")
        raise SystemExit(2)
    raise SystemExit(main(Path(args[0]), apply="--apply" in sys.argv))
