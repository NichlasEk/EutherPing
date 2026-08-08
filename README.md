# EutherPing

Version 0.8.15 keeps every locally authored SMS explicitly marked `READ` and
`SEEN` while Android moves it between outbox, sent, delivered, and failed
provider states. This prevents vendor Telephony providers from presenting the
sender's own new message as unread until the conversation is reopened.

Version 0.8.14 resolves an ordinary SMS conversation to Android's real thread
before loading its first bounded page. Contact searches therefore merge local
and international number forms such as `070…` and `+4670…` immediately instead
of temporarily showing only sent or received messages. Exact lookup remains the
fast path, Android's phone-number comparison is the second path, and a bounded
recent-row scan is used only as a compatibility fallback for unusual providers.

Version 0.8.13 added ratcheted EP3F attachment manifests to the working EP3
paired-device beta. Attachment names, sizes, hashes, AES-GCM content keys,
nonces, transport tokens, and Wi-Fi/Bluetooth endpoints now travel only inside
the verified Vodozemac session. The file itself remains AES-256-GCM ciphertext
in private storage and throughout direct transfer. Images retain explicit,
in-memory-on-demand decryption, and EP3 never falls back to EP1F or MMS.

Version 0.8.12 enabled the first explicit EP3 Ratchet Beta between two phones.
New Vessel invitations carry a Tink-signed Vodozemac pre-key, acceptance is the
first encrypted pre-key message, and verified text messages use Vodozemac's Olm
ratchet over carrier SMS. Both phones must run 0.8.12, accept the new invitation,
compare the new safety code, and verify it before EP3 sending unlocks. Upgraded
sessions never fall back to legacy EP1, ordinary SMS, or MMS. Existing EP1
history remains readable. This remains a paired-device beta:
external cryptographic integration review and physical Samsung/GrapheneOS tests
are release gates, and clearing app data requires pairing again.

Version 0.8.11 introduced the Apache-2.0 Vodozemac provider and encrypted
`secure_sessions_v3` store in every app build. App startup prepares one signed
ratchet pre-key in the background and keeps its opaque account state behind
Android Keystore and `AtomicFile`; there is no runtime opt-in switch. This is
the always-on local protocol foundation, not a silent conversation migration:
At that checkpoint EP1 remained the wire format; 0.8.12 activates the explicit
EP3 paired-device beta described above.

Version 0.8.10 gives incoming SMS, MMS, and private Vessel notifications a
recognizable short-short-long EutherPing vibration. The versioned Android
notification channel makes the new default effective on upgraded phones, while
System and privacy links directly to Android's channel controls. Vibrate mode
uses the pattern; completely silent mode, Do Not Disturb, device capabilities,
and any user channel override remain under Android's control.

Version 0.8.9 completes the on-device draft namespace split: ordinary Signal
draft text/image references remain in their carrier draft store, while Vessel
draft presence metadata now lives in a dedicated Secure index and its text
remains ciphertext in the Android-Keystore-protected Secure vault. Existing
Vessel presence flags migrate once and are removed from the ordinary store.
Clearing an ordinary draft no longer touches the Secure vault.

Version 0.8.8 signs new pairing invitations and acceptances over their public
keys, millisecond timestamp, and random 128-bit control ID. Fresh controls are
admitted once through the private replay index; tampered, duplicate, older than
30 days, or implausibly future controls are rejected before Android message
history. The signed capsule can use two carrier SMS parts. Existing compact v2
and signed v1 controls remain readable and gain exact-duplicate suppression,
but cannot retroactively prove their creation time.

Version 0.8.7 preserves an already verified Vessel identity when a different
pairing identity arrives. Secure sending locks fail-closed, the replacement is
kept separately from the trusted keys, and the user must explicitly keep the
old identity or begin verification of the new safety code. Repeated or third
replacement controls cannot silently overwrite either identity.

