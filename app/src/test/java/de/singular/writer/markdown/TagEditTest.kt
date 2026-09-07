// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Writing a tag into somebody's song, and taking one out again.
 *
 * The tests that matter here are the ones about what *did not* change. A tag edit touches a file the
 * user has one copy of, and the failure mode is not a crash — it is a line of a lyric quietly
 * reflowed, requoted or lost, noticed weeks later when there is nothing to compare against.
 */
class TagEditTest {

    @Test
    fun `no change writes nothing`() {
        val body = "\n#lyrics/snippet\n\nDu erreichst mich nicht\n"
        assertEquals(body, TagEdit.apply(body, emptyList(), emptyList()))
    }

    @Test
    fun `a tag goes onto the note's last tag line`() {
        val body = "\n#lyrics/snippet\n\nDu erreichst mich nicht\n"
        assertEquals(
            "\n#lyrics/snippet #busch\n\nDu erreichst mich nicht\n",
            TagEdit.apply(body, listOf("busch"), emptyList()),
        )
    }

    @Test
    fun `the last run is the one written to, not the first`() {
        // Casablanca (chords).md has #chords #radio above its diagrams and #lyrics/snippet below.
        val body = "\n#chords #radio\n\nAkkorde\n\n#lyrics/snippet\n"
        assertEquals(
            "\n#chords #radio\n\nAkkorde\n\n#lyrics/snippet #busch\n",
            TagEdit.apply(body, listOf("busch"), emptyList()),
        )
    }

    @Test
    fun `a trailing space on a tag line survives the write`() {
        // Several lines in the archive are written `#album/debut `, and the space is the file's.
        assertEquals(
            "\n#album/debut #busch \n",
            TagEdit.apply("\n#album/debut \n", listOf("busch"), emptyList()),
        )
    }

    @Test
    fun `removing a tag takes its space with it and leaves the rest of the line alone`() {
        assertEquals(
            "\n#album/debut #busch \n",
            TagEdit.apply("\n#album/debut #radio #busch \n", emptyList(), listOf("radio")),
        )
    }

    @Test
    fun `a tag is removed from every run that carries it`() {
        val body = "\n#chords #radio\n\nAkkorde\n\n#radio #lyrics/snippet\n"
        assertEquals(
            "\n#chords\n\nAkkorde\n\n#lyrics/snippet\n",
            TagEdit.apply(body, emptyList(), listOf("radio")),
        )
    }

    @Test
    fun `emptying a run at the foot takes the blank line above it`() {
        val body = "Du erreichst mich nicht\n\n#lyrics/snippet\n"
        assertEquals(
            "Du erreichst mich nicht\n",
            TagEdit.apply(body, emptyList(), listOf("lyrics/snippet")),
        )
    }

    @Test
    fun `emptying a run at the head keeps the blank line under the frontmatter`() {
        // Every note in the archive opens with one. Losing a tag is not a reason to be the exception.
        val body = "\n#lyrics/snippet\n\nDu erreichst mich nicht\n"
        assertEquals(
            "\nDu erreichst mich nicht\n",
            TagEdit.apply(body, emptyList(), listOf("lyrics/snippet")),
        )
    }

    @Test
    fun `a note with no tag line gets one at the foot`() {
        // Three notes in the archive carry `tags: []` and no hashtag anywhere.
        assertEquals(
            "\nDu erreichst mich nicht\n\n#busch\n",
            TagEdit.apply("\nDu erreichst mich nicht\n", listOf("busch"), emptyList()),
        )
    }

    @Test
    fun `two tags added at once share the line`() {
        assertEquals(
            "\n#lyrics/snippet #busch #radio\n",
            TagEdit.apply("\n#lyrics/snippet\n", listOf("busch", "radio"), emptyList()),
        )
    }

    @Test
    fun `a run of three tag lines is edited in place`() {
        // Helen weiss das auch.md ends with exactly this, trailing spaces included.
        val body = "und weiter\n\n#album/debut \n#100\n#album/entsetzlich #busch \n"
        assertEquals(
            "und weiter\n\n#album/debut \n#100\n#album/entsetzlich \n",
            TagEdit.apply(body, emptyList(), listOf("busch")),
        )
    }

