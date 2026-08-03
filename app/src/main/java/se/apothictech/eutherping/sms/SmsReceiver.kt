package se.apothictech.eutherping.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import se.apothictech.eutherping.secure.SecureRepository

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
        SecureRepository.handleIncomingControl(context, address, body)
        SmsRepository.persistIncoming(context, address, body, timestamp)
        IncomingMessageNotifier.show(
            context = context,
            address = address,
            body = SecureRepository.notificationText(context, address, body),
            secureLane = SecureRepository.isSecureBody(body),
        )
    }

}
