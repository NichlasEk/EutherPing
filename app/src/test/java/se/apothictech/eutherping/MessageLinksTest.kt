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
    fun `finds short links without a scheme and opens them securely`() {
        val text = "Dressman: bit.ly/4abcDEF. More at dressmann.com/se/"

        assertEquals(
            listOf(
                MessageUrlMatch(10, 24, "https://bit.ly/4abcDEF"),
                MessageUrlMatch(34, 51, "https://dressmann.com/se/"),
            ),
            findMessageUrls(text),
        )
    }

    @Test
    fun `does not turn email addresses into browser links`() {
        assertEquals(
            emptyList<MessageUrlMatch>(),
            findMessageUrls("Write to hello@example.com"),
        )
    }

    @Test
    fun `ignores text without a browser link`() {
        assertEquals(emptyList<MessageUrlMatch>(), findMessageUrls("Call me at 070-123 45 67"))
    }
}
