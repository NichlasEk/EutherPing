package se.apothictech.eutherping.sms

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomingMessageNotifierTest {
    @Test
    fun incomingChannelUsesVersionedEutherPingVibration() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        IncomingMessageNotifier.ensureChannel(context)

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = requireNotNull(manager.getNotificationChannel(IncomingMessageNotifier.CHANNEL_ID))
        assertEquals("incoming_messages_v2", channel.id)
        assertTrue(channel.shouldVibrate())
        assertArrayEquals(IncomingMessageNotifier.VIBRATION_PATTERN, channel.vibrationPattern)
    }
}
