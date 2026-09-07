# Pocket Prose — build plan

A writing app for Android that happens to store Markdown. Package `de.singular.writer`, GPL-3.0-only, aimed at F-Droid like its three siblings.

The audience does not know what Markdown is and must never be made to care. That single sentence decides most of what follows: the editor hides its syntax, tags are chips rather than hashtags, and the word "Markdown" appears nowhere in the UI.

Read `CLAUDE.md` first — it holds the file-format contract and the prime directive. This file is the route from nothing to an app that satisfies it.

## Decisions taken before any code

**Editor: live-styled, markers hidden.** Bold shows bold and the `**` is invisible; the markers fade in only while the cursor is inside that word. This is the most ambitious of the three options considered and the only one that honours "Markdown is the tech part in the background".

**One view, always editable.** Tap a note in the library and the cursor is already there. No read mode, no edit button, no mode indicator. Formatting controls exist only as a bar that appears when text is selected. Images render inline in the same view they are edited in.

**Tags are chips; the app keeps both representations in sync.** The user never types or sees a `#`. Adding a chip writes both the frontmatter list and the inline hashtag. This held for every tag except three until phase 6 — see the percent rename below.

**A note with no prose gets a muted placeholder line** — "Nothing written yet" — where the excerpt would go, keeping every library row the same height. This is not a rare case: see the survey below.

## What the archive actually contains

Verified on 2026-09-07 by pulling `/sdcard/Recordings/Lyrics` off the Fairphone and measuring it, not by trusting the description. Everything in `CLAUDE.md`'s corpus section holds exactly — 168 notes, 24 distinct tags, 3 untagged, 5 tags maximum on one note, 19 PNGs and 7 PDFs, and every note carrying exactly `title`, `created`, `updated`, `tags` and no other key.

Four things the measurement added:

**40 notes have no body at all beyond their hashtag line.** `Die Eule.md` in full, after its frontmatter, is a blank line and `#lyrics/titel`. Once hashtags are hoisted into chips these render as empty pages, and **just under a quarter of the library** has no excerpt to show. Hence the placeholder line. These are title ideas, and the app should treat them as a legitimate kind of note rather than as a defect.

This said 36 until phase 1 measured it properly. 36 was reached by grouping byte-identical bodies, which finds only the notes whose tag line is shared with another note and misses the four whose tag combination is unique. Counting the excerpts themselves — which is what the screen will actually show — gives 40, and `CorpusTest` now asserts it.

**Hashtag lines are usually at the bottom, not the top.** Re-measured for phase 6, counting runs rather than notes: 110 tag lines sit at the foot of a note, 66 at the head, 3 in the middle, and **13 notes carry them in more than one place** — not the 8 counted earlier. `Casablanca (chords).md` has `#chords #radio` above the images and `#lyrics/snippet` alone below them; `Helen weiss das auch.md` ends with three consecutive tag lines. Several are written with a trailing space (`#album/debut `), and two notes — `Nein, T wie taub.md` and `Wer nicht will 3.md` — put a tag line directly under a line of prose with no blank line between. Hoisting tags out of the display therefore touches several places in a file, and putting them back byte-identically is the delicate part of the tag work — not the parsing.

**Frontmatter and inline hashtags agree in all 168 notes**, once the two `%` tags are excluded. The invariant is currently perfect. The app's job is to preserve it, which is a far smaller problem than reconciling drift — do not write reconciliation machinery for a conflict that does not exist.

Where the duplication came from, since it decides what the app may do about it: **the frontmatter is not original to the notes.** They were written in an editor that had no frontmatter at all, where a tag was only an inline `#hashtag` in the note text; the YAML block was added by the export on the way out, deriving `tags:` from the hashtags already in the body and leaving the body untouched. So every note has carried the tag twice since before this app existed, and neither copy is the app's to remove. It also explains the one asymmetry below: the export could not read `%` as part of a tag name, which is why 21 percent tags never reached a `tags:` list.

**The test fixture does not cover the tag hazard.** `Lyrics_Test` contains no `#` chord anywhere. In the whole archive there is exactly one sharp, in `Radio (Song Notes).md`: "Tarantino für zwei in F# Moll." — an `F#`, with no `F#m` and no `C#` despite what `CLAUDE.md` says. The rule survives it without a special case, `#` there being preceded by a letter rather than by whitespace, but the fixture cannot demonstrate that. Phase 1 covers it as a unit test and a sharp gets added to the fixture.

