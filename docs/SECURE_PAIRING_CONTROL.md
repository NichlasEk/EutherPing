# Secure pairing control framing

Status: EutherPing 0.8.12 Ratchet Beta framing. This document describes both
the retained legacy identity exchange and the EP3 session bootstrap. It does
not constitute an independent cryptographic review.

## Transport and binary layout

New invitations use `EP3I:`. Their suffix is URL-safe, unpadded Base64 containing
this big-endian version 4 structure:

| Field | Size | Meaning |
| --- | ---: | --- |
| Version | 1 byte | `4` |
| Creation time | 8 bytes | Unix epoch milliseconds |
| Control ID | 16 bytes | Fresh cryptographically random identifier |
| HPKE key ID | 4 bytes | Tink primary key ID |
| HPKE value length | 2 bytes | Unsigned length |
| HPKE public value | bounded | Tink X25519 HPKE public-key protobuf value |
| Ed25519 key ID | 4 bytes | Tink primary key ID |
| Ed25519 value length | 2 bytes | Unsigned length |
| Ed25519 public value | bounded | Tink Ed25519 public-key protobuf value |
| Vodozemac publication length | 2 bytes | Exactly 164 bytes in this version |
| Vodozemac signed pre-key publication | 164 bytes | Opaque provider-owned bytes |
| Signature length | 2 bytes | Unsigned length, currently Tink-prefixed Ed25519 |
| Signature | bounded | Signature over every preceding binary field |

The complete Base64 suffix is bounded to 768 characters and can therefore use
several multipart SMS segments. No private key is transported. The displayed
EP3 safety code binds both legacy fingerprints and both ratchet-publication
hashes in stable order.

Acceptance uses `EP3A:` with a bounded binary frame. The acceptor establishes
an outbound Vodozemac session and encrypts a fresh, signed version 4 `EP3C:`
identity control as the first PRE_KEY ciphertext. Neither side can send text
until the new safety code is explicitly verified on both phones.

## Admission order

For a received version 4 invitation EutherPing:

1. applies strict Base64 and binary length bounds;
2. reconstructs and validates the exact expected Tink public-key types;
3. verifies the self-signature with the included Ed25519 public key;
4. derives the sender fingerprint from both public keysets;
5. rejects creation times over 30 days old or more than 24 hours ahead;
6. admits the `(fingerprint, kind, random control ID, capsule hash)` once through
   the bounded private replay index;
7. applies the peer-state transition; and
8. stores the remote ratchet publication as unverified peer state; and
9. only then permits Android Telephony persistence and local notification.

Invalid, stale, duplicate, and ID/hash-conflicting controls return before peer
state and Telephony history are changed. Self-signing prevents an observer from
refreshing the timestamp or control ID on somebody else's captured key bundle.
It does not prove who owns a newly introduced identity: users must still compare
the safety code out of band before `VERIFIED`, and an unexpected replacement is
quarantined with sending disabled.

## Legacy behavior

Compact binary versions 2 and 3 (`EP2I`/`EP2A`) and signed JSON version 1
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
The provider, not this framing layer, owns session establishment, encryption,
decryption, skipped keys, and ratchet state advancement.
