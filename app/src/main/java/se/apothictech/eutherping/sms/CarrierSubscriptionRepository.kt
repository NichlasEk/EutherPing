package se.apothictech.eutherping.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import java.security.MessageDigest

data class CarrierSubscription(
    val id: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String,
) {
    val label: String
        get() = buildString {
            append("SIM ")
            append(slotIndex + 1)
            displayName.takeIf(String::isNotBlank)?.let { append(" // ").append(it) }
            carrierName.takeIf { it.isNotBlank() && it != displayName }?.let { append(" // ").append(it) }
        }
}

object CarrierSubscriptionRepository {
    private const val PREFERENCES = "carrier_subscription_choices"

    fun active(context: Context): List<CarrierSubscription> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) return emptyList()
        return runCatching {
            context.getSystemService(SubscriptionManager::class.java)
                .activeSubscriptionInfoList
                .orEmpty()
                .map {
                    CarrierSubscription(
                        id = it.subscriptionId,
                        slotIndex = it.simSlotIndex,
                        displayName = it.displayName?.toString().orEmpty(),
                        carrierName = it.carrierName?.toString().orEmpty(),
                    )
                }
        }.getOrDefault(emptyList())
    }

    fun conversationKey(threadId: Long?, recipients: List<String>): String {
        threadId?.takeIf { it > 0 }?.let { return "thread:$it" }
        val canonical = recipients.map(String::trim).filter(String::isNotBlank).sorted().joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return "recipients:" + digest.take(12).joinToString("") { "%02x".format(it) }
    }

    fun selected(context: Context, conversationKey: String, observedSubscriptionId: Int? = null): Int? {
        val active = active(context)
        if (active.isEmpty()) return observedSubscriptionId?.takeIf { it >= 0 }
        val ids = active.mapTo(hashSetOf(), CarrierSubscription::id)
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.contains(conversationKey)) {
            return preferences.getInt(conversationKey, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                .takeIf(ids::contains)
        }
        observedSubscriptionId?.takeIf(ids::contains)?.let { return it }
        SubscriptionManager.getDefaultSmsSubscriptionId().takeIf(ids::contains)?.let { return it }
        return active.singleOrNull()?.id
    }

    fun remember(context: Context, conversationKey: String, subscriptionId: Int) {
        require(subscriptionId >= 0) { "Invalid carrier subscription" }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putInt(conversationKey, subscriptionId).apply()
    }

    fun label(context: Context, subscriptionId: Int?): String? {
        if (subscriptionId == null || subscriptionId < 0) return null
        return active(context).firstOrNull { it.id == subscriptionId }?.label ?: "SIM ID $subscriptionId"
    }
}
