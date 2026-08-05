# EutherPing next-step plan — 2026-08-04

## Goal

Take EutherPing from a working SMS/MMS and Secure Ping beta to a dependable
daily messaging app. Work is split into independently releasable checkpoints so
Samsung and GrapheneOS can be tested after every risky carrier or lifecycle
change.

The two product lanes remain strict:

- `Signals` owns ordinary carrier SMS and MMS.
- `Vessels` owns authenticated end-to-end encrypted messages and attachments.
- A failed secure transfer must never fall back to plaintext SMS or MMS.

## Phase 1 — biometric Vessel gate

- Require an enrolled Android biometric before opening `Vessels` when the gate
  is enabled.
- Relock after EutherPing leaves the foreground. Do not treat a Compose screen
  change as a new authentication session.
- Show a purple/green fingerprint scan surface while Android owns the actual
  trusted biometric prompt.
- Provide a System setting and clear guidance when no biometric is enrolled.
- Keep notification previews for Secure Ping private.

This is a UI access gate, not yet a migration of every key to a
user-authentication-bound Keystore key. That stronger key policy belongs to the
reviewed protocol/security phase because it needs recovery and device-change
semantics.

Acceptance: enrolled users cannot enter Vessels after a fresh foreground entry
without authenticating; cancellation stays outside Vessels; successful auth
unlocks only the current foreground session; Signals remains usable.

## Phase 2 — dependable outbox

- Present explicit `Preparing`, `Sending`, `Sent`, `Delivered`, and `Failed`
  state for outgoing SMS and MMS when Android or the carrier provides it.
- Keep failed MMS drafts and payloads available for retry without blocking later
  messages in the same conversation.
- Classify actionable failures: mobile data unavailable, missing/default SIM,
  carrier size rejection, APN/MMSC/network failure, permission/default-role
  loss, and unknown carrier error.
- Add retry and details actions. Automatic retry must be bounded and must not
  create duplicate carrier sends.
- Preserve provider-backed truth and reconcile status after process death.

Acceptance: force-stop/relaunch retains an understandable state; one failed
large or network-blocked MMS does not freeze the composer or later messages;
Samsung and GrapheneOS physical tests cover send, fail, retry, and receive.

## Phase 3 — daily convenience

- Add direct notification reply for ordinary SMS/MMS. Secure notifications stay
  content-private and may open the authenticated Vessel instead of accepting
  plaintext from a lock-screen RemoteInput.
- Save text and pending MMS image drafts per conversation in private app data.
- Add bounded global search plus search inside the active conversation, with
  contact, number, text, and date matching.
- Add pin, archive, mark read/unread, block/unblock, and local delete actions.
- Add notification privacy choices: sender and preview, sender only, or private.

Acceptance: drafts survive process death; search never loads/decrypts complete
history on the main thread; blocked numbers are clearly visible and reversible;
notification actions update Android's provider consistently.

## Phase 4 — Samsung smoothness and diagnostics

- Add repeatable cold/warm launch, first-conversation-visible, provider-page,
  MMS-preview decode, and dropped-frame measurements using local logs only.
- Add Android Baseline Profiles for startup, inbox opening, conversation opening,
  and MMS scrolling.
- Keep bounded queries and bitmap caches; measure before increasing either.
- Add a user-invoked, locally generated diagnostics report with secrets and
  message contents excluded.

Acceptance: compare before/after timings on the target Samsung and GrapheneOS;
no analytics or remote telemetry is introduced.

## Phase 5 — SIM and group carrier messaging

- Expose explicit per-send SIM selection when multiple active subscriptions
  exist and remember the last choice per conversation.
- Show which SIM received or sent a carrier message when Android exposes it.
- Implement group MMS with correct participant/thread identity and reply-all
  behavior; never mislabel it as secure.
- Test image/caption, failures, roaming, Wi-Fi calling, and mobile-data-off on
  the actual carriers before calling the phase complete.

Acceptance: single-SIM behavior does not regress; SIM choice is never silently
changed; group replies cannot accidentally become separate one-to-one threads.

