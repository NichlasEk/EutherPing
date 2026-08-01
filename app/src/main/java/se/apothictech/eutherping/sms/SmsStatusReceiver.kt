package se.apothictech.eutherping.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rawUri = intent.getStringExtra(SmsRepository.EXTRA_MESSAGE_URI) ?: return
        when (intent.action) {
            SmsRepository.ACTION_SMS_SENT -> {
                SmsRepository.updateSentState(context, rawUri.toUri(), resultCode)
            }
            SmsRepository.ACTION_SMS_DELIVERED -> Unit
        }
    }
}