Version 0.8.6 rejects replayed, stale, and conflicting authenticated Secure
message and attachment frames before they enter Android message history. The
private replay index stores only bounded frame hashes and acceptance times: no
message text, attachment names, keys, phone numbers, or ciphertext. Exact
duplicates are ignored without another notification; signed frames older than
30 days or more than 24 hours in the future fail closed. Pairing control capsules
remain outside this first authenticated-frame replay slice.
The reviewed ratcheting-protocol selection and its license gate are documented
in [`docs/SECURE_PROTOCOL_MIGRATION.md`](docs/SECURE_PROTOCOL_MIGRATION.md).
The current `libsignal`, Vodozemac, and OpenMLS comparison is in
[`docs/SECURE_PROTOCOL_CANDIDATE_ASSESSMENT.md`](docs/SECURE_PROTOCOL_CANDIDATE_ASSESSMENT.md),
the measured minimal-native comparison and Vodozemac spike decision are in
[`docs/SECURE_PROVIDER_SIZE_COMPARISON-2026-08-05.md`](docs/SECURE_PROVIDER_SIZE_COMPARISON-2026-08-05.md),
the real Keystore/AtomicFile session-state checkpoint is in
[`docs/SECURE_SESSIONS_V3_STORAGE-2026-08-05.md`](docs/SECURE_SESSIONS_V3_STORAGE-2026-08-05.md),
the real-phone EP3 beta flow and known limits are in
[`docs/EP3_RATCHET_BETA-2026-08-05.md`](docs/EP3_RATCHET_BETA-2026-08-05.md),
and the independent-review evidence checklist is in
[`docs/SECURE_EXTERNAL_REVIEW_PACKET.md`](docs/SECURE_EXTERNAL_REVIEW_PACKET.md).
The requirement-by-requirement implementation, emulator, physical-device, and
protocol evidence ledger is in
[`docs/ROADMAP_COMPLETION_AUDIT-2026-08-04.md`](docs/ROADMAP_COMPLETION_AUDIT-2026-08-04.md).

Version 0.8.5 hardens Secure Vessels storage. Encrypted attachment payloads stay
in a dedicated private app directory and are no longer decrypted merely because
a conversation is opened. A verified image is downloaded as ciphertext and is
decrypted only after `DECRYPT // SHOW IMAGE` is pressed; its authenticated image
bytes exist in memory only for the bounded preview and are cleared after decode.
Explicitly opening any attachment still creates a private, temporary FileProvider
copy so Android can hand it to the chosen viewer. Crash leftovers are removed at
the next app start. See [`docs/SECURE_STORAGE.md`](docs/SECURE_STORAGE.md).

Version 0.8.4 adds explicit SIM chips on multi-subscription phones, remembers a
choice per one-to-one or group conversation, and refuses to silently switch
away from a remembered SIM that is no longer active. Each carrier bubble and
inbox row shows its SIM when Android exposes the subscription ID. New Cell
Signal accepts comma-separated recipients; two or more recipients are composed
as one participant-based group MMS, and incoming group notifications reply to
the complete stored participant set. The physical carrier acceptance matrix is
in [`docs/CARRIER_SIM_GROUP_MMS_TESTING.md`](docs/CARRIER_SIM_GROUP_MMS_TESTING.md).

Version 0.8.3 adds local, privacy-safe performance diagnostics and Android
Baseline Profiles for startup, opening the inbox and a conversation, and
scrolling MMS history. System can export a bounded report containing timings,
counts, dimensions, and frame-jank totals but no phone numbers, contacts,
message text, attachment names, keys, or network endpoints. The repeatable
physical-phone procedure is documented in
[`docs/PERFORMANCE_TESTING.md`](docs/PERFORMANCE_TESTING.md).

Version 0.8.2 completes the daily-convenience phase. Long-press a conversation
to pin, archive, mark read/unread, or confirm Android system block/unblock;
archived rows can always be shown again from the deck menu. Search now covers
bounded SMS and MMS text, contact names, phone numbers, and dates. System offers
sender-and-preview, sender-only, and fully private ordinary notification modes,
while Secure Vessels remain private regardless of that choice.

Version 0.8.1 adds ordinary notification quick reply, persistent conversation
drafts, and bounded on-device message search. Vessel draft text is encrypted
under the Android Keystore vault, and Secure notifications intentionally do not
accept plaintext lock-screen replies. Version 0.8.0 added a default-on Android
biometric seal for Secure Vessels with an animated purple-green fingerprint
scanner and foreground-session relocking.
Outgoing bubbles now show provider-backed `SENDING`, `SENT`, `DELIVERED`, or
`FAILED` state. Multipart SMS waits for every part before advancing, while a
failed SMS or MMS can be retried from its message actions with locally derived
carrier, service, SIM, APN, or MMS HTTP guidance.

The current release is `0.8.15`; the detailed `0.8.0` overview below describes
the carrier and Secure Vessels baseline retained by this release.

EutherPing is an original Android messaging project with two deliberately distinct lanes:

- ordinary carrier SMS (`CELL`)
- authenticated end-to-end encrypted capsules transported as carrier SMS (`SECURE PING`)