**Percent tags are three, not two, and the frontmatter is not their index.** `100%`, `50%` and — unrecorded anywhere until now — `#75%`, in `Sieger sehen anders aus.md`. Both spellings occur in bodies: `#100%` bare 19 times, the wrapped `#100%#` 11 times. **21 percent tags live in a body with no frontmatter entry**, and none the other way round, so `CLAUDE.md`'s "index them from frontmatter" would lose 21 tags the author can plainly see in their own text. Phase 6 resolved this by renaming them rather than by indexing around them — see the end of this file.

One consequence outlived the rename: no percent tag anywhere in the archive was embedded in a sentence — all 33 sat on a tag line — so hiding those lines hides no words. The other, that a line reading `#album/debut #100% #busch` had to be recognised as tags despite the middle word being unreadable, went away with the `%`.

**Three notes carry HTML comments, and one attachment folder does not exist.** 24 instances of `<!-- {"embed":"true", "preview":"true"} -->`, all beside a PDF link, all left by the export. They are invisible in any renderer and are stripped for display — never from the file. More importantly, the three byte-identical `Wer geht vor` duplicates link into `Wer geht vor/`, the un-migrated layout it came with, and **that folder is not in the archive**: 24 dead links. Only `Wer geht vor.md` itself has the proper `## Anhänge` list into `attachments/`. Phase 5 must render a dead link as a link, without pretending to have the file, and must never repair one.

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

**Hand-rolled Markdown, no library.** Every edge case in the archive is a place where a CommonMark-correct library is wrong by the user's intent: `---` after a text line is a setext H2 to the spec and a horizontal rule here; `#100%#` was the export's wrapping and had to survive untouched until the rename; `F#` must not be a tag. A library gets these right by the standard and wrong by the archive, and bending it costs more than the parser we need. The supported vocabulary is deliberately tiny — headings, bold, italic, links, images, rules, lists — because this is a lyrics app and not a document processor.

**Frontmatter is re-emitted, never re-serialised.** `Frontmatter` holds the original raw lines alongside a parsed view. A write replaces only the lines whose keys actually changed and passes everything else through byte-for-byte, including quoting, ordering and any key we do not understand. This is what makes definition-of-done item 8 achievable instead of aspirational. An app that parses the block into a map and dumps it back has already lost, and that is precisely how the previous candidates failed.

**SAF cannot do an atomic replace, and the plan says so.** `DocumentsContract.renameDocument` refuses an existing target, so "temp file plus rename" becomes write-temp, delete-original, rename — which has a real if tiny window. There is no way around this through SAF. The mitigation is that a crash leaves an intact temp file rather than a truncated note, and that startup recovers from one. This is a platform limit rather than a shortcut, and it should be documented in the code where it lives.

## Palette

**Taupe, at deliberately reduced contrast, modelled on another editor's dark theme.** Settled in phase 3 against measurement rather than taste, and documented in full in `Theme.kt`.

The author asked for that softer look on 2026-09-07. Sampling a screenshot of it gave its actual numbers — page `#2F3235`, titles 5.30:1, body 4.49:1 — and the palette matches them: page `#363031`, titles 5.35:1, body 4.50:1. Two moves make that look and doing only one fails: the page lifts off black *and* the type comes down off white. Lift the page alone and the screen washes out; dim the type alone and it turns to murk.

The hue was then chosen from five candidates rendered side by side at identical luminance, so the choice cost nothing in legibility — only the hue moved. Taupe, hue 350 at very low saturation, reads as paper rather than as a colour and sits furthest from the other three apps' palettes. The accent is **barely a colour** — hue 18 at 12% saturation, picked from four candidates rendered at identical luminance so the choice cost nothing in contrast. It reads as a warm grey rather than as a clay, but is warm enough that a filled control does not look like plain ink. The louder candidates all lost on the same ground: the screen is 90% the user's own words, and chroma the app spends is spent competing with them. The quieter candidate — no accent at all, the page's own taupe one step up — remains the purest reading of "elegantly neutral" and is where to go if this ever starts to feel like a colour.

It is deliberately dimmer than the title — 5.01:1 against 5.35:1 on dark. At reduced contrast the brightest thing on screen is where the eye lands first, and in a writing app that has to be the writing.

