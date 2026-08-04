package se.apothictech.eutherping.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import se.apothictech.eutherping.MainActivity
import se.apothictech.eutherping.ConversationControlsRepository
import se.apothictech.eutherping.NotificationPrivacy
import se.apothictech.eutherping.R
import se.apothictech.eutherping.contacts.ContactRepository

object IncomingMessageNotifier {
    internal const val CHANNEL_ID = "incoming_sms"
    internal const val EXTRA_THREAD_ID = "thread_id"
    internal const val EXTRA_RECIPIENTS = "recipients"
    internal const val EXTRA_SUBSCRIPTION_ID = "subscription_id"

    fun show(
        context: Context,
        address: String,
        body: String,
        secureLane: Boolean,
        threadId: Long? = null,
        displayName: String? = ContactRepository.displayName(context, address),
        recipients: List<String> = listOf(address),
        subscriptionId: Int? = null,
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val blocked = runCatching {
            ConversationControlsRepository.canBlockNumbers(context) &&
                android.provider.BlockedNumberContract.isBlocked(context, address)
        }.getOrDefault(false)
        if (blocked) return
        val privacy = ConversationControlsRepository.notificationPrivacy(context)
        val visibleTitle = when {
            secureLane || privacy == NotificationPrivacy.PRIVATE -> "New message"
            else -> displayName ?: address
        }
        val visibleBody = when {
            secureLane -> "Encrypted Secure Ping"
            privacy == NotificationPrivacy.SENDER_ONLY -> "New message"
            privacy == NotificationPrivacy.PRIVATE -> "Open EutherPing to read"
            else -> body
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Incoming messages",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Incoming SMS and MMS received by EutherPing"
            },
        )
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = "sms:$address".toUri()
            putExtra(MainActivity.EXTRA_SECURE_LANE, secureLane)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            address.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markReadAction = threadId?.let {
            val markReadIntent = Intent(context, MarkMessageReadReceiver::class.java).apply {
                action = MarkMessageReadReceiver.ACTION_MARK_AS_READ
                putExtra(EXTRA_THREAD_ID, it)
                putExtra(MarkMessageReadReceiver.EXTRA_NOTIFICATION_ID, address.hashCode())
            }
            val markReadPendingIntent = PendingIntent.getBroadcast(
                context,
                address.hashCode(),
                markReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            NotificationCompat.Action.Builder(
                R.drawable.ic_eutherping,
                "MARK AS READ",
                markReadPendingIntent,
            )
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build()
        }
        val replyAction = if (!secureLane) {
            val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
                action = NotificationReplyReceiver.ACTION_REPLY
                putExtra(NotificationReplyReceiver.EXTRA_ADDRESS, address)
                putExtra(EXTRA_RECIPIENTS, recipients.toTypedArray())
                subscriptionId?.let { putExtra(EXTRA_SUBSCRIPTION_ID, it) }
                putExtra(EXTRA_THREAD_ID, threadId ?: -1L)
                putExtra(NotificationReplyReceiver.EXTRA_NOTIFICATION_ID, address.hashCode())
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                37 * address.hashCode(),
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            NotificationCompat.Action.Builder(
                R.drawable.ic_eutherping,
                "REPLY",
                replyPendingIntent,
            )
                .addRemoteInput(
                    RemoteInput.Builder(NotificationReplyReceiver.REMOTE_INPUT_KEY)
                        .setLabel("Reply")
                        .build(),
                )
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .build()
        } else {
            null
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eutherping)
            .setContentTitle(visibleTitle)
            .setContentText(visibleBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(visibleBody))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(
                if (secureLane || privacy == NotificationPrivacy.PRIVATE) {
                    NotificationCompat.VISIBILITY_SECRET
                } else {
                    NotificationCompat.VISIBILITY_PRIVATE
                },
            )
            .apply {
                replyAction?.let(::addAction)
                markReadAction?.let(::addAction)
            }
            .build()
        NotificationManagerCompat.from(context).notify(address.hashCode(), notification)
    }

    fun showReplyFailure(context: Context, address: String, notificationId: Int, detail: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eutherping)
            .setContentTitle("Reply not sent")
            .setContentText(detail ?: "Open EutherPing to retry")
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