The current `0.8.0` checkpoint is a functional SMS, image-MMS, and Secure Ping beta with two separate inboxes. Incoming SMS and MMS notifications prefer the matching phonebook name, and opening one resolves the same name in the conversation header. Notifications can mark the complete Android thread as read, and long ordinary SMS drafts automatically become captioned MMS without interrupting the composer. Resuming EutherPing refreshes the current bounded message page and retries carrier-image previews, so an MMS received while the phone sleeps appears without closing and reopening the conversation. Selecting a carrier image now creates an MMS draft instead of sending immediately: the composer shows a bounded preview above the caption field, supports replacing or removing the image, and sends only when the user presses Send. Failed sends retain the image and caption for retry. Conversation loading retries briefly when Android exposes an MMS row before its image part or image stream is ready, without widening the bounded history query. `Signals` owns ordinary carrier SMS and clearly labelled, unencrypted carrier image MMS. Carrier images are shown inline and copied into a temporary FileProvider cache before opening, because Android's protected MMS provider URI cannot reliably be delegated to external viewers. MMS setup now requests `RECEIVE_MMS` and `RECEIVE_WAP_PUSH` explicitly for devices that do not grant the complete SMS permission group with the default-app role, and incoming downloads prefer the broadcast subscription before falling back to the default SMS or data subscription. Large outgoing images are sampled, recompressed, and spatially reduced until they fit the carrier payload limit; a failed conversion always releases the composer instead of leaving attachment controls busy. Incoming MMS persistence bypasses the legacy configuration path that crashes on Samsung Android 16, catches all receiver failures, and recovers a downloaded PDU left behind by an interrupted older build. A newly stored carrier MMS now posts the same high-priority local message notification as SMS and opens its Signal conversation; cache recovery does not replay stale notifications. Long-pressing an MMS or verified Vessel image offers separate open and save actions through Android's document picker; an explicitly saved Vessel image is decrypted and clearly described as no longer Vessel-protected. Web links in message text open in the browser, while long-pressing the surrounding message bubble provides copy, forward, and local-delete actions. `Vessels` owns secure contact discovery, invitations, safety-code verification, encrypted conversations, and encrypted file or image offers. By default, entering Vessels after EutherPing has left the foreground requires an enrolled Android biometric. A purple-green fingerprint scanner fronts Android's trusted biometric prompt, and the gate can be managed under System. Its header search opens an opt-in phonebook search and selecting a contact starts a dedicated secure vessel. Conversation bubbles distinguish received messages on the left from sent messages on the right, System offers persistent Light and Cool Dark themes, and Android's system Back action moves up through the current screen hierarchy before leaving the app. A private on-device conversation index is rendered immediately while Android's Telephony provider refreshes it in the background. Refresh retains only the latest ordinary and secure row per thread, decrypts only the secure preview it renders, resolves MMS addresses once per MMS-only thread, and remains resident while a conversation is open. Conversation detail asks Android's provider for only the newest 20 rows in the selected Signals or Vessels lane. Reaching the older edge loads 30 more automatically; MMS rows and parts use the same bounded paging instead of scanning the complete MMS history. Provider notifications are debounced, MMS parts remain lazy, and contact-name completion does not trigger another provider pass. Cached Vessel rows never contain decrypted Secure Ping plaintext. Permissions are refreshed whenever the app resumes, and provider failures are shown explicitly rather than appearing as an unexplained empty list.

Long-pressing a message offers copy, forward, and local deletion. Secure forwards are encrypted again for a separately verified Vessel and never silently fall back to ordinary SMS. The conversation overflow menu can copy the address or delete the complete local Android thread after confirmation; the main overflow menu refreshes messages or opens System and privacy.

Secure Ping pairs two installations with signed public-key and ratchet-pre-key invitations, shows the same safety code on both phones, and encrypts only after both users verify that code. New 0.8.12 pairings and explicit legacy upgrades use the Apache-2.0 Vodozemac Olm ratchet; retained EP1 history uses Google Tink HPKE and Ed25519. Private keysets, ratchet state, and the local plaintext vault are protected by Android Keystore-backed encrypted storage. No private key is placed in SMS or the Android Telephony provider.

This is deliberately labelled **Ratchet Beta**. EP3 text has per-message ratchet advancement through Vodozemac, but EutherPing does not claim independently reviewed forward secrecy or post-compromise security yet. Group encryption, secure backups, ratcheted attachments, verified reset UX, and atomic receive-state/plaintext commits remain future work. Authenticated frames have bounded replay and stale-frame rejection; unexpected identity changes are quarantined. Carrier metadata, delivery records, multipart-SMS cost, and the fact that two numbers communicate remain visible to the mobile network. Carrier MMS remains a separate unencrypted telephony feature.

## Try Secure Ping on two phones

1. Install the same APK on both Android phones and choose EutherPing as the default SMS app.
2. Open `Vessels` on phone A, tap the search icon, and select the other person from the phonebook.
3. Tap `SEND SECURE INVITE` in the new vessel.
4. On phone B, open `Vessels`, select the incoming vessel, and tap `ACCEPT SECURE PING`.
5. Compare the safety code shown on both phones. If every group matches, tap `CODES MATCH — VERIFY` on both.
6. Send a short test message in the vessel conversation. The composer remains locked until verification is complete.

