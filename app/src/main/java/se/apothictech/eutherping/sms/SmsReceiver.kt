package se.apothictech.eutherping.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import se.apothictech.eutherping.MainActivity
import se.apothictech.eutherping.R
import se.apothictech.eutherping.secure.SecureRepository

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isEmpty()) return
        val address = parts.firstNotNullOfOrNull { it.displayOriginatingAddress }
            ?: parts.firstNotNullOfOrNull { it.originatingAddress }
            ?: "Unknown sender"
        val body = parts.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val timestamp = parts.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
        SecureRepository.handleIncomingControl(context, address, body)
        SmsRepository.persistIncoming(context, address, body, timestamp)
        showNotification(
            context = context,
            address = address,
            body = SecureRepository.notificationText(context, address, body),
            secureLane = SecureRepository.isSecureBody(body),
        )
    }

    private fun showNotification(context: Context, address: String, body: String, secureLane: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Incoming messages",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Incoming SMS received by EutherPing"
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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eutherping)
            .setContentTitle(address)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(address.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "incoming_sms"
    }
}
