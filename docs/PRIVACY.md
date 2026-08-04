# EutherPing privacy policy draft

Last updated: 2026-08-04

This repository document is the source draft for EutherPing's future public and in-app privacy policy. A stable public URL and an in-app link are required before a Google Play submission.

## What EutherPing accesses

- SMS and MMS messages, addresses, timestamps, delivery state, and MMS media, only while EutherPing is the active Android default SMS handler.
- Phone contacts, only after the separate Contacts permission is granted, to let the user search for message recipients and display local contact names.
- A user-selected image or file, through Android's system document picker, when the user explicitly attaches it.
- Local-network state and a local IP address when a verified Vessel sends or receives a direct encrypted attachment.
- Already paired Bluetooth device names when the user enables the optional Nearby devices fallback. EutherPing does not request Bluetooth scanning or location.
- Cryptographic identity keys created for Secure Ping and protected using Android Keystore.
- An Android biometric authentication result when the optional Vessel seal is enabled. EutherPing never receives fingerprint or biometric data.

## How the data is used

SMS and MMS data is used to display, send, receive, and maintain the user's messaging history through Android's Telephony provider. Carrier SMS and MMS necessarily pass through the user's mobile operator and are not end-to-end encrypted by EutherPing.

Secure Ping text is encrypted to a verified recipient before it is placed in carrier SMS. Secure attachment ciphertext is transferred over direct local Wi-Fi or authenticated Bluetooth between already paired phones; its signed key and manifest are carried inside an encrypted Secure Ping SMS capsule. Phone numbers and carrier metadata remain visible to the mobile operator.

Contacts stay on the device. EutherPing does not upload the address book. Selected carrier-MMS images are scaled locally before Android hands the MMS to the operator. Selected secure attachments are encrypted locally before transport.

## Storage and deletion

- Ordinary SMS and MMS history is stored in Android's system Telephony provider and follows Android's message deletion and device-retention behavior.
- Secure Ping private keys and the sent-text display vault are stored in app-private storage protected by Android Keystore.
- Ordinary text and pending-image draft references are stored in app-private preferences. Vessel draft text is encrypted by the Android-Keystore-protected vault, and cleared after a successful send or explicit draft removal.
- Secure attachment payloads remain encrypted in a dedicated app-private storage directory. An inline Vessel image is decrypted and authenticated only after an explicit tap, decoded from a bounded byte array in memory, and never written to a plaintext preview file. Explicitly opening a verified file creates a plaintext viewing copy scheduled for deletion after ten minutes; saving creates a temporary private copy only while writing to the user-selected destination. Remaining transient files are cleared on the next app start.
- Temporary MMS transport PDU files are stored in private cache and deleted after completion or by Android cache management.
- Android cloud backup and device transfer are disabled for the app.

## Collection, sharing, and remote services

EutherPing 0.8.8 has no developer account system, analytics, ads, telemetry, or EutherPing-operated message relay. The developer does not collect or sell message, contact, file, or usage data. A private app file caches ordinary conversation previews and non-secret Vessel metadata for fast startup; decrypted Secure Ping text is deliberately replaced by a neutral placeholder in that index. The Vessel seal's enabled/disabled choice is stored in local app preferences, while authentication is handled entirely by Android. Data is disclosed to a mobile operator only when the user chooses carrier SMS or MMS, to the intended paired phone as encrypted Bluetooth attachment ciphertext, and to another selected app when the user explicitly opens or saves an attachment.

Direct Wi-Fi and Bluetooth attachment transfer do not send data to an EutherPing backend. The peer receives ciphertext and can decrypt it only with the intended verified Vessel identity.

Message search is performed locally over bounded provider results. Secure search
queries and decrypted results are not cached. Ordinary notification quick reply
passes the typed reply directly to Android's carrier SMS/MMS path; Secure
notifications do not accept plaintext RemoteInput replies.

Pin and archive state is app-private metadata. Blocking and unblocking use
Android's system blocked-number provider after explicit confirmation, so the
choice can also affect calls and other carrier-message apps. Notification
privacy can hide the message preview, or both sender and preview; Secure Vessel
notifications always use the private form.

Performance diagnostics are written only to a bounded private on-device file
and are never uploaded. They contain event names, timestamps, durations,
numeric counts, image dimensions and frame-jank totals. Phone numbers, contact
names, message contents, attachment names, cryptographic material, IP addresses
and service endpoints are excluded. A report leaves app-private storage only
when the user explicitly chooses Export in System and selects a destination
through Android's document picker.

On phones with more than one active subscription, EutherPing asks Android for
phone-state and phone-number access solely to label available SIMs and exclude
the device's own line from an incoming group-MMS participant list. The selected
subscription ID is remembered locally per conversation. SIM metadata and phone
numbers are never added to diagnostics or uploaded by EutherPing.

## Security and limitations

Secure Ping is labelled beta. It uses established cryptographic primitives, but it does not yet provide Double Ratchet forward secrecy, post-compromise security, secure backups, or group encryption. Users should compare the safety code on both devices in person or through another trusted channel before verifying a Vessel.

Carrier SMS and MMS are ordinary operator services. The `Signals` UI labels them as carrier traffic and they must not be treated as Secure Ping.

## Contact and publication TODO

Before publication, replace this section with the developer's support email and legal identity, publish the exact policy at a stable HTTPS URL, link it inside the app and Play listing, and keep the date and Data safety answers synchronized with every release.