## Phase 6 — reviewed Secure Vessels protocol

- Keep Vessel plaintext and attachments separate from ordinary SMS/MMS state:
  only encrypted wire capsules may live in Telephony, sent plaintext stays in a
  Keystore-protected vault, attachment payloads stay encrypted in private app
  storage, and image preview decrypts in memory only after explicit user action.
- Add replay tracking and reject duplicate or stale authenticated frames.
- Select a reviewed asynchronous messaging session protocol/library that
  provides forward secrecy and post-compromise security. Do not design a custom
  Double Ratchet primitive in EutherPing.
- Specify identity changes, skipped-message keys, corrupted state, reinstall,
  device replacement, backup, and safety-code re-verification before migration.
- Publish protocol test vectors and malformed-input/interoperability tests, then
  obtain an external security review before removing the `Secure Beta` label.

Acceptance: an implementation and migration design can be independently
reviewed; legacy conversations fail safely; no silent plaintext fallback exists.

Design status: [`SECURE_PROTOCOL_MIGRATION.md`](SECURE_PROTOCOL_MIGRATION.md)
selects Signal's current `libsignal` as the leading one-to-one implementation
and MLS as a later group candidate. The owner accepted the libsignal/AGPLv3
direction on 2026-08-05. A provider-neutral API and pinned `0.99.4` adapter now
exist only in isolated, non-shipping modules. The spike proves Alice/Bob
ratcheting, out-of-order delivery, replay rejection, reload, atomic rollback,
and Android JNI loading, but exposes blocking SMS and APK-size costs. Details
are in [`LIBSIGNAL_SPIKE-2026-08-05.md`](LIBSIGNAL_SPIKE-2026-08-05.md).
The current candidate comparison finds no permissively licensed, reviewed,
supported Android drop-in: Vodozemac requires an explicit upstream X25519 fix,
outside-Matrix integration review, and owned Rust/JNI layer; OpenMLS requires a
larger authentication/delivery architecture. The decision and reviewer-ready
evidence scope are captured in
[`SECURE_PROTOCOL_CANDIDATE_ASSESSMENT.md`](SECURE_PROTOCOL_CANDIDATE_ASSESSMENT.md)
and [`SECURE_EXTERNAL_REVIEW_PACKET.md`](SECURE_EXTERNAL_REVIEW_PACKET.md).

## Release rhythm

Each phase should land as a narrow commit, pass unit/lint/debug/release checks,
and receive focused instrumentation coverage. Carrier-sensitive phases require
physical Samsung and GrapheneOS acceptance before the following risky carrier
phase. Completed checkpoints are pushed and published as versioned APKs through
EutherOxide; unfinished phases remain documented here rather than being marketed
as complete.
The current requirement-by-requirement evidence and explicitly missing physical
or protocol gates are maintained in
[`ROADMAP_COMPLETION_AUDIT-2026-08-04.md`](ROADMAP_COMPLETION_AUDIT-2026-08-04.md).

## Checkpoints

- `0.8.0`: phases 1 and 2 shipped — biometric Vessel gate and provider-backed
  SMS/MMS delivery, failure details, and retry.
- `0.8.1`: first phase 3 slice — ordinary notification quick reply, persistent
  per-conversation text/image drafts (Keystore-encrypted for Vessels), bounded
  on-device message-text search, and active-conversation search. Secure
  notifications intentionally do not accept lock-screen plaintext replies.
- `0.8.2`: phase 3 complete — global search also matches bounded MMS captions,
  contacts, numbers, and local date formats; long-press conversation controls
  pin, archive, mark read/unread, and confirm Android system block/unblock.
  Archived rows are explicitly recoverable, and System offers sender+preview,
  sender-only, and private notification modes. Existing per-message copy,
  forward, and local-delete actions remain available inside conversations.
- `0.8.3`: phase 4 implementation checkpoint — startup and conversation journeys
  ship with an Android Baseline Profile; local diagnostics measure first frame,
  provider pages, MMS preview decode/cache, and rendered-frame jank. Reports are
  bounded, manually exported, and exclude message content and identifiers. The
  Samsung and GrapheneOS before/after comparison remains a physical-device
  acceptance step and is not claimed complete by the automated test gate.
