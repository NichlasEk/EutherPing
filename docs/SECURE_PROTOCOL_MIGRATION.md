# Secure Vessels reviewed-protocol migration

Status: design checkpoint for independent review. No new protocol dependency is
enabled by this document. EutherPing remains **Secure Beta**.

The legacy identity-control boundary, including the signed fresh version 3
pairing capsule and its fail-closed replacement policy, is documented in
[`SECURE_PAIRING_CONTROL.md`](SECURE_PAIRING_CONTROL.md). It hardens migration
entry but is not a substitute for the reviewed session protocol below.

## Decision

The leading implementation for one-to-one Secure Vessels is Signal's current
[`libsignal`](https://github.com/signalapp/libsignal), using its Java/Android
packages and protocol state machine rather than implementing Double Ratchet in
EutherPing. Signal documents that this implementation includes Double Ratchet,
is used by its official clients, and publishes Android ABIs and Java artifacts.

This selection is blocked before dependency integration for two explicit
reasons:

1. `libsignal` is GNU AGPLv3 while EutherPing currently has no repository license.
   Adding it is a product licensing decision, not a mechanical build change.
2. Signal states that use outside Signal is unsupported and that APIs may change
   without notice. EutherPing must pin an exact version and own compatibility,
   update, and security-response work.

No `libsignal` binary or source may be added until the owner accepts the license
and maintenance implications or selects a different reviewed implementation.
The current permissive-alternative comparison and its additional security gates
are recorded in
[`SECURE_PROTOCOL_CANDIDATE_ASSESSMENT.md`](SECURE_PROTOCOL_CANDIDATE_ASSESSMENT.md).

The protocol rationale is the official
[`Double Ratchet` specification](https://signal.org/docs/specifications/doubleratchet/):
message keys advance on every message and new Diffie-Hellman values provide
forward security and recovery after a later uncompromised ratchet step. Session
setup should use the library's supported asynchronous prekey flow rather than an
EutherPing-designed key agreement. Signal's current
[`PQXDH` specification](https://signal.org/docs/specifications/pqxdh/) describes
the asynchronous prekey model and its post-quantum forward-secrecy goal.

IETF [`MLS (RFC 9420)`](https://www.rfc-editor.org/rfc/rfc9420.html) remains the
candidate for a future encrypted group-Vessels phase. MLS supports groups from
two members upward with forward secrecy and post-compromise security, but it
assumes authentication and delivery services and has group epoch/commit state.
Introducing those services merely to replace today's one-to-one SMS capsules is
larger than this migration and would couple it to the still-unimplemented Secure
group feature.

## Non-negotiable boundaries

- The library owns session establishment, message encryption/decryption,
  ratchet advancement, skipped-key handling, and protocol serialization.
- EutherPing owns only transport envelopes, UI, local storage adapters,
  identity-change policy, and migration orchestration.
- No custom Double Ratchet, KDF chain, prekey derivation, or skipped-key scheme.
- No silent fallback to `EP1M`, ordinary SMS, or carrier MMS after an upgraded
  session has been selected.
- Existing `EP1M`/`EP1F` history remains readable but is never marketed as
  forward-secure.

## Proposed wire transition

New envelopes use an `EP3` family and an unambiguous binary payload encoded for
SMS transport. Exact bytes and limits are frozen only after the dependency spike
can measure current library output.

- `EP3P`: versioned prekey/session-establishment payload.
- `EP3M`: ratcheted one-to-one application message.
- `EP3F`: ratcheted manifest for an attachment that remains encrypted at rest
  and in direct Wi-Fi/Bluetooth transit.
- `EP3R`: explicit reset request carrying no plaintext and requiring user-visible
  re-verification before a replacement identity is trusted.

Every envelope binds protocol version, sender/recipient identity, EutherPing
conversation ID, payload kind, and transport-fragment metadata as authenticated
associated data where the selected library API permits. Multipart assembly is
bounded before parsing. Unknown versions, malformed frames, downgrade attempts,
and duplicate protocol messages fail closed.

SMS cost and reliability are release gates. PQXDH prekey material and initial
messages may be much larger than current pairing capsules. The spike must record
serialized sizes and multipart counts on real carriers before EP3 can ship. If
the supported asynchronous handshake is not practical over SMS, EutherPing must
add a narrowly scoped prekey delivery service or choose another reviewed
protocol; it must not simplify PQXDH or invent a smaller handshake.

## Private state layout

Protocol state lives under a new `secure_sessions_v3` private namespace,
separate from Android Telephony, ordinary conversation caches, current Tink
keysets, and legacy `EP1` plaintext vault entries.

- Identity, session, signed-prekey, one-time-prekey, and post-quantum-prekey
  records are serialized only through the selected library.
- Serialized secret state is envelope-encrypted under a dedicated Android
  Keystore key before atomic persistence.
- A per-peer transaction writes ratchet state before a received plaintext is
  exposed. Failure to commit keeps the message unavailable so a crash cannot
  reuse a consumed message key.
- Backup and device transfer remain disabled. No protocol secret enters logs,
  diagnostics, notifications, the conversation index, or Telephony.
- Telephony contains only the EP3 ciphertext envelope and unavoidable carrier
  metadata.

## Identity and recovery behavior

| Event | Required behavior |
| --- | --- |
| First EP3 upgrade | Create a fresh library identity and require a new safety-code verification; do not inherit trust silently from EP1. |
| Remote identity changes | Block sending and plaintext display for the new identity; retain old history; require explicit re-verification. |
| Local reinstall/data loss | Create a new identity, mark old sessions unavailable, and require every Vessel to pair again. |
| Corrupt session state | Quarantine the session and ciphertext; never auto-reset or fall back; offer a visible verified reset. |
| Out-of-order messages | Use only the library's skipped-message-key mechanism and its documented bounds. |
| Message beyond skipped-key bound | Keep ciphertext with an actionable failure; do not advance state speculatively. |
| Device replacement | Treat as a new identity. There is no secure-state transfer in this phase. |
| Backup request | Unsupported until a separately reviewed, user-authenticated encrypted export design exists. |
| Simultaneous session initiation | Resolve through library behavior plus deterministic app state; cover with two-device tests before release. |

## Migration sequence

1. **License decision and dependency spike.** Record the accepted license,
   exact artifact version/checksums, supported Android ABIs, release size,
   min-SDK behavior, public APIs, and serialized prekey/message sizes. No UI
   exposure.
2. **Encrypted protocol store.** Implement atomic Keystore-envelope adapters and
   crash/reload tests with synthetic identities only.
3. **EP3 interoperability harness.** Run Alice and Bob as independent stores and
   processes. Freeze app-level envelope fixtures only after library output and
   state transitions pass.
4. **Opt-in paired-device beta.** Require a new verification and show a distinct
   `RATCHET BETA` state. Keep EP1 history read-only. Never auto-upgrade one phone
   while the peer cannot understand EP3.
5. **Migration release.** Enable new sessions only after Samsung and GrapheneOS
   carrier testing, malformed-input tests, independent review, and a rollback
   plan that preserves ciphertext without downgrade.
6. **Remove Secure Beta only after external review.** Review must cover storage,
   transport envelopes, state transactions, identity UX, notification privacy,
   attachment key binding, and dependency update policy—not just primitives.
   The reviewer evidence and finding-closure template are in
   [`SECURE_EXTERNAL_REVIEW_PACKET.md`](SECURE_EXTERNAL_REVIEW_PACKET.md).

## Required tests and evidence

- Official-library tests and pinned dependency checksums.
- Independent Alice/Bob initial-session and bidirectional known-answer fixtures.
- State serialization/reload after every send and receive transition.
- Out-of-order delivery, skipped keys, duplicate delivery, delayed initial
  message, simultaneous initiation, and dropped SMS fragments.
- Old/new identity substitution, unverified reset, downgrade, wrong recipient,
  malformed length/version/type, bit flips, truncation, and random input.
- Crash injection before and after atomic state commit.
- Reinstall, corrupt state, unavailable Keystore key, and app upgrade from every
  retained EP1 checkpoint.
- ABI smoke tests on arm64 Samsung, arm64 GrapheneOS, and x86_64 emulator.
- Release APK size, method count, startup/jank comparison, multipart SMS count,
  and real-carrier cost/reliability matrix.
- An external review report and a closed remediation list.

## Completion gate

Phase 6 is not complete when this design is merged. It completes only when a
license-compatible reviewed library is integrated, EP3 interoperability and
failure tests pass, two physical phones complete a newly verified session, the
legacy path cannot be selected as a downgrade, and external review findings are
resolved. Until then the current HPKE/Ed25519 framing plus replay filter remains
an explicitly limited beta.
