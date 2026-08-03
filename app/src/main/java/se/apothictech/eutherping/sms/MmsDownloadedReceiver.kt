package se.apothictech.eutherping.sms

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.klinker.android.send_message.MmsReceivedReceiver

class MmsDownloadedReceiver : MmsReceivedReceiver() {
    override fun onMessageReceived(context: Context, messageUri: Uri) {
        Log.i("EutherPingMms", "Carrier MMS downloaded and stored at $messageUri")
        context.sendBroadcast(Intent(SmsRepository.ACTION_SMS_CHANGED).setPackage(context.packageName))
    }

    override fun onError(context: Context, error: String) {
        Log.e("EutherPingMms", "Carrier MMS download could not be stored: $error")
        context.sendBroadcast(Intent(SmsRepository.ACTION_SMS_CHANGED).setPackage(context.packageName))
    }
}