- `0.8.4`: phase 5 implementation checkpoint — active SIMs are explicit in the
  composer, remembered per participant-based thread, preserved through retry,
  and shown on messages when Android supplies a subscription ID. New and
  incoming group MMS retain their complete participant set and reply-all stays
  in the same Android thread. Emulator provider tests cover group identity; the
  documented Samsung/GrapheneOS carrier, roaming, Wi-Fi-calling and
  mobile-data-off matrix remains the physical acceptance gate.
- `0.8.5`: first phase 6 hardening checkpoint — Secure attachment download stores
  ciphertext only; image preview is an explicit action and performs bounded,
  authenticated in-memory decryption with no plaintext preview file. Temporary
  open/save copies are private and crash leftovers are cleared at startup.
  Instrumentation covers memory preview, tamper rejection and cleanup. Replay
  protection and migration to a reviewed session protocol remain unfinished.
- `0.8.6`: authenticated Secure messages and attachment offers are admitted once
  through a bounded private replay index. Exact duplicate frames, conflicting
  ciphertext under an accepted ID, frames older than 30 days, and frames over 24
  hours ahead are rejected before Telephony persistence. Pairing controls and
  the reviewed ratcheting-protocol migration remain unfinished.
- `0.8.7`: unexpected identity replacement is fail-closed. A different pairing
  identity can no longer overwrite an already verified peer: the trusted keys
  remain stored separately, Secure sending locks, and the pending fingerprint
  must be explicitly rejected or promoted into a fresh unverified safety-code
  flow. Duplicate and third replacement controls are idempotent and cannot
  rotate either saved identity. Timestamped pairing-control framing and the
  reviewed ratcheting-protocol migration remain unfinished.
- `0.8.8`: new pairing invitations and acceptances self-sign their complete
  public-key bundle, timestamp, and random 128-bit control ID. They are admitted
  once through the bounded replay index and rejected when tampered, duplicated,
  older than 30 days, or over 24 hours in the future before peer state or
  Telephony persistence. The signed control is bounded to two SMS parts. Legacy
  v2/v1 controls remain readable with exact-duplicate suppression but cannot
  retroactively prove freshness. The reviewed ratcheting-protocol migration
  remains unfinished.
- Post-`0.8.8` documentation checkpoint: the Secure storage claim is reconciled
  with the implementation (ciphertext-only attachment storage and explicit
  in-memory image preview), current permissive protocol alternatives are
  assessed against 2026 security evidence, and the external-review scope and
  finding log are ready. No crypto dependency, license, APK, or runtime behavior
  changes in this checkpoint.
- `0.8.9`: Vessel draft presence metadata moves out of the ordinary carrier
  draft store into a dedicated Secure index. Vessel text remains ciphertext in
  the Android-Keystore-protected Secure vault, legacy hashed presence flags
  migrate once and are deleted, and ordinary draft clearing no longer touches
  Secure storage. Instrumentation inspects all three stores and proves the
  plaintext marker enters none of their persisted values.
- `0.8.10`: SMS, MMS, and private Vessel alerts share a recognizable
  short-short-long EutherPing vibration on a versioned Android notification
  channel. System and privacy links to the channel settings; Android silent,
  vibration, Do Not Disturb, device, and user overrides remain authoritative.
  Instrumentation verifies the shipped channel ID and pattern. Physical feel
  and suppression behavior remain to be accepted on Samsung and GrapheneOS.
- Post-`0.8.10` protocol checkpoint: provider-neutral `crypto-api`, isolated
  `crypto-libsignal`, and a non-shipping Android probe pin libsignal `0.99.4`.
  No app dependency, EP3 traffic, existing-Vessel migration, APK release, or
  server deployment is introduced. SMS/prekey delivery, native size, encrypted
  durable state, licensing, physical-device interoperability, and external
  review remain production gates.
