# Secure attachments, MMS, and direct Wi-Fi

## Current state

EutherPing 0.6.0 implements encrypted images and files inside verified `Vessels`. A signed, recipient-encrypted `EP1F` offer travels over SMS while the AES-256-GCM ciphertext payload travels directly over the same local Wi-Fi. The sender authenticates the downloader's Vessel signing key, offers expire after 24 hours, and no file transport falls back to plaintext MMS. Files are limited to 256 MB in this beta.

`Signals` also implements a separate carrier image-MMS beta: one selected image plus an optional caption, outgoing PDU persistence and system upload, incoming WAP Push download, provider persistence, and image history. Carrier MMS is explicitly labelled as ordinary and unencrypted. Group MMS, video/audio, manual per-SIM selection, app-level retries, and broad physical carrier testing remain later milestones. Bluetooth transport, automatic secure peer discovery, resumable secure chunks, background foreground-service hosting, and an optional encrypted relay also remain later milestones.

## Product separation

- `Signals` will own ordinary carrier SMS and MMS. Carrier MMS is not end-to-end encrypted by EutherPing.
- `Vessels` will own end-to-end encrypted text, images, and files.
- A secure attachment must be encrypted before any transport sees it. Switching transport must never change the cryptographic result or expose plaintext.

## Secure attachment envelope

Do not invent new cryptographic primitives. Version 0.6.0 generates a fresh random 256-bit content key and 96-bit nonce for every attachment and streams the file through standard AES-256-GCM. It HPKE-encrypts that key and the complete signed manifest to the verified Vessel identity. The manifest binds ciphertext and plaintext hashes and sizes, media type, safe filename, sender and recipient fingerprints, message identifier, endpoint, and protocol version.

The receiver verifies the signed manifest and recipient identity before accepting bytes, signs the direct request with its Vessel key, streams ciphertext into private app storage, authenticates the complete file before exposing it, and deletes partial or failed transfers. Plain filenames, thumbnails, keys, hashes, and media metadata are not sent outside the HPKE-encrypted envelope. Opening a verified download creates a temporary private cache copy for Android's selected viewer; it is cleared on the next app start and scheduled for deletion after ten minutes.

This is still product protocol work and requires review, test vectors, malformed-input tests, size limits, replay handling, cancellation, storage-pressure behavior, and key-change behavior before a security claim is made.

## Transport order

1. If both verified vessels are reachable on the same Wi-Fi, transfer the encrypted payload directly over the LAN.
2. If local transfer is unavailable, keep the offer visible and explain the failure. Do not silently fall back to plaintext MMS.
3. A future opt-in relay may carry the same encrypted envelope without access to its plaintext.
4. Carrier MMS can be offered separately under `Signals`, but is never presented as Secure Ping.

## Direct Wi-Fi discovery and authentication

Version 0.6.0 carries a tokenized local endpoint inside the recipient-encrypted offer and does not broadcast a phone number, contact name, stable fingerprint, or filename on the LAN. Future discovery should use Android's supported network-service discovery APIs and advertise only an ephemeral service identifier. Discovery merely finds a route; authorization still requires an already verified Vessel identity.

The sender authorizes a download only after verifying a request signed by the intended recipient's Vessel key. The receiver authenticates the content through the sender-signed manifest plus the exact ciphertext hash and AEAD tag; the local HTTP socket itself is not TLS. Pairing, identity changes, and safety-code verification remain SMS-backed. Direct Wi-Fi does not create a second implicit trust system.

Local networking uses only Android's internet/network/Wi-Fi-state permissions and has no remote backend. The UI identifies `DIRECT WIFI`; it never claims that a carrier MMS was a secure direct transfer.

## Carrier MMS beta

Version 0.6.0 uses the platform `SmsManager` for carrier/APN transport, composes standards-based PDU data, scales images to the carrier-reported size limit, persists outgoing and incoming MMS through Android's Telephony provider, and uses the WAP Push delivery path required of a default SMS handler. It selects the subscription supplied by an incoming push and otherwise uses Android's default SMS subscription.

The implementation is intentionally independent from Secure Ping attachments so neither feature becomes a fallback that weakens the other. Its PDU/provider path is covered on Android 11 emulators, but an emulator has no real MMSC. Before calling carrier MMS production-ready, test send, receive, failure, retry, roaming, mobile-data-off, Wi-Fi-calling, and multi-SIM behavior on every intended physical phone/carrier combination.
