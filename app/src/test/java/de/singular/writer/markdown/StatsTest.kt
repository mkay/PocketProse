// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsTest {

    @Test
    fun `an empty note counts nothing`() {
        assertEquals(Stats(0, 0, 0), Stats.of(""))
    }

    @Test
    fun `whitespace alone is not a line`() {
        assertEquals(Stats(0, 3, 0), Stats.of("\n \n"))
    }

    @Test
    fun `blank lines between verses are spacing, not content`() {
        val body = "Du erreichst mich nicht\n\nUnd das obwohl es\nso wichtig ist\n"
        assertEquals(3, Stats.of(body).lines)
    }

    @Test
    fun `words are runs of non-whitespace, however they are spelled`() {
        assertEquals(3, Stats.of("**bold** F# und").words)
    }

    @Test
    fun `characters include the spaces and the newlines`() {
        assertEquals(4, Stats.of("a b\n").characters)
    }

    @Test
    fun `a leftover hashtag line is counted, because it is text`() {
        // 165 notes in the archive open on one of these. The app renders it as text everywhere
        // else; a count that skipped it would be the only place in the app still hiding a line.
        val body = "\n#lyrics/snippet\n\nDu erreichst mich nicht\n"
        assertEquals(Stats(5, 42, 2), Stats.of(body))
    }

    @Test
    fun `a trailing newline does not invent a last line`() {
        // The characters differ by exactly that newline — it is in the file, so it is counted.
        assertEquals(2, Stats.of("eins\nzwei").lines)
        assertEquals(2, Stats.of("eins\nzwei\n").lines)
        assertEquals(10, Stats.of("eins\nzwei\n").characters)
    }
}
