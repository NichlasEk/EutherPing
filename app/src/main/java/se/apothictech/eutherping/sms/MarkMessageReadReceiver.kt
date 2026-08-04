package se.apothictech.eutherping.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

class MarkMessageReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_AS_READ) return
        val threadId = intent.getLongExtra(IncomingMessageNotifier.EXTRA_THREAD_ID, -1L)
        if (threadId <= 0L) return
        if (!SmsRepository.markThreadRead(context, threadId)) return
        NotificationManagerCompat.from(context).cancel(
            intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0),
        )
        context.sendBroadcast(Intent(SmsRepository.ACTION_SMS_CHANGED).setPackage(context.packageName))
    }

    companion object {
        const val ACTION_MARK_AS_READ = "se.apothictech.eutherping.MARK_AS_READ"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
