<h1>
  <img src="docs/icon.png" alt="Pocket Prose icon" height="52" align="middle" />
  Pocket Prose
</h1>

**Write, keep, capture inspiration.**

A Markdown notes app for Android that reads a folder you already have. Point it at a directory of `.md` files — via the system file picker, so it needs no storage permission of its own — and it lists, reads, searches and lightly edits them in place. The files stay the database. Uninstall it and the folder is byte-identical to how it found it, apart from edits you actually made.

Built for a personal lyrics archive of 168 notes written over ten years, which is why it is opinionated about leaving things alone.

## What it does not do

This is the short list, and it is the point of the app rather than a set of missing features:

- No renaming files to match their titles or headings, no injecting IDs or app-owned keys into frontmatter, no rewriting frontmatter it did not itself change.
- No importing into an internal database. No required subfolder layout, naming scheme, or index file.
- No auto-deleting a note because a file vanished, and no auto-merging when one changes underneath it — the folder is synced by Syncthing, so that happens. Conflicts are surfaced, never resolved for you.
- No cloud account, no telemetry, no network permission.

## What it does

- **Titles from frontmatter**, never derived from the filename — the two often differ, and duplicate titles across files stay separate notes.
- **A tag tree** built from `/` path segments, kept in sync between the frontmatter list and the inline `#hashtags`, with `F#` in a lyric correctly not being a tag.
- **Relative images and attachments**, resolved against the note's own folder, so `![](attachments/chords.png)` renders and a linked PDF opens in an external viewer.
- **Dates from the file**, not from when the app first saw it.
- **Atomic writes** — temp file plus rename, so a sync client never sees a half-written note.

## Building

Requires a JDK (17+) and the Android SDK. Point the build at your SDK with a git-ignored `local.properties` in the project root:

```properties
sdk.dir=/path/to/Android/Sdk
```

Then:

```sh
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## License

GPL-3.0-**only** — version 3 of the GNU General Public License, and not "or any later version". The full text is in [LICENSE](LICENSE), the copyright notice in [COPYRIGHT](COPYRIGHT).

### Artwork and name

The icon and the wordmark are licensed separately, under **CC BY 4.0**. [COPYRIGHT](COPYRIGHT) lists the files. Their editable sources live beside this repository rather than in it, so a clone can use and modify the exported drawables but cannot re-export them.

The **name** is not licensed by either grant — give a fork its own.

Notes can be set in **Literata** (© 2017 The Literata Project Authors) or **Source Serif 4** (© 2014–2021 Adobe Systems Incorporated), both bundled under the **SIL Open Font License 1.1** — [Literata](licenses/Literata-OFL.txt), [Source Serif](licenses/SourceSerif4-OFL.txt). The Literata faces are static instances cut from the upstream variable fonts and subset to Latin; the Source Serif faces are unmodified upstream files, because that family reserves its name and a modified copy could not keep it. `COPYRIGHT` says exactly how.

## Disclaimer

This project was developed with the assistance of Claude, under my direction and functional review.
