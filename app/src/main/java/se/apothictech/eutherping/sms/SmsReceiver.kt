package se.apothictech.eutherping.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import se.apothictech.eutherping.secure.SecureRepository
import se.apothictech.eutherping.secure.SecureFrameAcceptance

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isEmpty()) return
        val address = parts.firstNotNullOfOrNull { it.displayOriginatingAddress }
            ?: parts.firstNotNullOfOrNull { it.originatingAddress }
            ?: "Unknown sender"
        val body = parts.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val timestamp = parts.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
        when (SecureRepository.acceptIncomingControl(context, address, body)) {
            SecureFrameAcceptance.DUPLICATE,
            SecureFrameAcceptance.STALE,
            SecureFrameAcceptance.INVALID,
            -> {
                Log.w("EutherPingSecure", "Rejected duplicate, stale, or invalid Secure pairing control")
                return
            }
            SecureFrameAcceptance.ACCEPTED -> Unit
            SecureFrameAcceptance.NOT_AUTHENTICATED_FRAME -> Unit
        }
        when (SecureRepository.acceptIncomingAuthenticatedFrame(context, address, body)) {
            SecureFrameAcceptance.DUPLICATE,
            SecureFrameAcceptance.STALE,
            SecureFrameAcceptance.INVALID,
            -> {
                Log.w("EutherPingSecure", "Rejected duplicate, stale, or invalid Secure Ping frame")
                return
            }
            SecureFrameAcceptance.ACCEPTED,
            SecureFrameAcceptance.NOT_AUTHENTICATED_FRAME,
            -> Unit
        }
        val messageUri = SmsRepository.persistIncoming(context, address, body, timestamp)
        IncomingMessageNotifier.show(
            context = context,
            address = address,
            body = SecureRepository.notificationText(context, address, body),
            secureLane = SecureRepository.isSecureBody(body),
            threadId = messageUri?.let { SmsRepository.threadIdForMessage(context, it) },
        )
    }

}
