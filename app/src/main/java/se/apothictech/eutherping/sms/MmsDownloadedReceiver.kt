package se.apothictech.eutherping.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import java.io.File
import kotlin.concurrent.thread

class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val file = intent.getStringExtra(CarrierMmsRepository.EXTRA_DOWNLOADED_FILE)?.let(::File)
        if (resultCode != Activity.RESULT_OK) {
            Log.e(
                "EutherPingMms",
                "Carrier MMS download failed with result code $resultCode and HTTP status " +
                    intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, 0),
            )
            file?.delete()
            context.sendBroadcast(Intent(SmsRepository.ACTION_SMS_CHANGED).setPackage(context.packageName))
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val resultIntent = Intent(intent)
        thread(name = "EutherPing-MMS-persist") {
            try {
                CarrierMmsRepository.persistDownloadedMms(
                    appContext,
                    checkNotNull(file) { "Carrier MMS download did not provide a temporary file" },
                    resultIntent.getStringExtra(CarrierMmsRepository.EXTRA_CONTENT_LOCATION),
                    resultIntent.getIntExtra(CarrierMmsRepository.EXTRA_SUBSCRIPTION_ID, -1),
                ).getOrThrow()
            } catch (error: Throwable) {
                Log.e("EutherPingMms", "Carrier MMS download could not be stored", error)
                appContext.sendBroadcast(
                    Intent(SmsRepository.ACTION_SMS_CHANGED).setPackage(appContext.packageName),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
