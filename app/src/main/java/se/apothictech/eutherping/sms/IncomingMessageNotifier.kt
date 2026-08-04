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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import se.apothictech.eutherping.MainActivity
import se.apothictech.eutherping.R
import se.apothictech.eutherping.contacts.ContactRepository

object IncomingMessageNotifier {
    internal const val CHANNEL_ID = "incoming_sms"
    internal const val EXTRA_THREAD_ID = "thread_id"

    fun show(
        context: Context,
        address: String,
        body: String,
        secureLane: Boolean,
        threadId: Long? = null,
        displayName: String? = ContactRepository.displayName(context, address),
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eutherping)
            .setContentTitle(displayName ?: address)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .apply { markReadAction?.let(::addAction) }
            .build()
        NotificationManagerCompat.from(context).notify(address.hashCode(), notification)
    }
}
