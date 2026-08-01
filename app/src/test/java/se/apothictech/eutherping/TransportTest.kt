package se.apothictech.eutherping

import org.junit.Assert.assertEquals
import org.junit.Test

class TransportTest {
    @Test
    fun `secure transport never looks like sms`() {
        assertEquals("SECURE PING", Transport.SECURE.label)
        assertEquals("CELL // SMS", Transport.SMS.label)
    }
}
