// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScratchTest {

    @Test
    fun `a plus glued to the text is scratch, a plus with a space is a bullet, a plus inside is text`() {
        assertTrue(Scratch.isScratch("+Marie, Marie - vierundzwanzig, nie"))
        assertTrue(Scratch.isScratch("+Refrain lauter?"))
        assertFalse(Scratch.isScratch("+ Marie"))
        assertFalse(Scratch.isScratch("+"))
        assertFalse(Scratch.isScratch("a + b"))
        assertFalse(Scratch.isScratch(" +Marie"))
    }

    @Test
    fun `the draft closes up over its scratch lines`() {
        val text = "Marie, Marie\n+Marie, nie\n+lauter?\nEin Herz\n\n+Strophe 2?\nOh Marie\n"
        assertEquals("Marie, Marie\nEin Herz\n\nOh Marie\n", Scratch.strip(text))
    }

    @Test
    fun `a scratch line the note ends on goes with the newline before it`() {
        assertEquals("Marie", Scratch.strip("Marie\n+nie"))
        assertEquals("Marie\n", Scratch.strip("Marie\n+nie\n"))
        assertEquals("", Scratch.strip("+nie"))
    }

    @Test
    fun `a note without scratch lines is handed back as it is`() {
        val text = "Marie\n+ ein Punkt\na + b\n"
        assertEquals(text, Scratch.strip(text))
    }

    @Test
    fun `the live scanner marks the whole line and hides none of it`() {
        val text = "Marie\n+nie\nHerz"
        val scratch = Live.scan(text).filter { it.mark == Mark.SCRATCH }
        assertEquals(listOf(6 until 10), scratch.map { it.content })
        for (cursor in -1 until text.length) {
            val live = Live.of(text, if (cursor < 0) IntRange(-5, -5) else cursor..cursor)
            assertTrue(live.hide.isEmpty())
            assertEquals(listOf(6 to 10), live.styles.filter { it.mark == Mark.SCRATCH }.map { it.start to it.end })
        }
    }

    @Test
    fun `emphasis inside a scratch line still folds and the line stays dimmed`() {
        val live = Live.of("+nie **fett**", IntRange(-5, -5))
        assertEquals(listOf(0 to 9), live.styles.filter { it.mark == Mark.SCRATCH }.map { it.start to it.end })
        assertEquals(listOf(5 to 9), live.styles.filter { it.mark == Mark.BOLD && !it.isMarker }.map { it.start to it.end })
    }
}
