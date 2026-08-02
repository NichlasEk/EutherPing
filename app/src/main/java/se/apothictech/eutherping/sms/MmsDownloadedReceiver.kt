package se.apothictech.eutherping.sms

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.klinker.android.send_message.MmsReceivedReceiver

class MmsDownloadedReceiver : MmsReceivedReceiver() {
    override fun onMessageReceived(context: Context, messageUri: Uri) {
        context.sendBroadcast(Intent(SmsRepository.ACTION_SMS_CHANGED).setPackage(context.packageName))
    }

    override fun onError(context: Context, error: String) {
        context.sendBroadcast(Intent(SmsRepository.ACTION_SMS_CHANGED).setPackage(context.packageName))
    }
}