What this gives up is real: body type sits within a hair of WCAG's 4.5:1 floor for small text, where the first palette had it at 18:1. **If legibility ever bites, the lever is the page, not the type** — darkening it lifts every ratio at once and keeps the look. Light is reduced in the same character but held higher (title 6.53:1), because a light page in sunlight is unforgiving in a way a dark page indoors is not.

## Phases

Each phase ends somewhere verifiable. The app reads the entire archive correctly before it is allowed to write a single byte.

**0 — Skeleton.** Gradle, manifest, `Theme.kt`, SAF folder picker with a persisted tree grant lifted from TitleTrack's `RecordingStore`, and a list of bare filenames. Done when it installs on the Fairphone, opens `Lyrics_Test`, and still has the grant after a reboot.

**1 — `markdown/`, tests only, no UI.** Frontmatter round-trip on all 168 real notes. The tag rule with `F#`, `## Strophe`, `#100%#` and a multi-tag line as explicit cases. Block parsing where `---` at the top of a file is frontmatter and `---` anywhere else is a rule, with `- - -` identical. Excerpt extraction, including the 36 empty ones. Done when the tests encode every edge case above and pass.

**2 — `vault/`, read-only.** SAF listing, note reading, the in-memory index, change detection by content hash rather than mtime (a sync client rewrites mtimes), and NFC/NFD-tolerant name matching for the macOS-origin filenames. Done when items 1, 2, 3, 6, 7 and — the one that matters — 8 all verify against a copy of the real archive.

**3 — Library screen. Done.** Title, excerpt or placeholder, date and tags per row; the tag drawer; full-text search.

The drawer is **alphabetical with counts**, not frequency-ordered. Frequency was the obvious answer to `lyrics/snippet` covering 131 of 168 notes and is the wrong one: it puts the least useful filter in the most prominent slot. What makes alphabetical safe is that the whole tree fits on one screen — 14 top-level entries and 11 children — so nothing is buried and predictability is worth more than ranking. It is 300dp wide, not Material's 360dp, so the list stays visible behind it and it reads as a panel rather than a page.

Rows sort by **`updated`**, not `created`. The two differ on 48 of the 168 notes; `created` is what the archive is worth, `updated` is what you reach for.

Tags sit on **one line, right-aligned, with a `+N` chip** for what does not fit. Wrapping was tried and made rows of uneven height, which reads as broken. The count is decided by measuring, in a `SubcomposeLayout`: how many chips fit depends on how wide `+N` is and `N` depends on how many fit, so it walks the count down until the row fits rather than guessing at character widths.

**4 — Editor. Done.** Always editable, no chrome, format bar on selection only, and the Markdown invisible until the cursor reaches it.

The spike came out better than the plan feared. `OutputTransformation` owns the cursor mapping, so the dangerous arithmetic never had to be written; and `TextFieldBuffer.originalSelection` is readable inside the transformation, so the fade-in needs no rebuilding and no feedback loop. **The fallback to dimmed markers was not needed.** What stayed ours is *which* ranges to hide, which is `markdown/LiveText.kt` — pure Kotlin, tested at every marker position in all 168 notes, with one test asserting that what disappears is only marker characters and never a word the author typed.

Saving checks four things in order, each preventing one way of losing writing: refuse a note that did not round-trip; do nothing if the body is unchanged; re-read and compare by content hash before writing; then temp-file, verify, swap. A conflict keeps both — the user's version is written as a numbered sibling, matching how the archive already numbers `Wer geht vor 2.md`.

Verified on the device: opening several notes and editing one changed exactly that one file, and inside it exactly two lines — `updated`, and the text typed. `created` untouched, key order and quoting preserved, no temp files left behind.

**5 — Images and attachments. Done.** Relative-path resolution against the note's own folder, both the `attachments/` form and the flat sibling form, a `BitmapFactory` loader with a 4 MB LRU cache, and links opening by intent. No Coil — 38 images at 151×164 do not justify a dependency.

The apparent conflict between "one view, always editable" and "images render inline" dissolved on measurement: **every image in the archive sits on a line of its own**, in runs of three or four, and the most any line carries beside them is a bare `3x`. So the body is cut at image lines, text stays directly editable, and images are drawn in the gaps. 165 of 168 notes produce a single segment and are unchanged. `Segments.join(Segments.split(body))` is asserted byte-identical on every note in the archive.

