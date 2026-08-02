# EutherPing privacy policy draft

Last updated: 2026-08-02

This repository document is the source draft for EutherPing's future public and in-app privacy policy. A stable public URL and an in-app link are required before a Google Play submission.

## What EutherPing accesses

- SMS and MMS messages, addresses, timestamps, delivery state, and MMS media, only while EutherPing is the active Android default SMS handler.
- Phone contacts, only after the separate Contacts permission is granted, to let the user search for message recipients and display local contact names.
- A user-selected image or file, through Android's system document picker, when the user explicitly attaches it.
- Local-network state and a local IP address when a verified Vessel sends or receives a direct encrypted attachment.
- Cryptographic identity keys created for Secure Ping and protected using Android Keystore.

## How the data is used

SMS and MMS data is used to display, send, receive, and maintain the user's messaging history through Android's Telephony provider. Carrier SMS and MMS necessarily pass through the user's mobile operator and are not end-to-end encrypted by EutherPing.

Secure Ping text is encrypted to a verified recipient before it is placed in carrier SMS. Secure attachment ciphertext is transferred directly over the local Wi-Fi network; its signed key and manifest are carried inside an encrypted Secure Ping SMS capsule. Phone numbers and carrier metadata remain visible to the mobile operator.

Contacts stay on the device. EutherPing does not upload the address book. Selected carrier-MMS images are scaled locally before Android hands the MMS to the operator. Selected secure attachments are encrypted locally before transport.

## Storage and deletion

- Ordinary SMS and MMS history is stored in Android's system Telephony provider and follows Android's message deletion and device-retention behavior.
- Secure Ping private keys and the sent-text display vault are stored in app-private storage protected by Android Keystore.
- Secure attachment payloads remain encrypted in app-private storage. A plaintext viewing copy is created only when the user opens a verified file, scheduled for deletion after ten minutes, and cleared on the next app start.
- Temporary MMS transport PDU files are stored in private cache and deleted after completion or by Android cache management.
- Android cloud backup and device transfer are disabled for the app.

## Collection, sharing, and remote services

EutherPing 0.6.5 has no developer account system, analytics, ads, telemetry, or EutherPing-operated message relay. The developer does not collect or sell message, contact, file, or usage data. A private app file caches ordinary conversation previews and non-secret Vessel metadata for fast startup; decrypted Secure Ping text is deliberately replaced by a neutral placeholder in that index. Data is disclosed to a mobile operator only when the user chooses carrier SMS or MMS, and to another selected app when the user explicitly opens an attachment using Android's viewer mechanism.

Direct local encrypted attachment transfer does not send data to an EutherPing backend. The peer receives ciphertext and can decrypt it only with the intended verified Vessel identity.

## Security and limitations

Secure Ping is labelled beta. It uses established cryptographic primitives, but it does not yet provide Double Ratchet forward secrecy, post-compromise security, secure backups, or group encryption. Users should compare the safety code on both devices in person or through another trusted channel before verifying a Vessel.

Carrier SMS and MMS are ordinary operator services. The `Signals` UI labels them as carrier traffic and they must not be treated as Secure Ping.

## Contact and publication TODO

Before publication, replace this section with the developer's support email and legal identity, publish the exact policy at a stable HTTPS URL, link it inside the app and Play listing, and keep the date and Data safety answers synchronized with every release.
