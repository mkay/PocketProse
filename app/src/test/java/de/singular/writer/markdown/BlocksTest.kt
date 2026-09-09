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
    fun `a line of only hashtags is prose like any other line`() {
        // It was its own hidden block kind until 2026-09-09. Tags come from the frontmatter now, so
        // there is nothing special about a line that happens to start with a `#` — and 165 notes in
        // the archive carry one, which is why this is asserted rather than assumed.
        assertEquals(listOf(Block.Paragraph("#chords #radio")), Blocks.parse("#chords #radio"))
    }

    @Test
    fun `a line mixing a hashtag with words stays visible prose`() {
        assertEquals(
            listOf(Block.Paragraph("sing it #loud tonight")),
            Blocks.parse("sing it #loud tonight"),
        )
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
    fun `headings need their space, so a hashtag is not a heading`() {
        assertEquals(listOf(Block.Heading(2, "Strophe")), Blocks.parse("## Strophe"))
        assertEquals(listOf(Block.Paragraph("#lyrics")), Blocks.parse("#lyrics"))
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
