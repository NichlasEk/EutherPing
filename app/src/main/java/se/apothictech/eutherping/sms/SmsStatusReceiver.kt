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
                SmsRepository.updateSentPartState(
                    context,
                    rawUri.toUri(),
                    resultCode,
                    intent.getIntExtra(SmsRepository.EXTRA_PART_INDEX, 0),
                    intent.getIntExtra(SmsRepository.EXTRA_PART_COUNT, 1),
                )
            }
            SmsRepository.ACTION_SMS_DELIVERED -> {
                SmsRepository.updateDeliveredPartState(
                    context,
                    rawUri.toUri(),
                    resultCode,
                    intent.getIntExtra(SmsRepository.EXTRA_PART_INDEX, 0),
                    intent.getIntExtra(SmsRepository.EXTRA_PART_COUNT, 1),
                )
            }
        }
    }
}
