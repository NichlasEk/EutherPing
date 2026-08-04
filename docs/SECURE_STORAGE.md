# Secure Vessels storage boundary

This document describes EutherPing 0.8.5. It is a storage boundary, not a claim
that the current Secure Beta framing is a reviewed ratcheting protocol.

## What is stored where

| Data | Location | Form |
| --- | --- | --- |
| Ordinary SMS/MMS | Android Telephony provider | Carrier plaintext and MMS parts |
| Secure message/offer transport | Android Telephony SMS provider | `EP…` encrypted or public pairing capsule only |
| Received Secure message plaintext | Not persisted by EutherPing | Decrypted for authenticated Vessel display |
| Sent Secure message plaintext and drafts | Private app SharedPreferences | Tink AES-GCM ciphertext under Android Keystore |
| Secure attachment payload | `filesDir/secure_attachments/{incoming,outgoing}` | AES-256-GCM ciphertext (`.enc`) |
| Conversation startup index | Private app file | Neutral Vessel placeholder; never Secure plaintext |
| Inline Secure image preview | App memory after explicit tap | Authenticated decoded bitmap; recycled with UI |
| Explicit external open | Private app cache through FileProvider | Temporary plaintext, deleted after ten minutes or next start |
| Explicit save | User-selected document destination | Plaintext by deliberate user action; no longer Vessel-protected |

Secure messages use carrier SMS as their transport, so the encrypted wire
capsule and carrier metadata necessarily exist in Android Telephony. Secure
plaintext is never written to the ordinary SMS/MMS provider or the conversation
index. Carrier/network metadata is not hidden by this design.

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

## Automated evidence

`SecureAttachmentRepositoryTest` verifies that an image preview succeeds
without leaving preview/save files, corrupted ciphertext fails authentication,
and every transient Secure cache directory is cleaned at startup.
`ConversationIndexCacheTest` verifies that a supplied Secure plaintext preview
is replaced by the neutral encrypted placeholder before the index is written.

The remaining Phase 6 work is replay/staleness rejection plus migration to a
reviewed asynchronous session implementation with forward secrecy and
post-compromise security. Until that is implemented and externally reviewed,
the product remains labelled **Secure Beta**.