Images are mounted on paper — the light scheme's own `surfaceContainerHighest`, fixed rather than theme-following. The chord diagrams are 89% transparent PNGs whose ink is `#111111`, drawn against a white page in the editor they came from; on this app's dark page they read at 1.46:1 and effectively vanish. Inverting was the alternative and is wrong, because the app cannot know line art from a photograph.

One deviation from `CLAUDE.md`, deliberately: attachments open from a strip at the foot of the note rather than by tapping the link text. The editor is always live, so a tap in it means "put the cursor here"; making a tap sometimes mean "leave the app and open a PDF" is a coin toss played while writing. The links are untouched — this adds an affordance, not a rewrite.

**6 — Tag chips, two-way sync. Done.** The most dangerous write path in the app, and the one that changed the archive rather than only reading it. "No `#` anywhere" is now true of the editor as well as the list and the drawer.

**Nothing is converted, and nothing moves.** Both representations already exist in every note and both stay. A tag line in a body is *hidden from the editor*, never deleted; the frontmatter list is left exactly as it is unless the user changes a chip. The app has no operation that reads one representation and rewrites the other — the only writes it can make are "add the tag just added" and "remove the tag just removed", to both places at once. That is the whole of the two-way sync, and the absence of a reconciliation path is the feature.

**The set the user edits is seeded from the frontmatter, never from the union.** `Note.tags` unions frontmatter and inline for the *index*; handing that union to `Frontmatter.withTags` would promote a body-only tag into the YAML the moment the user changed some unrelated chip — writing to lines nobody touched. The 21 body-only percent tags were what exposed it, and the rename has since removed them; the rule stays, because it is what guarantees the two representations keep agreeing rather than the measurement that says they currently do.

**Tag lines leave the editable text as segments, not as hidden characters.** A run of consecutive tag lines becomes a `Segment.Tags`, cut out the way an image line already is, so it is structurally outside every text buffer and a backspace at the edge cannot silently eat a tag. The segment absorbs the blank lines before it, and those after it as well when the run begins the body, so a note whose tags sit at the top does not open on an empty first line. Bytes only ever move between adjacent segments, so `join(split(x)) == x` still holds and is asserted over all 168 notes.

**Chips sit at the foot of the note and wrap.** Not the library row's one-line `+N`: hiding a tag behind `+2` in the very place it is edited would be perverse. Tapping the row opens a sheet — the archive's tags, alphabetical, checkable, plus a field for a new one, lowercased on entry. One deliberate confirm, one write path, no chip that deletes a tag on a mis-tap.

**A tag a body carries and the frontmatter does not is chipped and marked not-removable.** It is displayed because the author can see it in their own text, and it is not removable because the app may not promote it into a `tags:` list on their behalf. No note in the archive is in this state any more — the percent rename was what emptied the category — so this now exists for the note edited elsewhere and left disagreeing with itself.

Writing goes through `Vault.save` unchanged in shape — refuse a note that did not round-trip, no-op if nothing changed, re-read and compare by content hash, then temp-file, verify, swap — with the tag set carried alongside the body so `updated` is stamped once for both.

What it cost, honestly: three of the phase's original decisions were elaborate ways of *displaying* a problem the data did not have to have, and the fix was a one-time rename of three tags — see below. The remaining machinery is small, and all of it is about not writing: a tag run leaves the editable text as a segment rather than being hidden in place, the editable set is the frontmatter's and never the union, and `Note.withTags` returns null when nothing moved so an open-and-close still writes nothing.

Two things phase 6 found and did not fix. Returning to the foreground refreshes the index but **does not rebuild an open note's document**, so a file that changes underneath a note you are looking at is not picked up until you leave the note and come back — defensible while somebody is typing, but currently silent, and phase 7's business. And `NoteDocument` has no unit tests: its two pieces of logic, `chips()` and `toggleTag`, are three lines each and everything underneath them is covered, but they are the only tag logic in the app reached by no test.

**7 — Sync, conflicts, polish.** Foreground re-read, pull-to-refresh, keep-both on conflict with the difference surfaced, never auto-delete on a vanished file. Then settings, about, German strings, fastlane metadata and the F-Droid layout.

Three things phase 6 handed it, all found by running against the real archive rather than the fixture:

