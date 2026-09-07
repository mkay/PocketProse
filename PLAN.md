# Pocket Prose — build plan

A writing app for Android that happens to store Markdown. Package `de.singular.writer`, GPL-3.0-only, aimed at F-Droid like its three siblings.

The audience does not know what Markdown is and must never be made to care. That single sentence decides most of what follows: the editor hides its syntax, tags are chips rather than hashtags, and the word "Markdown" appears nowhere in the UI.

Read `CLAUDE.md` first — it holds the file-format contract and the prime directive. This file is the route from nothing to an app that satisfies it.

## Decisions taken before any code

**Editor: live-styled, markers hidden.** Bold shows bold and the `**` is invisible; the markers fade in only while the cursor is inside that word. This is the most ambitious of the three options considered and the only one that honours "Markdown is the tech part in the background".

**One view, always editable.** Tap a note in the library and the cursor is already there. No read mode, no edit button, no mode indicator. Formatting controls exist only as a bar that appears when text is selected. Images render inline in the same view they are edited in.

**Tags are chips; the app keeps both representations in sync.** The user never types or sees a `#`. Adding a chip writes both the frontmatter list and the inline hashtag. `100%` and `50%` are frontmatter-only — they cannot be written as inline hashtags at all, and their bodies (which carry Bear's `#100%#` wrapping) are never touched.

**A note with no prose gets a muted placeholder line** — "Nothing written yet" — where the excerpt would go, keeping every library row the same height. This is not a rare case: see the survey below.

## What the archive actually contains

Verified on 2026-09-07 by pulling `/sdcard/Recordings/Lyrics` off the Fairphone and measuring it, not by trusting the description. Everything in `CLAUDE.md`'s corpus section holds exactly — 168 notes, 24 distinct tags, 3 untagged, 5 tags maximum on one note, 19 PNGs and 7 PDFs, and every note carrying exactly `title`, `created`, `updated`, `tags` and no other key.

Four things the measurement added:

**40 notes have no body at all beyond their hashtag line.** `Die Eule.md` in full, after its frontmatter, is a blank line and `#lyrics/titel`. Once hashtags are hoisted into chips these render as empty pages, and **just under a quarter of the library** has no excerpt to show. Hence the placeholder line. These are title ideas, and the app should treat them as a legitimate kind of note rather than as a defect.

This said 36 until phase 1 measured it properly. 36 was reached by grouping byte-identical bodies, which finds only the notes whose tag line is shared with another note and misses the four whose tag combination is unique. Counting the excerpts themselves — which is what the screen will actually show — gives 40, and `CorpusTest` now asserts it.

**Hashtag lines are usually at the bottom, not the top.** 85 notes end with one, 60 begin with one, 1 has it in the middle, and 8 carry them in more than one place. `Casablanca (chords).md` has `#chords #radio` above the images and `#lyrics/snippet` alone below them. Hoisting tags out of the display therefore touches several places in a file, and putting them back byte-identically is the delicate part of the tag work — not the parsing.

**Frontmatter and inline hashtags agree in all 168 notes**, once the two `%` tags are excluded. The invariant is currently perfect. The app's job is to preserve it, which is a far smaller problem than reconciling drift — do not write reconciliation machinery for a conflict that does not exist.

**The test fixture does not cover the tag hazard.** `Lyrics_Test` contains no `#` chord anywhere. In the whole archive there is exactly one sharp, in `Radio (Song Notes).md`: "Tarantino für zwei in F# Moll." — an `F#`, with no `F#m` and no `C#` despite what `CLAUDE.md` says. The rule survives it without a special case, `#` there being preceded by a letter rather than by whitespace, but the fixture cannot demonstrate that. Phase 1 covers it as a unit test and a sharp gets added to the fixture.

**Percent tags are three, not two, and the frontmatter is not their index.** `100%`, `50%` and — unrecorded anywhere until now — `#75%`, in `Sieger sehen anders aus.md`. Both spellings occur in bodies: `#100%` bare 19 times, Bear's wrapped `#100%#` 11 times. **21 percent tags live in a body with no frontmatter entry**, and none the other way round, so `CLAUDE.md`'s "index them from frontmatter" would lose 21 tags the author can plainly see in their own text. What the app should do about that is an open question — see the end of this file.

Two consequences already handled. A line reading `#album/debut #100% #busch` is a line of tags, not prose: requiring every word to be a *writable* hashtag made all of `Müde.md`'s first line prose, and its library row led with its own tags instead of with the song. And no percent tag anywhere in the archive is embedded in a sentence — all 33 sit on a tag line — so counting them costs no hidden words.

**Three notes carry HTML comments, and one attachment folder does not exist.** 24 instances of `<!-- {"embed":"true", "preview":"true"} -->`, all beside a PDF link, all left by the Bear export. They are invisible in any renderer and are stripped for display — never from the file. More importantly, the three byte-identical `Wer geht vor` duplicates link into `Wer geht vor/`, the un-migrated Bear layout, and **that folder is not in the archive**: 24 dead links. Only `Wer geht vor.md` itself has the proper `## Anhänge` list into `attachments/`. Phase 5 must render a dead link as a link, without pretending to have the file, and must never repair one.

**The setext hazard is latent, not live.** `CLAUDE.md` says five notes use `---` as a horizontal rule and that two of them put it directly after a line of text, where strict CommonMark would read it as a setext H2. Measured: **two** notes contain a bare `---` in the body (`Atlantik.md` and `Sieger sehen anders aus.md`) and **twenty** use `- - -`, one note using both, so 21 notes carry a rule. Every bare `---` in the archive follows a blank line or another rule — **none** directly follows prose, so the ambiguity does not occur in the data at all today.

That does not make the decision optional. The moment the author types `---` under a line, a lyric would silently become a heading. `Blocks` therefore refuses setext unconditionally, and the case is pinned by a synthetic test rather than a corpus one, since the corpus cannot demonstrate it.

One smaller correction: the `Wer geht vor` trio has 959-byte bodies, not 957.

`CLAUDE.md` itself still carries the old figures. Its corpus section is written to be trusted without re-measuring, so it is worth bringing into line.

## Layout

Single Gradle module `:app`, cloned from TitleTrack's skeleton: AGP 9.2.1, Compose BOM 2026.06.01, compileSdk and targetSdk 36, minSdk 26, Lifecycle pinned at 2.9.4, `vcsInfo { include = false }` so an F-Droid rebuild can match byte-for-byte, and the `checkNoHardcodedUiStrings` verification task. `MainActivity` extends `AppCompatActivity` for `AppCompatDelegate.setApplicationLocales`, exactly as in the other three, and for the same reason.

    de/singular/writer/
      markdown/  Frontmatter, Blocks, Inline, Tags, Excerpt    pure Kotlin, no Android, unit-tested
      vault/     Vault, Note, NoteIndex, Attachments, Conflict, VaultFailure
      ui/        LibraryScreen, EditorScreen, TagDrawer, FormatBar, NoteImage,
                 Theme, Common, SettingsScreen, AboutScreen
      MainActivity, WriterViewModel, Settings

The `markdown/` split mirrors TitleTrack's `audio/` and Crystal Ball's `chords/`: the logic whose failure is expensive and invisible lives in files with no Android in them, so the frightening cases are `./gradlew test` rather than a phone with the real archive on it. A frontmatter round-trip bug is this app's equivalent of the tuner answering an octave out.

## Three engineering positions

**Hand-rolled Markdown, no library.** Every edge case in the archive is a place where a CommonMark-correct library is wrong by the user's intent: `---` after a text line is a setext H2 to the spec and a horizontal rule here; `#100%#` is Bear's wrapping and must survive untouched; `F#` must not be a tag. A library gets these right by the standard and wrong by the archive, and bending it costs more than the parser we need. The supported vocabulary is deliberately tiny — headings, bold, italic, links, images, rules, lists — because this is a lyrics app and not a document processor.

**Frontmatter is re-emitted, never re-serialised.** `Frontmatter` holds the original raw lines alongside a parsed view. A write replaces only the lines whose keys actually changed and passes everything else through byte-for-byte, including quoting, ordering and any key we do not understand. This is what makes definition-of-done item 8 achievable instead of aspirational. An app that parses the block into a map and dumps it back has already lost, and that is precisely how the previous candidates failed.

**SAF cannot do an atomic replace, and the plan says so.** `DocumentsContract.renameDocument` refuses an existing target, so "temp file plus rename" becomes write-temp, delete-original, rename — which has a real if tiny window. There is no way around this through SAF. The mitigation is that a crash leaves an intact temp file rather than a truncated note, and that startup recovers from one. This is a platform limit rather than a shortcut, and it should be documented in the code where it lives.

## Palette

Warm neutral throughout: Tailwind's `stone` ramp rather than TitleTrack's cool one, `#FAFAF9` down to `#0C0A09`, with `#78716C` as the mid. Surfaces carry the character and the accent is nearly absent — for a writing app the page is the design. A single restrained clay accent appears mostly as shape (the selection format bar, a chosen tag) and barely as type.

Exact accent value and its contrast numbers get settled in phase 0 against measurement, and documented in `Theme.kt` in the same style as TitleTrack's — the reasoning recorded beside the constant so a later tidy-up cannot quietly undo a decision.

## Phases

Each phase ends somewhere verifiable. The app reads the entire archive correctly before it is allowed to write a single byte.

**0 — Skeleton.** Gradle, manifest, `Theme.kt`, SAF folder picker with a persisted tree grant lifted from TitleTrack's `RecordingStore`, and a list of bare filenames. Done when it installs on the Fairphone, opens `Lyrics_Test`, and still has the grant after a reboot.

**1 — `markdown/`, tests only, no UI.** Frontmatter round-trip on all 168 real notes. The tag rule with `F#`, `## Strophe`, `#100%#` and a multi-tag line as explicit cases. Block parsing where `---` at the top of a file is frontmatter and `---` anywhere else is a rule, with `- - -` identical. Excerpt extraction, including the 36 empty ones. Done when the tests encode every edge case above and pass.

**2 — `vault/`, read-only.** SAF listing, note reading, the in-memory index, change detection by content hash rather than mtime (Nextcloud rewrites mtimes), and NFC/NFD-tolerant name matching for the macOS-origin filenames. Done when items 1, 2, 3, 6, 7 and — the one that matters — 8 all verify against a copy of the real archive.

**3 — Library screen.** Title, excerpt or placeholder, date and tag chips per row; the tag drawer; full-text search. One design constraint from the data: `lyrics/snippet` covers 131 of 168 notes, so the tree needs counts and the useful filters are all in the long tail. Do not build a UI that assumes an even spread.

**4 — Editor.** The `BasicTextField(state:)` plus `OutputTransformation` spike, promoted to the real screen. Always editable, no chrome, format bar on selection only. The first writes happen here, so item 8 is re-verified afterwards: open every note, edit none, `md5sum` unchanged.

**5 — Images and attachments.** Relative-path resolution against the note's own folder, both the `attachments/` form and the flat sibling form, a small `BitmapFactory` loader with an LRU cache, and PDF links opening by intent. No Coil — 26 images at 151×164 do not justify a dependency. Done when items 4 and 5 pass.

**6 — Tag chips, two-way sync.** The most dangerous write path in the app, deliberately last, and informed by the placement survey above. Adding a chip writes the frontmatter list and the inline hashtag; removing one removes both; `100%` and `50%` are frontmatter-only and their bodies are never touched.

**7 — Sync, conflicts, polish.** Foreground re-read, pull-to-refresh, keep-both on conflict with the difference surfaced, never auto-delete on a vanished file. Then settings, about, German strings, fastlane metadata and the F-Droid layout.

## The risk worth naming

Hiding `**` means the displayed text is shorter than the stored text, and a wrong offset mapping is a crash on somebody's song. `BasicTextField(state: TextFieldState)` with an `OutputTransformation` maintains that correspondence for us, which is far safer than hand-writing an `OffsetMapping`. The genuine unknown is the fade-in-on-cursor behaviour, because an `OutputTransformation` is not handed the selection and must be rebuilt as it changes, which risks a render feedback loop.

**This is spiked before any editor UI is designed.** If it does not come out clean, the fallback is dimmed markers rather than hidden ones — a decision to take on a working prototype, not in advance, and one that changes the product enough to be worth knowing early.

## Definition of done

`CLAUDE.md`'s eight checks, unchanged, against a copy of the real archive. Item 8 is the one that matters and it is checked at the end of every phase from 2 onward, not only at the end.

## Open question, for phase 6

**Do body-only percent tags belong in the index?** 21 of them exist — `#100%` written in a note whose frontmatter does not list `100%`. Indexing from the frontmatter alone, as `CLAUDE.md` says, means the drawer shows `100%` on 11 notes when the author has written it on far more; indexing from the body too means the drawer disagrees with the frontmatter, and phase 6 has to decide whether saving a note reconciles them — which would write to 21 files that the user did not edit, and that is exactly the kind of unbidden tidying the prime directive forbids.

The safe reading is: index from both so the drawer tells the truth about what is written, and never write a reconciliation the user did not ask for. That is a decision about the archive's meaning, so it is the author's to make.
