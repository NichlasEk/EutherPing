package se.apothictech.eutherping.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        CarrierMmsRepository.receiveNotification(context, intent).onFailure {
            Log.e("EutherPingMms", "Could not start carrier MMS download", it)
        }
    }
}
