package se.apothictech.eutherping.sms

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsDeliveryStatusTest {
    @Test
    fun multipartSmsWaitsForEverySentAndDeliveredPart() {
        val context = readyContext()
        val uri = insertOutbox(context)
        try {
            SmsRepository.updateSentPartState(context, uri, Activity.RESULT_OK, 0, 2)
            assertEquals(
                Telephony.Sms.MESSAGE_TYPE_OUTBOX to Telephony.Sms.STATUS_PENDING,
                state(context, uri),
            )

            SmsRepository.updateSentPartState(context, uri, Activity.RESULT_OK, 1, 2)
            assertEquals(
                Telephony.Sms.MESSAGE_TYPE_SENT to Telephony.Sms.STATUS_NONE,
                state(context, uri),
            )

            SmsRepository.updateDeliveredPartState(context, uri, Activity.RESULT_OK, 1, 2)
            assertEquals(Telephony.Sms.STATUS_NONE, state(context, uri).second)
            SmsRepository.updateDeliveredPartState(context, uri, Activity.RESULT_OK, 0, 2)
            assertEquals(Telephony.Sms.STATUS_COMPLETE, state(context, uri).second)
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun anyFailedPartMakesThePersistedSmsRetryable() {
        val context = readyContext()
        val uri = insertOutbox(context)
        try {
            SmsRepository.updateSentPartState(
                context,
                uri,
                SmsManager.RESULT_ERROR_NO_SERVICE,
                1,
                3,
            )
            assertEquals(
                Telephony.Sms.MESSAGE_TYPE_FAILED to Telephony.Sms.STATUS_FAILED,
                state(context, uri),
            )
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun outgoingStatusTransitionRepairsVendorUnreadFlags() {
        val context = readyContext()
        val uri = insertOutbox(context)
        try {
            context.contentResolver.update(
                uri,
                ContentValues().apply {
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.SEEN, 0)
                },
                null,
                null,
            )

            SmsRepository.updateSentPartState(context, uri, Activity.RESULT_OK, 0, 1)

            assertEquals(1 to 1, readState(context, uri))
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun readyContext(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd role add-role-holder android.app.role.SMS ${context.packageName}")
            .close()
        Thread.sleep(500)
        return context
    }

    private fun insertOutbox(context: Context): Uri = checkNotNull(
        context.contentResolver.insert(
            Telephony.Sms.Outbox.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, "15550123")
                put(Telephony.Sms.BODY, "multipart status test")
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
            },
        ),
    )

    private fun state(context: Context, uri: Uri): Pair<Int, Int> = checkNotNull(
        context.contentResolver.query(
            uri,
            arrayOf(Telephony.Sms.TYPE, Telephony.Sms.STATUS),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) to cursor.getInt(1) else null
        },
    )

    private fun readState(context: Context, uri: Uri): Pair<Int, Int> = checkNotNull(
        context.contentResolver.query(
            uri,
            arrayOf(Telephony.Sms.READ, Telephony.Sms.SEEN),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) to cursor.getInt(1) else null
        },
    )
}