Version 0.8.13 requires both phones for a new EP3 pairing and its signed pre-key invitation can span several carrier SMS parts. Existing v2/v1 pairing history remains readable and a verified legacy Vessel offers an explicit EP3 upgrade. Equivalent local/international number formats such as `070…` and `+4670…` are matched. Encrypted messages and EP3F manifests can span several SMS, so carrier charges may apply. Secure mode never silently falls back to plaintext SMS, MMS, or EP1F. EP3 attachments use the existing encrypted direct Wi-Fi/Bluetooth payload transfer only after the ratcheted manifest is admitted.

In a verified Vessel, the attachment button encrypts a selected file locally with AES-256-GCM, signs and HPKE-encrypts its key and manifest for the recipient, and sends that offer as Secure Ping SMS capsules. The same encrypted payload transfers over direct Wi-Fi first or authenticated Bluetooth Classic between phones already paired in Android settings. Bluetooth never carries ordinary SMS or decrypted text. The sender must keep EutherPing available, offers expire after 24 hours, and this beta limits files to 256 MB. The receiving phone verifies the Vessel identity, request proof and ciphertext hash while downloading; AEAD authentication, plaintext size, and plaintext hash are verified only when the user asks to decrypt. Verified images are shown inline only after that explicit action and only in their separate Vessel conversation; other files retain an explicit open action.

In an ordinary Signal conversation, the same attachment button selects an image, scales and JPEG-compresses it to the active carrier's reported MMS limit, builds a standard MMS PDU, persists it in Android's Telephony provider, and delegates upload to Android's subscription-aware `SmsManager`. Incoming WAP Push notifications are downloaded through the same system API and persisted as carrier MMS. This path needs physical testing across the intended phones, SIMs, APNs, roaming policies, and carriers; emulator tests can prove PDU/provider integration but cannot prove a mobile operator accepts the transfer.

## Build

```sh
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk ANDROID_HOME=/opt/android-sdk ./gradlew testDebugUnitTest assembleDebug lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Privacy

EutherPing 0.8.15 has no accounts, analytics, ads, telemetry, or remote service. Network and optional Nearby devices permissions are used only for direct encrypted attachment transfer and Android's carrier MMS transport. Bluetooth access is limited to already paired devices and does not scan location. SMS and MMS data is read from and written to Android's system Telephony provider only after the user makes EutherPing the default SMS app. A private local index caches ordinary conversation previews for fast startup; cached Vessel rows use a neutral placeholder and never store decrypted Secure Ping plaintext. Secure Ping plaintext is decrypted only inside EutherPing; outgoing text, ratcheted attachment manifests, and Vessel drafts are stored in a Keystore-protected local vault with a separate Secure draft index so they never enter the ordinary draft store. EP3 ratchet account/session state is serialized by Vodozemac and envelope-encrypted under a dedicated Android Keystore key before atomic persistence. Attachment payloads remain encrypted in private app storage. Vessel image previews are decrypted and authenticated only after an explicit tap, decoded from memory without a plaintext preview file, and recycled when the bubble leaves composition. Explicitly opening a file creates a private view copy scheduled for deletion. Phonebook data is read locally only after the separate Contacts permission is granted. Android's biometric service verifies access to Vessels when the optional seal is enabled; EutherPing receives only success or failure and never fingerprint data. Theme and biometric-seal choices are stored only in local app preferences. Incoming-message notifications are generated locally, use an Android-controlled vibration channel, and do not expose Secure Ping plaintext on the lock screen. Cloud backup and device transfer are disabled.

## Visual direction

Near-black OLED surfaces, toxic-green sonar geometry, amber carrier signals, and violet secure echoes. The bundled sonar artwork is original and generated specifically for EutherPing from a user-provided mood reference.

## Security direction

EutherPing owns its `EP3I`/`EP3A` pairing, `EP3M` text, and `EP3F` attachment-manifest envelopes, product semantics, and versioning while Vodozemac owns ratchet session establishment and state advancement. Legacy `EP2I`/`EP2A`, `EP1I`/`EP1A`, `EP1M`, and `EP1F` history remains readable. No silent fallback from EP3 to legacy Secure Ping, plaintext SMS, or MMS is allowed. Independent integration review and physical two-phone interop remain required before removing the beta label.

License selection is intentionally deferred until the first product architecture is settled.

The planned separation between carrier MMS, encrypted attachments, and direct local transfer is documented in [`docs/SECURE_ATTACHMENTS_AND_MMS.md`](docs/SECURE_ATTACHMENTS_AND_MMS.md).
