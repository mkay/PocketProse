// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class BlocksTest {

    @Test
    fun `a rule after a line of text is a rule, never a setext heading`() {
        // Sieger sehen anders aus.md is written exactly this way. CommonMark would make the first
        // line an H2; the author means a rule, and this app is not a spec implementation.
        val blocks = Blocks.parse("a line of the song\n---\nthe next line\n")
        assertEquals(
            listOf(Block.Paragraph("a line of the song"), Block.Rule, Block.Paragraph("the next line")),
            blocks,
        )
    }

    @Test
    fun `the spaced spelling of a rule is the same rule`() {
        // 20 notes use `- - -`. Without the spacing clause these parse as three empty bullets.
        assertEquals(listOf(Block.Rule), Blocks.parse("- - -"))
        assertEquals(listOf(Block.Rule), Blocks.parse("---"))
        assertEquals(listOf(Block.Rule), Blocks.parse("***"))
        assertEquals(listOf(Block.Rule), Blocks.parse("- - - -"))
    }

    @Test
    fun `a single dash item is still a bullet`() {
        assertEquals(listOf(Block.Bullets(listOf("one", "two"))), Blocks.parse("- one\n- two"))
    }

    @Test
    fun `a line of only hashtags is a tag line and never shown as text`() {
        assertEquals(listOf(Block.TagLine(listOf("chords", "radio"))), Blocks.parse("#chords #radio"))
    }

    @Test
    fun `a line mixing a hashtag with words stays visible prose`() {
        assertEquals(
            listOf(Block.Paragraph("sing it #loud tonight")),
            Blocks.parse("sing it #loud tonight"),
        )
    }

    @Test
    fun `a numeric tag counts towards a tag line`() {
        // Müde.md reads exactly this. The middle tag was `#100%` until the rename on 2026-09-07, and
        // being unreadable it made the whole line prose — so the library row for Müde led with the
        // note's own tags instead of with the song. Now it is simply one of the three.
        assertEquals(
            listOf(Block.TagLine(listOf("album/debut", "100", "busch"))),
            Blocks.parse("#album/debut #100 #busch"),
        )
        assertEquals(listOf(Block.TagLine(listOf("75"))), Blocks.parse("#75"))
    }

    @Test
    fun `a numeric tag inside a sentence leaves the sentence visible`() {
        // No note in the archive does this, but hiding a line of prose would be unforgivable, so
        // the "entirely tags" requirement is pinned rather than assumed.
        assertEquals(
            listOf(Block.Paragraph("das ist zu #100 mein Ernst")),
            Blocks.parse("das ist zu #100 mein Ernst"),
        )
    }

    @Test
    fun `an HTML comment does not stop a line being read as prose`() {
        val line = "[Graphic.pdf](Wer%20geht%20vor/Graphic.pdf)<!-- {\"embed\":\"true\"} -->x3"
        assertEquals(listOf(Block.Paragraph(line)), Blocks.parse(line))
    }

    @Test
    fun `headings need their space, so a tag is not a heading`() {
        assertEquals(listOf(Block.Heading(2, "Strophe")), Blocks.parse("## Strophe"))
        assertEquals(listOf(Block.TagLine(listOf("lyrics"))), Blocks.parse("#lyrics"))
    }

    @Test
    fun `line breaks inside a verse are kept`() {
        val blocks = Blocks.parse("Du erreichst mich nicht\nund das ist gut so\n\nzweite Strophe")
        assertEquals(
            listOf(
                Block.Paragraph("Du erreichst mich nicht\nund das ist gut so"),
                Block.Paragraph("zweite Strophe"),
            ),
            blocks,
        )
    }
}
