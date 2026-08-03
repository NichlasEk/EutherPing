# EutherPing

EutherPing is an original Android messaging project with two deliberately distinct lanes:

- ordinary carrier SMS (`CELL`)
- authenticated end-to-end encrypted capsules transported as carrier SMS (`SECURE PING`)

The current `0.7.6` checkpoint is a functional SMS, image-MMS, and Secure Ping beta with two separate inboxes. Incoming SMS and MMS notifications prefer the matching phonebook name, and opening one resolves the same name in the conversation header. Resuming EutherPing refreshes the current bounded message page and retries carrier-image previews, so an MMS received while the phone sleeps appears without closing and reopening the conversation. `Signals` owns ordinary carrier SMS and clearly labelled, unencrypted carrier image MMS. Carrier images are shown inline and copied into a temporary FileProvider cache before opening, because Android's protected MMS provider URI cannot reliably be delegated to external viewers. MMS setup now requests `RECEIVE_MMS` and `RECEIVE_WAP_PUSH` explicitly for devices that do not grant the complete SMS permission group with the default-app role, and incoming downloads prefer the broadcast subscription before falling back to the default SMS or data subscription. Large outgoing images are sampled, recompressed, and spatially reduced until they fit the carrier payload limit; a failed conversion always releases the composer instead of leaving attachment controls busy. Incoming MMS persistence bypasses the legacy configuration path that crashes on Samsung Android 16, catches all receiver failures, and recovers a downloaded PDU left behind by an interrupted older build. A newly stored carrier MMS now posts the same high-priority local message notification as SMS and opens its Signal conversation; cache recovery does not replay stale notifications. Long-pressing an MMS or verified Vessel image offers separate open and save actions through Android's document picker; an explicitly saved Vessel image is decrypted and clearly described as no longer Vessel-protected. Web links in message text open in the browser, while long-pressing the surrounding message bubble provides copy, forward, and local-delete actions. `Vessels` owns secure contact discovery, invitations, safety-code verification, encrypted conversations, and encrypted file or image offers. Its header search opens an opt-in phonebook search and selecting a contact starts a dedicated secure vessel. Conversation bubbles distinguish received messages on the left from sent messages on the right, System offers persistent Light and Cool Dark themes, and Android's system Back action moves up through the current screen hierarchy before leaving the app. A private on-device conversation index is rendered immediately while Android's Telephony provider refreshes it in the background. Refresh retains only the latest ordinary and secure row per thread, decrypts only the secure preview it renders, resolves MMS addresses once per MMS-only thread, and remains resident while a conversation is open. Conversation detail asks Android's provider for only the newest 20 rows in the selected Signals or Vessels lane. Reaching the older edge loads 30 more automatically; MMS rows and parts use the same bounded paging instead of scanning the complete MMS history. Provider notifications are debounced, MMS parts remain lazy, and contact-name completion does not trigger another provider pass. Cached Vessel rows never contain decrypted Secure Ping plaintext. Permissions are refreshed whenever the app resumes, and provider failures are shown explicitly rather than appearing as an unexplained empty list.

Long-pressing a message offers copy, forward, and local deletion. Secure forwards are encrypted again for a separately verified Vessel and never silently fall back to ordinary SMS. The conversation overflow menu can copy the address or delete the complete local Android thread after confirmation; the main overflow menu refreshes messages or opens System and privacy.

Secure Ping pairs two installations with compact public-key invitation and acceptance capsules, shows the same safety code on both phones, and encrypts signed messages to the recipient's public key only after both users verify that code. It uses Google Tink's HPKE construction (X25519, HKDF-SHA-256, AES-256-GCM) and Ed25519 signatures. Private keysets and the local sent-message plaintext vault are protected by Android Keystore. No private key is placed in SMS or the Android Telephony provider.

This is deliberately labelled **Secure Beta**. It does not yet implement a Double Ratchet, forward secrecy, post-compromise security, replay tracking, group encryption, or secure backups. Carrier metadata, delivery records, multipart-SMS cost, and the fact that two numbers communicate remain visible to the mobile network. Carrier MMS is a separate ordinary-telephony feature and is not end-to-end encrypted. Its first beta supports one image plus an optional caption; group MMS, video/audio, manual per-SIM selection, blocking, and backup remain future work.

## Try Secure Ping on two phones

