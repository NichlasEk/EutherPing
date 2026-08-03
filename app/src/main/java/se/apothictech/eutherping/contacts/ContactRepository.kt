package se.apothictech.eutherping.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat

data class PhoneContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
)

object ContactRepository {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun loadPhoneContacts(context: Context): List<PhoneContact> =
        loadPhoneContactsResult(context).getOrDefault(emptyList())

    fun loadPhoneContactsResult(context: Context): Result<List<PhoneContact>> = runCatching {
        check(hasPermission(context)) { "Contacts permission is not granted" }
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val seen = hashSetOf<String>()
        buildList {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
            )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    )
                    val nameIndex = cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    )
                    val numberIndex = cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                    )
                    while (cursor.moveToNext()) {
                        val number = cursor.getString(numberIndex).orEmpty().trim()
                        if (number.isBlank()) continue
                        val normalized = normalizedNumber(number)
                        if (!seen.add(normalized.ifBlank { number })) continue
                        val contactId = cursor.getLong(idIndex)
                        add(
                            PhoneContact(
                                id = "$contactId:$normalized",
                                name = cursor.getString(nameIndex).orEmpty().ifBlank { number },
                                phoneNumber = number,
                            ),
                        )
                    }
            }
        }
    }

    fun displayName(contacts: List<PhoneContact>, address: String): String? {
        val normalizedAddress = normalizedNumber(address)
        return contacts.firstOrNull { contact ->
            val normalizedContact = normalizedNumber(contact.phoneNumber)
            normalizedAddress == normalizedContact ||
                (
                    normalizedAddress.length >= 7 &&
                        normalizedContact.length >= 7 &&
                        normalizedAddress.takeLast(7) == normalizedContact.takeLast(7)
                )
        }?.name
    }

    /** Resolve one incoming address without loading the complete phonebook. */
    fun displayName(context: Context, address: String): String? {
        if (!hasPermission(context) || address.isBlank()) return null
        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(address),
        )
        return runCatching {
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME),
                )?.trim()?.takeIf(String::isNotEmpty)
            }
        }.getOrNull()
    }

    private fun normalizedNumber(number: String): String = PhoneNumberUtils.normalizeNumber(number)
}
