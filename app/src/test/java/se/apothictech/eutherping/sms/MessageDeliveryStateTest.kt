package se.apothictech.eutherping.sms

import android.provider.Telephony
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageDeliveryStateTest {
    @Test
    fun smsStatesFollowProviderBoxAndDeliveryStatus() {
        assertEquals(
            MessageDeliveryState.SENDING,
            sms(Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.STATUS_PENDING).deliveryState(),
        )
        assertEquals(
            MessageDeliveryState.SENT,
            sms(Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.STATUS_NONE).deliveryState(),
        )
        assertEquals(
            MessageDeliveryState.DELIVERED,
            sms(Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.STATUS_COMPLETE).deliveryState(),
        )
        assertEquals(
            MessageDeliveryState.FAILED,
            sms(Telephony.Sms.MESSAGE_TYPE_FAILED, Telephony.Sms.STATUS_FAILED).deliveryState(),
        )
    }

    @Test
    fun incomingMessagesNeverShowOutgoingState() {
        assertNull(
            sms(Telephony.Sms.MESSAGE_TYPE_INBOX, Telephony.Sms.STATUS_NONE, incoming = true)
                .deliveryState(),
        )
    }

    @Test
    fun mmsStatesFollowProviderMessageBox() {
        assertEquals(MessageDeliveryState.SENDING, mms(Telephony.Mms.MESSAGE_BOX_OUTBOX).deliveryState())
        assertEquals(MessageDeliveryState.SENT, mms(Telephony.Mms.MESSAGE_BOX_SENT).deliveryState())
        assertEquals(MessageDeliveryState.FAILED, mms(Telephony.Mms.MESSAGE_BOX_FAILED).deliveryState())
    }

    private fun sms(box: Int, status: Int, incoming: Boolean = false) = SmsEntry(
        id = 1,
        body = "test",
        timestamp = 1,
        incoming = incoming,
        read = true,
        status = status,
        box = box,
    )

    private fun mms(box: Int) = SmsEntry(
        id = -2,
        body = "mms",
        timestamp = 1,
        incoming = false,
        read = true,
        status = box,
        box = box,
        isMms = true,
    )
}
