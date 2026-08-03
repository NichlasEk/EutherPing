package se.apothictech.eutherping.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

class MmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CarrierMmsRepository.ACTION_MMS_SENT) return
        if (resultCode != android.app.Activity.RESULT_OK) {
            Log.e("EutherPingMms", "Carrier MMS send failed with result code $resultCode")
        }
        CarrierMmsRepository.updateSentState(
            context,
            intent.getStringExtra(CarrierMmsRepository.EXTRA_MMS_URI).orEmpty(),
            resultCode,
        )
        intent.getStringExtra("pdu_path")?.let(::File)?.delete()
    }
}