    @Test
    fun `a numeric tag is written like any other`() {
        // `100`, `50` and `75` were `100%`, `50%` and `75%` until the rename on 2026-09-07. The
        // whole point of dropping the `%` is that there is no longer a case here.
        assertEquals(
            "\n#lyrics/snippet #100\n",
            TagEdit.apply("\n#lyrics/snippet\n", listOf("100"), emptyList()),
        )
        assertEquals(
            "\n#lyrics/snippet\n",
            TagEdit.apply("\n#lyrics/snippet #100\n", emptyList(), listOf("100")),
        )
    }

    @Test
    fun `a name that cannot be a hashtag never reaches the body`() {
        // The frontmatter can hold a name the body cannot. Such a tag is written to the `tags:` list
        // alone rather than mangled into something a body could carry.
        val body = "\n#lyrics/snippet\n"
        assertEquals(body, TagEdit.apply(body, listOf("100%"), emptyList()))
        assertEquals(body, TagEdit.apply(body, listOf("zwei worte"), emptyList()))
    }

    @Test
    fun `a hashtag inside a sentence is part of the sentence`() {
        // Not a tag line, so not this file's business — the words stay exactly as they are.
        val body = "das ist #busch und mehr\n\n#lyrics/snippet\n"
        assertEquals(
            "das ist #busch und mehr\n\n#lyrics/snippet\n",
            TagEdit.apply(body, emptyList(), listOf("busch")),
        )
    }

    @Test
    fun `a sharp in a lyric is not a tag and is never touched`() {
        // The only sharp in 168 notes, in Radio (Song Notes).md. The tag line goes; the lyric comes
        // back character for character.
        val body = "Tarantino für zwei in F# Moll\n\n#lyrics/snippet\n"
        assertEquals(
            "Tarantino für zwei in F# Moll\n",
            TagEdit.apply(body, emptyList(), listOf("lyrics/snippet")),
        )
    }

    @Test
    fun `a heading is never mistaken for a tag line`() {
        val body = "## Strophe\n\n#lyrics/snippet\n"
        assertEquals("## Strophe\n", TagEdit.apply(body, emptyList(), listOf("lyrics/snippet")))
    }

    @Test
    fun `a parent tag is not struck by a child of the same name`() {
        // `album` and `album/debut` are different tags and share a prefix.
        assertEquals(
            "\n#album/debut\n",
            TagEdit.apply("\n#album #album/debut\n", emptyList(), listOf("album")),
        )
    }

    @Test
    fun `swapping a note's only tag leaves the tag line where the author put it`() {
        // Seen on the phone: replacing `lyrics/snippet` with `75` on a note whose tags sat at the
        // head moved them to the foot. The removal emptied the run and dropped it, and the addition
        // then started a fresh one at the bottom. Two correct operations, one line moved in a file
        // nobody asked to have rearranged.
        val body = "\n#lyrics/snippet\n\nDu erreichst mich nicht\n"
        assertEquals(
            "\n#75\n\nDu erreichst mich nicht\n",
            TagEdit.apply(body, listOf("75"), listOf("lyrics/snippet")),
        )
    }

    @Test
    fun `swapping keeps a foot tag line at the foot too`() {
        val body = "Du erreichst mich nicht\n\n#lyrics/snippet\n"
        assertEquals(
            "Du erreichst mich nicht\n\n#75\n",
            TagEdit.apply(body, listOf("75"), listOf("lyrics/snippet")),
        )
    }

    @Test
    fun `adding then removing a tag gives the body back unchanged`() {
        for (body in listOf(
            "\n#lyrics/snippet\n\nDu erreichst mich nicht\n",
            "Du erreichst mich nicht\n\n#lyrics/snippet\n",
            "\n#chords #radio\n\nAkkorde\n\n#lyrics/snippet\n",
            "\n#album/debut \n#100\n#busch \n",
        )) {
            val added = TagEdit.apply(body, listOf("busch2"), emptyList())
            assertEquals(body, TagEdit.apply(added, emptyList(), listOf("busch2")))
        }
    }
}
