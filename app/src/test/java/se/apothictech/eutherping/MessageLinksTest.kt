package se.apothictech.eutherping

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageLinksTest {
    @Test
    fun `finds secure and www links without sentence punctuation`() {
        val text = "See https://example.com/a?b=1, or www.apothictech.se."

        assertEquals(
            listOf(
                MessageUrlMatch(4, 29, "https://example.com/a?b=1"),
                MessageUrlMatch(34, 52, "https://www.apothictech.se"),
            ),
            findMessageUrls(text),
        )
    }

    @Test
    fun `ignores text without a browser link`() {
        assertEquals(emptyList<MessageUrlMatch>(), findMessageUrls("Call me at 070-123 45 67"))
    }
}
