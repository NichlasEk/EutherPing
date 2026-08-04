package se.apothictech.eutherping

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationControlsRepositoryTest {
    @Test
    fun pinArchiveAndNotificationPrivacyRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val address = "+46709990001"

        ConversationControlsRepository.setPinned(context, address, secure = false, pinned = false)
        ConversationControlsRepository.setArchived(context, address, secure = false, archived = false)
        assertFalse(ConversationControlsRepository.state(context, address, false).pinned)
        assertFalse(ConversationControlsRepository.state(context, address, false).archived)

        ConversationControlsRepository.setPinned(context, address, secure = false, pinned = true)
        ConversationControlsRepository.setArchived(context, address, secure = false, archived = true)
        assertTrue(ConversationControlsRepository.state(context, address, false).pinned)
        assertTrue(ConversationControlsRepository.state(context, address, false).archived)
        assertFalse("Vessel metadata must stay separate", ConversationControlsRepository.state(context, address, true).pinned)

        NotificationPrivacy.entries.forEach { privacy ->
            ConversationControlsRepository.setNotificationPrivacy(context, privacy)
            assertTrue(ConversationControlsRepository.notificationPrivacy(context) == privacy)
        }

        ConversationControlsRepository.setPinned(context, address, secure = false, pinned = false)
        ConversationControlsRepository.setArchived(context, address, secure = false, archived = false)
        ConversationControlsRepository.setNotificationPrivacy(context, NotificationPrivacy.SENDER_AND_PREVIEW)
    }

    @Test
    fun blockAndUnblockUseAndroidSystemListWhenSmsRoleAllowsIt() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd role add-role-holder android.app.role.SMS ${context.packageName}")
            .close()
        Thread.sleep(500)
        if (!ConversationControlsRepository.canBlockNumbers(context)) return
        val address = "+46709990002"
        try {
            ConversationControlsRepository.setBlocked(context, address, true).getOrThrow()
            assertTrue(
                ConversationControlsRepository.isBlocked(
                    ConversationControlsRepository.blockedNumbers(context),
                    address,
                ),
            )
        } finally {
            ConversationControlsRepository.setBlocked(context, address, false).getOrThrow()
        }
        assertFalse(
            ConversationControlsRepository.isBlocked(
                ConversationControlsRepository.blockedNumbers(context),
                address,
            ),
        )
    }
}
