package se.apothictech.eutherping.sms

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import kotlin.concurrent.thread

class NotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return
        val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        val recipients = intent.getStringArrayExtra(IncomingMessageNotifier.EXTRA_RECIPIENTS)
            ?.map(String::trim)?.filter(String::isNotBlank).orEmpty().ifEmpty { listOf(address) }
        val reply = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(REMOTE_INPUT_KEY)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (address.isBlank() || reply.isBlank()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val threadId = intent.getLongExtra(IncomingMessageNotifier.EXTRA_THREAD_ID, -1L).takeIf { it > 0 }
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, address.hashCode())
        val observedSubscriptionId = intent.getIntExtra(
            IncomingMessageNotifier.EXTRA_SUBSCRIPTION_ID,
            -1,
        ).takeIf { it >= 0 }
        thread(name = "EutherPing-notification-reply") {
            try {
                val conversationKey = CarrierSubscriptionRepository.conversationKey(threadId, recipients)
                val subscriptionId = CarrierSubscriptionRepository.selected(
                    appContext,
                    conversationKey,
                    observedSubscriptionId,
                )
                val result = if (recipients.size > 1 || SmsRepository.smsPartCount(appContext, reply) > 1) {
                    CarrierMmsRepository.sendText(appContext, recipients, reply, subscriptionId)
                } else {
                    SmsRepository.sendText(appContext, address, reply, subscriptionId)
                }
                result.getOrThrow()
                SmsRepository.markThreadRead(appContext, threadId)
                appContext.getSystemService(NotificationManager::class.java).cancel(notificationId)
                appContext.sendBroadcast(
                    Intent(SmsRepository.ACTION_SMS_CHANGED).setPackage(appContext.packageName),
                )
            } catch (error: Throwable) {
                Log.e("EutherPingReply", "Notification reply failed", error)
                IncomingMessageNotifier.showReplyFailure(appContext, address, notificationId, error.message)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "se.apothictech.eutherping.REPLY_FROM_NOTIFICATION"
        const val REMOTE_INPUT_KEY = "notification_reply_text"
        const val EXTRA_ADDRESS = "reply_address"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
