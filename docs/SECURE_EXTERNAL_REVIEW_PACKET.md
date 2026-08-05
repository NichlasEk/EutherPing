# Secure Vessels external review packet

Status: reviewer-ready scope and evidence index for EutherPing 0.8.9. This is
not an external security review and does not close Phase 6.

## Review claim boundary

The current product is **Secure Beta**. It uses reviewed primitives through
Google Tink, but its HPKE/Ed25519 application framing is not a reviewed
ratcheting session protocol and provides neither forward secrecy nor
post-compromise security. A reviewer must assess the complete app protocol and
state handling, not infer safety from the primitives alone.

## In-scope documents

- [`SECURE_STORAGE.md`](SECURE_STORAGE.md): data locations and plaintext
  lifecycle.
- [`SECURE_PAIRING_CONTROL.md`](SECURE_PAIRING_CONTROL.md): signed fresh pairing,
  replay admission, and identity replacement boundary.
- [`SECURE_ATTACHMENTS_AND_MMS.md`](SECURE_ATTACHMENTS_AND_MMS.md): attachment
  envelope, Wi-Fi/Bluetooth ciphertext transport, and separation from MMS.
- [`SECURE_PROTOCOL_MIGRATION.md`](SECURE_PROTOCOL_MIGRATION.md): proposed EP3
  state, migration, and release gates.
- [`SECURE_PROTOCOL_CANDIDATE_ASSESSMENT.md`](SECURE_PROTOCOL_CANDIDATE_ASSESSMENT.md):
  current dependency and license comparison.
- [`SECURE_PROVIDER_SIZE_COMPARISON-2026-08-05.md`](SECURE_PROVIDER_SIZE_COMPARISON-2026-08-05.md):
  measured JNI/provider checkpoint, license choice, and remaining gates.
- [`SECURE_SESSIONS_V3_STORAGE-2026-08-05.md`](SECURE_SESSIONS_V3_STORAGE-2026-08-05.md):
  real Keystore/AtomicFile state implementation and failure evidence.
- [`PRIVACY.md`](PRIVACY.md): user-facing storage and disclosure claims.

## Required code review areas

1. Identity generation, Keystore wrapping, safety-code binding, trusted versus
   pending peer state, reinstall, and explicit replacement behavior.
2. Pairing-control signature domain, timestamp bounds, random IDs, canonical
   number matching, multipart assembly, replay index, and fail-before-Telephony
   ordering.
3. Message and attachment signature/HPKE associated data, sender/recipient
   binding, freshness, duplicate/conflict behavior, and notification privacy.
4. Attachment manifest binding, AES-GCM streaming, ciphertext and plaintext
   hashes/sizes, direct Wi-Fi/Bluetooth authorization, expiry, path handling,
   and temporary plaintext cleanup.
5. Sent-plaintext vault and draft encryption, conversation-index redaction,
   logs/diagnostics, lifecycle memory release, backup exclusion, and Android
   component/export rules.
6. The selected reviewed ratchet's state transaction boundary, skipped-key
   limits, simultaneous initiation, downgrade resistance, update policy, and
   migration from readable legacy history.

## Existing automated evidence

- Real Android Keystore/Tink pairing, identity transition, and replacement
  quarantine tests.
- Signed pairing acceptance, tamper, duplicate, stale, future, malformed,
  length-bound, and legacy compatibility tests.
- Authenticated message/offer replay, ID conflict, and freshness tests.
- Secure attachment in-memory preview, ciphertext tamper rejection, and
  transient cache cleanup tests.
- Conversation-index tests proving Secure plaintext is replaced before cache
  persistence.
- The isolated Vodozemac provider's signed key publication, signed initial
  identity binding, bidirectional/out-of-order ratchet, opaque reload, replay
  rejection, identity-change rejection, and copy-on-write rollback tests on
  Android 11. This module is not linked into the shipping app.
- Real Android Keystore/no-backup persistence, atomic rollback, simulated commit
  failure, ciphertext tamper, cross-identity swap, repository re-instantiation,
  and persistent Alice/Bob continuation tests for `secure_sessions_v3`.
- Full 0.8.9 unit, lint, debug/release, and instrumentation gate, including
  ordinary/Secure draft namespace separation and legacy metadata migration.

## Evidence still required

- Independent review report with version/commit, threat model, findings,
  severity, and reviewer identity.
- Finding-by-finding remediation commits and reviewer retest sign-off.
- Two physical arm64 devices: intended Samsung and GrapheneOS models.
- New pairing, verified safety code, bidirectional traffic, delayed/out-of-order
  delivery, duplicate carrier delivery, force-stop/reboot, identity replacement,
  and lost-state behavior.
- Direct Wi-Fi and paired-Bluetooth attachment transfer, corruption, expiry,
  cancellation, storage inspection before/after preview, and temporary-file
  cleanup after open/save/crash.
- Selected ratchet dependency version/checksum/SBOM/license, ABI coverage,
  serialized size/cost matrix, and upstream security-response policy.

## Reviewer finding log

| ID | Severity | Finding | Fix commit | Reviewer retest | Status |
| --- | --- | --- | --- | --- | --- |
| _pending_ |  |  |  |  | Open |

The `Secure Beta` label may be removed only after the implementation dependency
is approved, the physical evidence exists, and every review finding is closed.
