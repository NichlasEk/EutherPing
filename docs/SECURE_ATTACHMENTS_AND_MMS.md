# Secure attachments, MMS, direct Wi-Fi, and Bluetooth

## Current state

EutherPing 0.8.7 implements encrypted images and files inside verified `Vessels`. A signed, recipient-encrypted `EP1F` offer travels over SMS while the same AES-256-GCM ciphertext payload travels over direct Wi-Fi or authenticated Bluetooth Classic. Bluetooth is limited to devices already paired in Android settings and requires optional Nearby devices access on Android 12+. The sender authenticates the downloader's Vessel signing key on either transport, offers expire after 24 hours, and no file transport falls back to plaintext MMS. Files are limited to 256 MB in this beta.

`Signals` also implements a separate carrier image-MMS beta: one selected image plus an optional caption, outgoing PDU persistence and system upload, incoming WAP Push download, provider persistence, image history, group reply-all, explicit per-conversation SIM selection, and app-level retry. Carrier MMS is explicitly labelled as ordinary and unencrypted. Video/audio, broad physical carrier testing, automatic secure peer discovery, resumable secure chunks, background foreground-service hosting, and an optional encrypted relay remain later milestones.

## Product separation

- `Signals` will own ordinary carrier SMS and MMS. Carrier MMS is not end-to-end encrypted by EutherPing.
- `Vessels` will own end-to-end encrypted text, images, and files.
- A secure attachment must be encrypted before any transport sees it. Switching transport must never change the cryptographic result or expose plaintext.

## Secure attachment envelope

Do not invent new cryptographic primitives. Version 0.8.7 generates a fresh random 256-bit content key and 96-bit nonce for every attachment and streams the file through standard AES-256-GCM. It HPKE-encrypts that key and the complete signed manifest to the verified Vessel identity. The manifest binds ciphertext and plaintext hashes and sizes, media type, safe filename, sender and recipient fingerprints, message identifier, endpoint, enabled transports, and protocol version.

The receiver verifies the signed manifest and recipient identity before accepting bytes, signs the direct request with its Vessel key, streams ciphertext into private app storage, verifies the ciphertext hash, and deletes partial or failed transfers. Plain filenames, thumbnails, keys, hashes, and media metadata are not sent outside the HPKE-encrypted envelope. A verified `image/*` offer remains encrypted until the user presses `DECRYPT // SHOW IMAGE`; AEAD authentication and bounded decoding then happen in memory without a plaintext preview file. Opening or saving a verified download performs complete authentication and creates a private temporary copy only for the requested operation; view copies are cleared on the next app start and scheduled for deletion after ten minutes.

This is still product protocol work and requires external review, protocol test vectors, broader malformed-input tests, cancellation, storage-pressure behavior, and migration to a reviewed ratcheting session protocol before a stronger security claim is made. Authenticated frame replay/staleness limits and fail-closed identity-change quarantine are implemented in the current beta.

## Transport order

1. If both verified vessels are reachable on the same Wi-Fi, transfer the encrypted payload directly over the LAN.
2. If Wi-Fi fails and the signed offer enables Bluetooth, connect to an already paired phone's EutherPing RFCOMM service and authenticate with the same Vessel proof.
3. If neither direct transport is available, keep the offer visible and explain the failure. Do not silently fall back to plaintext MMS.
4. A future opt-in relay may carry the same encrypted envelope without access to its plaintext.
5. Carrier MMS can be offered separately under `Signals`, but is never presented as Secure Ping.

### Bluetooth physical acceptance test

1. Install the same 0.7.0 build on both phones, open `System`, grant `Nearby devices`, and pair the phones in Android Bluetooth settings.
2. Return to EutherPing and confirm `BLUETOOTH VESSEL // READY` on both phones.
3. Disable Wi-Fi on both phones, keep the sender app available, and offer a small file from a verified Vessel.
4. Confirm the bubble says `BLUETOOTH`, download it on the receiver, and open the verified plaintext.
5. Repeat with Wi-Fi enabled and confirm the offer says `DIRECT WIFI + BLUETOOTH`; Wi-Fi should be tried first.
6. Unpair the phones and confirm the app reports an actionable pairing error instead of falling back to MMS or exposing plaintext.

## Direct Wi-Fi discovery and authentication

Version 0.7.0 carries a tokenized local endpoint and optional Bluetooth capability inside the recipient-encrypted offer. Bluetooth tries only Android-bonded devices, prioritizes the encrypted sender device-name hint, and accepts a transfer only after the sender verifies the recipient's signed Vessel proof. It requests `BLUETOOTH_CONNECT`, not device scanning or location. Future automatic discovery should advertise only an ephemeral service identifier; discovery merely finds a route and never creates a second trust system.

The sender authorizes a download only after verifying a request signed by the intended recipient's Vessel key. The receiver authenticates the content through the sender-signed manifest plus the exact ciphertext hash and AEAD tag; the local HTTP socket itself is not TLS. Pairing, identity changes, and safety-code verification remain SMS-backed. Direct Wi-Fi does not create a second implicit trust system.

Local networking uses Android's internet/network/Wi-Fi-state permissions; Bluetooth uses optional Nearby devices access. Neither transport has a remote backend. The UI identifies `DIRECT WIFI`, `BLUETOOTH`, or both; it never claims that a carrier MMS was a secure direct transfer.

## Carrier MMS beta

Version 0.7.0 uses the platform `SmsManager` for carrier/APN transport, composes standards-based PDU data, scales images to the carrier-reported size limit, persists outgoing and incoming MMS through Android's Telephony provider, and uses the WAP Push delivery path required of a default SMS handler. It selects the subscription supplied by an incoming push and otherwise uses Android's default SMS subscription. Stored carrier images are decoded into bounded inline previews, and opening one uses an app-controlled temporary copy so external viewers work even when the Telephony provider does not delegate its own URI.

The implementation is intentionally independent from Secure Ping attachments so neither feature becomes a fallback that weakens the other. Its PDU/provider path is covered on Android 11 emulators, but an emulator has no real MMSC. Before calling carrier MMS production-ready, test send, receive, failure, retry, roaming, mobile-data-off, Wi-Fi-calling, and multi-SIM behavior on every intended physical phone/carrier combination.
