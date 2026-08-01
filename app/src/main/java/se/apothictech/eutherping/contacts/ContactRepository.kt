package se.apothictech.eutherping.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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

    fun loadPhoneContacts(context: Context): List<PhoneContact> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        return runCatching {
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
        }.getOrDefault(emptyList())
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

    private fun normalizedNumber(number: String): String = PhoneNumberUtils.normalizeNumber(number)
}
