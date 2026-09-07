// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parts of attachment resolution that are arithmetic on strings rather than trips to a provider. */
class AttachmentsPathTest {

    @Test
    fun `the export's encoded paths decode to real names`() {
        assertEquals(
            "Wer geht vor/Pasted Graphic 12.pdf",
            Attachments.decode("Wer%20geht%20vor/Pasted%20Graphic%2012.pdf"),
        )
        assertEquals(
            "attachments/casablanca-chords-01.png",
            Attachments.decode("attachments/casablanca-chords-01.png"),
        )
    }

    @Test
    fun `an encoded slash stays part of a name and does not invent a folder`() {
        // Decoding the whole path at once would turn %2F into a separator and send the walk looking
        // for a folder that was never meant. Each segment is decoded on its own.
        assertEquals("odd/name.png", Attachments.decode("odd%2Fname.png"))
    }

    @Test
    fun `umlauts survive decoding`() {
        assertEquals("Müde.png", Attachments.decode("M%C3%BCde.png"))
    }

    @Test
    fun `a URL is not something to look for in the folder`() {
        assertTrue(Attachments.isAbsoluteUrl("http://ekimas.de"))
        assertTrue(Attachments.isAbsoluteUrl("https://example.com/a.png"))
        assertTrue(Attachments.isAbsoluteUrl("mailto:someone@example.com"))
        assertFalse(Attachments.isAbsoluteUrl("attachments/a.png"))
        assertFalse(Attachments.isAbsoluteUrl("a.png"))
    }
}
