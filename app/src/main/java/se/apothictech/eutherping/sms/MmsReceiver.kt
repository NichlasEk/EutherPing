package se.apothictech.eutherping.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Android requires the default SMS candidate to own the WAP-push entry point.
 * Carrier MMS PDU download and persistence are deliberately deferred until the
 * SMS path is physically verified across supported carriers.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
    }
}