1. Install the same APK on both Android phones and choose EutherPing as the default SMS app.
2. Open `Vessels` on phone A, tap the search icon, and select the other person from the phonebook.
3. Tap `SEND SECURE INVITE` in the new vessel.
4. On phone B, open `Vessels`, select the incoming vessel, and tap `ACCEPT SECURE PING`.
5. Compare the safety code shown on both phones. If every group matches, tap `CODES MATCH — VERIFY` on both.
6. Send a short test message in the vessel conversation. The composer remains locked until verification is complete.

Version 0.7.0 keeps each invitation and acceptance to one carrier SMS and matches equivalent local/international number formats such as `070…` and `+4670…`. Encrypted messages and attachment offers can span several SMS, so carrier charges may apply. Secure mode never silently falls back to plaintext SMS or MMS. GrapheneOS can grant Network for direct Wi-Fi and Nearby devices for Bluetooth attachment transfer.

In a verified Vessel, the attachment button encrypts a selected file locally with AES-256-GCM, signs and HPKE-encrypts its key and manifest for the recipient, and sends that offer as Secure Ping SMS capsules. The same encrypted payload transfers over direct Wi-Fi first or authenticated Bluetooth Classic between phones already paired in Android settings. Bluetooth never carries ordinary SMS or decrypted text. The sender must keep EutherPing available, offers expire after 24 hours, and this beta limits files to 256 MB. The receiving phone verifies the Vessel identity, request proof, ciphertext hash, AEAD tag, plaintext size, and plaintext hash. Verified images are fetched and shown inline only in their separate Vessel conversation; other files retain an explicit open action.

In an ordinary Signal conversation, the same attachment button selects an image, scales and JPEG-compresses it to the active carrier's reported MMS limit, builds a standard MMS PDU, persists it in Android's Telephony provider, and delegates upload to Android's subscription-aware `SmsManager`. Incoming WAP Push notifications are downloaded through the same system API and persisted as carrier MMS. This path needs physical testing across the intended phones, SIMs, APNs, roaming policies, and carriers; emulator tests can prove PDU/provider integration but cannot prove a mobile operator accepts the transfer.

## Build

```sh
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk ANDROID_HOME=/opt/android-sdk ./gradlew testDebugUnitTest assembleDebug lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Privacy

EutherPing 0.7.0 has no accounts, analytics, ads, telemetry, or remote service. Network and optional Nearby devices permissions are used only for direct encrypted attachment transfer and Android's carrier MMS transport. Bluetooth access is limited to already paired devices and does not scan location. SMS and MMS data is read from and written to Android's system Telephony provider only after the user makes EutherPing the default SMS app. A private local index caches ordinary conversation previews for fast startup; cached Vessel rows use a neutral placeholder and never store decrypted Secure Ping plaintext. Secure Ping plaintext is decrypted only inside EutherPing; outgoing text plaintext is stored in a Keystore-protected local vault so the sender can read their own history. Attachment payloads remain encrypted in private app storage. Inline Vessel image decoding uses a short-lived plaintext cache file that is deleted immediately after the bounded preview is created; explicitly opening a file creates a private view copy scheduled for deletion. Phonebook data is read locally only after the separate Contacts permission is granted. Theme selection is stored only in local app preferences. Incoming-message notifications are generated locally and do not expose Secure Ping plaintext on the lock screen. Cloud backup and device transfer are disabled.

## Visual direction

Near-black OLED surfaces, toxic-green sonar geometry, amber carrier signals, and violet secure echoes. The bundled sonar artwork is original and generated specifically for EutherPing from a user-provided mood reference.

## Security direction

EutherPing owns its compact `EP2I`/`EP2A` pairing framing and `EP1M` message framing, product semantics, and versioning while relying on established cryptographic constructions. Legacy `EP1I`/`EP1A` pairing messages remain readable. No silent fallback from Secure Ping to plaintext SMS is allowed. The next security milestone is a reviewed session protocol with replay protection and a Double Ratchet instead of adding home-grown cryptographic primitives.

License selection is intentionally deferred until the first product architecture is settled.

The planned separation between carrier MMS, encrypted attachments, and direct local transfer is documented in [`docs/SECURE_ATTACHMENTS_AND_MMS.md`](docs/SECURE_ATTACHMENTS_AND_MMS.md).
