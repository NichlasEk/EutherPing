# Secure pairing control framing

Status: EutherPing 0.8.8 Secure Beta framing. This document describes the
current identity-exchange control; it is not the planned ratcheting session
protocol and does not claim forward secrecy or post-compromise security.

## Transport and binary layout

New invitations retain the `EP2I:` text prefix and acceptances retain `EP2A:`.
The suffix is URL-safe, unpadded Base64 containing this big-endian version 3
structure:

| Field | Size | Meaning |
| --- | ---: | --- |
| Version | 1 byte | `3` |
| Creation time | 8 bytes | Unix epoch milliseconds |
| Control ID | 16 bytes | Fresh cryptographically random identifier |
| HPKE key ID | 4 bytes | Tink primary key ID |
| HPKE value length | 2 bytes | Unsigned length |
| HPKE public value | bounded | Tink X25519 HPKE public-key protobuf value |
| Ed25519 key ID | 4 bytes | Tink primary key ID |
| Ed25519 value length | 2 bytes | Unsigned length |
| Ed25519 public value | bounded | Tink Ed25519 public-key protobuf value |
| Signature length | 2 bytes | Unsigned length, currently Tink-prefixed Ed25519 |
| Signature | bounded | Signature over every preceding binary field |

The complete text capsule is bounded by instrumentation to at most 306 GSM
characters, which is at most two 153-character multipart SMS payloads. No
private key is transported. The fingerprint and safety code are derived from
the reconstructed HPKE and signing public keysets, not from a claimed value in
the message.

## Admission order

For a received version 3 control EutherPing:

1. applies strict Base64 and binary length bounds;
2. reconstructs and validates the exact expected Tink public-key types;
3. verifies the self-signature with the included Ed25519 public key;
4. derives the sender fingerprint from both public keysets;
5. rejects creation times over 30 days old or more than 24 hours ahead;
6. admits the `(fingerprint, kind, random control ID, capsule hash)` once through
   the bounded private replay index;
7. applies the peer-state transition; and
8. only then permits Android Telephony persistence and local notification.

Invalid, stale, duplicate, and ID/hash-conflicting controls return before peer
state and Telephony history are changed. Self-signing prevents an observer from
refreshing the timestamp or control ID on somebody else's captured key bundle.
It does not prove who owns a newly introduced identity: users must still compare
the safety code out of band before `VERIFIED`, and an unexpected replacement is
quarantined with sending disabled.

## Legacy behavior

Compact binary version 2 (`EP2I`/`EP2A`) and signed JSON version 1
(`EP1I`/`EP1A`) remain readable so existing history and in-flight pairings do
not become plaintext or silently trusted. Their exact capsule hash is admitted
once, but neither older format contains an authenticated creation time, so age
cannot be proven retroactively. A pre-0.8.8 app cannot parse version 3 and fails
closed; both phones should be upgraded before starting a new pairing.

## Automated evidence

`SecurePairingControlTest` covers the two-part bound, first admission, exact
replay rejection, signed-byte tamper rejection, stale-time rejection, and
version 2 decoding/duplicate suppression on Android with real Tink and Keystore
primitives. `SecureIdentityTransitionTest` separately covers fail-closed
identity replacement, preservation of trusted keys, and idempotent pending
state. `SecureReplayRepositoryTest` covers freshness, collisions, and replay
index behavior.

The remaining reviewed-protocol work is specified in
[`SECURE_PROTOCOL_MIGRATION.md`](SECURE_PROTOCOL_MIGRATION.md). This control
format must not be extended into a home-grown ratchet.