**The sync client is Syncthing, not Nextcloud.** Established on 2026-09-07 from the `.stfolder` marker inside `/sdcard/Recordings/Lyrics` itself, which makes that note folder a sync root and its parent an ordinary directory. It changes what a conflict looks like: Syncthing writes `<name>.sync-conflict-<YYYYMMDD>-<HHMMSS>-<DEVICEID>.md` **into the same folder**, so a conflict arrives as an extra `.md` carrying the same `title` as the note it conflicts with. Since the archive already holds four notes titled `Wer geht vor?`, nothing distinguishes a sync conflict from a legitimate duplicate except the filename. Recognise the pattern, surface it, and never delete or auto-resolve one. The app's own keep-both writes a numbered sibling instead, and the two shapes should stay distinct.

**An open note does not notice its file changing.** Returning to the foreground refreshes the index, but `NoteDocument` is remembered per note URI, so a note already open keeps the body it was opened with. It is right not to yank text from under someone mid-sentence; it is wrong to say nothing. This bit during phase 6 testing and looked like a parser bug for several minutes.

**Item 8 now has a baseline to check against.** Verified on 2026-09-07 against a copy of the migrated archive at `/sdcard/Recordings/Lyrics_Check`: reading every note through the app left all 168 byte-identical and all 26 attachments untouched, with no temp files left behind. That is the first time the check has run on the real data rather than on the 12-note fixture — so it covered the four `Wer geht vor` duplicates, the 24 dead attachment links, the 8-image chord sheets and the notes carrying a bare `---`.

## The risk that was named, and how it resolved

Hiding `**` makes the displayed text shorter than the stored text, and a wrong offset mapping is a crash on somebody's song. This was spiked before any editor UI was designed, on the understanding that a bad result meant falling back to dimmed markers.

It resolved cleanly on both counts. `OutputTransformation` hands over a `TextFieldBuffer` and takes responsibility for the cursor mapping itself, so the offset arithmetic that would have been hand-written is Compose's. And the fade-in did not need the transformation rebuilt on every cursor move — `TextFieldBuffer.originalSelection` is readable right inside `transformOutput`, in the original coordinate space, which is exactly what the decision logic wants. One instance, no rebuilding, no feedback loop. `TextFieldBuffer.addStyle` being public means hiding and styling happen in one pass rather than needing two mechanisms.

**The fallback was not needed.** What remains ours is which ranges to hide, and that lives in `markdown/LiveText.kt` with no Android in it.

## The risk that is left

SAF cannot do an atomic replace, so the swap in `Vault.save` is delete-then-rename with a window of milliseconds. `recoverTemp` restores an interrupted write on the next listing. See the note on `Vault.save` — writing in place would move the truncation inside the user's own file, which no recovery undoes.

## Definition of done

`CLAUDE.md`'s eight checks, unchanged, against a copy of the real archive. Item 8 is the one that matters and it is checked at the end of every phase from 2 onward, not only at the end.

## The percent tags, and how the question actually resolved

**The problem.** `100%`, `50%` and `75%` contain a character no hashtag can hold. They could only live in the frontmatter, and that is also why the export lost 21 of them: reading a body, it could not see `#100%` as a tag, so it wrote no `tags:` entry. The archive was left with 33 notes carrying a percent tag in their text and only 12 declaring one.

Phase 6 first tried to work around that. Index from the frontmatter alone, as `CLAUDE.md` said; chip the body-only ones without indexing them so they were at least visible; mark them not removable, because removing one would have meant editing prose. Three special cases, on three different screens, for three tags.

**Resolved on 2026-09-07 by renaming them instead: `100`, `50`, `75`.** The author had kept the `%` on the earlier understanding that the app should never rename a tag — which remains right as a rule about what the *app* does unbidden, and was never a reason to refuse a rename the author asks for. `tools/rename-percent-tags.py` does it once over the folder: `#100%` and the wrapped `#100%#` become `#100` in bodies, the `tags:` entries lose their `%`, and the 21 notes the export skipped get the entry they should always have had. 33 notes changed, no prose line touched, no `updated` stamp moved.

What that bought is not cosmetic. Every special case above is gone — no frontmatter-only rule, no chipped-not-indexed, no unremovable chip — and the tag rule grew by one word: a hashtag may open with a letter **or a digit**. That widening is safe because those 33 hashtags are the only `#digit` sequences in all 168 notes, which is asserted rather than assumed. The archive gained a tag it never had (`75` was invisible to the app before, having no frontmatter entry anywhere), `100` went from 11 notes to 30, and frontmatter and body now agree on every tag on every note.

The lesson worth keeping: two of the three phase 6 design decisions were elaborate ways of displaying a problem the data did not have to have. The fix was in the archive, not in the app.
