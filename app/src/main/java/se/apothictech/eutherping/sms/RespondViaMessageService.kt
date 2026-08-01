package se.apothictech.eutherping.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.data?.schemeSpecificPart.orEmpty()
        val body = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (address.isNotBlank() && body.isNotBlank()) {
            SmsRepository.sendText(applicationContext, address, body)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
