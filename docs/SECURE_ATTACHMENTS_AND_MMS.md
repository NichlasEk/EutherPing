# Secure attachments, MMS, and direct Wi-Fi

## Current state

EutherPing 0.4.0 does not send or download MMS and does not attach files to Secure Ping messages. Android's MMS entry points are declared only because they are part of the default-SMS role contract. The app also deliberately has no `INTERNET` permission, so direct LAN transfer is not active yet.

## Product separation

- `Signals` will own ordinary carrier SMS and MMS. Carrier MMS is not end-to-end encrypted by EutherPing.
- `Vessels` will own end-to-end encrypted text, images, and files.
- A secure attachment must be encrypted before any transport sees it. Switching transport must never change the cryptographic result or expose plaintext.

## Secure attachment envelope

Do not invent new cryptographic primitives. Generate a fresh random content key for every attachment, encrypt the file with an established streaming AEAD construction, wrap that content key to the verified vessel's existing public encryption identity, and sign a compact manifest with the sender's existing signing identity. The manifest must bind at least the content hash, ciphertext size, media type, filename policy, sender and recipient fingerprints, message identifier, and protocol version.

The receiver must verify the signed manifest and recipient identity before accepting bytes, stream ciphertext into private app storage, authenticate the complete file before exposing it, and delete partial or failed transfers. Plain filenames, thumbnails, and media metadata must not be sent outside the encrypted envelope.

This is still product protocol work and requires review, test vectors, malformed-input tests, size limits, replay handling, cancellation, storage-pressure behavior, and key-change behavior before a security claim is made.

## Transport order

1. If both verified vessels explicitly enable local transfer and are reachable on the same Wi-Fi, transfer the encrypted envelope directly over the LAN.
2. If local transfer is unavailable, keep the attachment queued and explain why. Do not silently fall back to plaintext MMS.
3. A future opt-in relay may carry the same encrypted envelope without access to its plaintext.
4. Carrier MMS can be offered separately under `Signals`, but is never presented as Secure Ping.

## Direct Wi-Fi discovery and authentication

Local discovery should use Android's supported network-service discovery APIs and advertise only an ephemeral service identifier. A phone number, contact name, stable fingerprint, or filename must not be broadcast on the LAN. Discovery merely finds a route; authorization still requires an already verified Vessel identity.

The direct connection must mutually authenticate against the verified EutherPing identities and transfer only the already encrypted envelope. Pairing, identity changes, and safety-code verification remain SMS-backed in the first implementation. Direct Wi-Fi must not create a second implicit trust system.

Adding local networking requires an explicit privacy review and the minimum Android network permissions needed for supported OS versions. The UI must clearly show `DIRECT WIFI`, `QUEUED`, `RELAY`, or `FAILED`; it must never claim that a carrier MMS was a secure direct transfer.

## Carrier MMS milestone

Real default-SMS-app MMS support requires carrier configuration/APN handling, WAP push parsing, PDU persistence, downloads, uploads, attachments, retries, roaming and data-policy behavior, multi-SIM selection, and extensive device/carrier testing. It should be implemented independently from Secure Ping attachments so neither feature becomes a fallback that weakens the other.
