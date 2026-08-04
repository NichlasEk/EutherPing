# Secure Vessels storage boundary

This document describes EutherPing 0.8.9. It is a storage boundary, not a claim
that the current Secure Beta framing is a reviewed ratcheting protocol.

## What is stored where

| Data | Location | Form |
| --- | --- | --- |
| Ordinary SMS/MMS | Android Telephony provider | Carrier plaintext and MMS parts |
| Secure message/offer transport | Android Telephony SMS provider | `EP…` encrypted or public pairing capsule only |
| Received Secure message plaintext | Not persisted by EutherPing | Decrypted for authenticated Vessel display |
| Sent Secure message plaintext and draft text | Dedicated private Secure vault | Tink AES-GCM ciphertext under Android Keystore |
| Secure draft presence index | Dedicated private Secure draft index | Has-draft flag under a one-way conversation identifier; no text or attachment URI |
| Secure attachment payload | `filesDir/secure_attachments/{incoming,outgoing}` | AES-256-GCM ciphertext (`.enc`) |
| Conversation startup index | Private app file | Neutral Vessel placeholder; never Secure plaintext |
| Accepted-frame replay index | Private app SharedPreferences | Bounded hashes and acceptance times only |
| Trusted and pending peer identities | Private app SharedPreferences | Public keys, fingerprints, state; no private key material |
| Inline Secure image preview | App memory after explicit tap | Authenticated decoded bitmap; recycled with UI |
| Explicit external open | Private app cache through FileProvider | Temporary plaintext, deleted after ten minutes or next start |
| Explicit save | User-selected document destination | Plaintext by deliberate user action; no longer Vessel-protected |

Secure messages use carrier SMS as their transport, so the encrypted wire
capsule and carrier metadata necessarily exist in Android Telephony. Secure
plaintext is never written to the ordinary SMS/MMS provider or the conversation
index. Carrier/network metadata is not hidden by this design.

Version 0.8.9 also removes Secure draft metadata from the ordinary carrier-draft
preferences. A legacy hashed Vessel presence flag is migrated once into the
dedicated Secure index and deleted from the ordinary store. Ordinary draft
cleanup no longer invokes the Secure vault.

## Image and file lifecycle

Downloading an attachment authenticates the signed manifest and verifies the
ciphertext size and SHA-256 hash. It stores only ciphertext. EutherPing waits for
the user to press `DECRYPT // SHOW IMAGE` before running AES-GCM authentication
and plaintext hash/size verification. Image previews up to 32 MiB are decoded
from a byte array in memory; the source byte array is overwritten immediately
after decode and the bitmap is recycled when the UI releases it.

Opening a file in another Android app cannot be memory-only because the viewer
needs a URI it can read. EutherPing therefore creates a private cache copy,
grants temporary read access through its non-exported FileProvider, schedules
deletion after ten minutes, and removes leftovers when EutherPing next starts.
The save flow similarly uses a private temporary copy only while writing to the
destination selected by the user, then deletes it in `finally`.

Cloud backup and device-to-device transfer are disabled for the entire app.

Authenticated message, attachment, and current pairing frames are admitted once. A private replay
index hashes the peer fingerprint, frame kind, and random frame ID, then stores
only that derived key, the ciphertext SHA-256, and local acceptance time. It is
bounded to 4,096 records with 90-day retention. Exact network replays, conflicting
ciphertext under an accepted ID, signed frames older than 30 days, and frames
more than 24 hours ahead of the local clock are rejected before Telephony
persistence. This is an anti-replay layer for the current beta framing, not a
substitute for the planned reviewed ratcheting protocol.

New pairing controls self-sign their timestamp, random 128-bit control ID, and
complete compact public-key bundle with the included Ed25519 identity. The same
30-day past and 24-hour future bounds apply before any peer state or Telephony
row is written. Legacy v2/v1 controls remain readable and exact duplicates are
suppressed, but their old formats contain no authenticated creation time and
therefore cannot receive retrospective stale-age enforcement.

An incoming pairing control with a different identity cannot replace a verified
peer. The trusted public keys remain in their original fields, the candidate
public keys are stored separately in private app preferences, and Secure sending
is disabled until the user either rejects the candidate or promotes it into a
fresh unverified safety-code flow. Accepting a replacement invitation returns
this installation's public-key acceptance capsule so the reinstalled/replaced
phone can complete the two-sided handshake. No identity key or safety code is
written into ordinary message plaintext.

## Automated evidence

`SecureAttachmentRepositoryTest` verifies that an image preview succeeds
without leaving preview/save files, corrupted ciphertext fails authentication,
and every transient Secure cache directory is cleaned at startup.
`ConversationIndexCacheTest` verifies that a supplied Secure plaintext preview
is replaced by the neutral encrypted placeholder before the index is written.
`SecureReplayRepositoryTest` verifies first acceptance, exact replay rejection,
ID/ciphertext conflict rejection, freshness windows, and non-recording of stale
frames against Android's real private app storage.
`SecureIdentityTransitionTest` verifies preservation of trusted keys,
idempotence, quarantine of replacement keys, and the initial acceptance state.

The remaining Phase 6 work is migration to a reviewed asynchronous session
implementation with forward secrecy and post-compromise security. Until that is
implemented and externally reviewed, the product remains labelled **Secure
Beta**.
