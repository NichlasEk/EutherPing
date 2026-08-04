package se.apothictech.eutherping.sms

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarrierSubscriptionRepositoryTest {
    @Test
    fun conversationKeyIsOrderIndependentAndSeparatesGroups() {
        val first = CarrierSubscriptionRepository.conversationKey(
            null,
            listOf("+46700000001", "+46700000002"),
        )
        val reordered = CarrierSubscriptionRepository.conversationKey(
            null,
            listOf("+46700000002", "+46700000001"),
        )
        val different = CarrierSubscriptionRepository.conversationKey(
            null,
            listOf("+46700000001", "+46700000003"),
        )

        assertEquals(first, reordered)
        assertNotEquals(first, different)
        assertEquals("thread:44", CarrierSubscriptionRepository.conversationKey(44L, listOf("ignored")))
    }
}
