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

## Release rhythm

Each phase should land as a narrow commit, pass unit/lint/debug/release checks,
and receive focused instrumentation coverage. Carrier-sensitive phases require
physical Samsung and GrapheneOS acceptance before the following risky carrier
phase. Completed checkpoints are pushed and published as versioned APKs through
EutherOxide; unfinished phases remain documented here rather than being marketed
as complete.

## Checkpoints

- `0.8.0`: phases 1 and 2 shipped — biometric Vessel gate and provider-backed
  SMS/MMS delivery, failure details, and retry.
- `0.8.1`: first phase 3 slice — ordinary notification quick reply, persistent
  per-conversation text/image drafts (Keystore-encrypted for Vessels), bounded
  on-device message-text search, and active-conversation search. Secure
  notifications intentionally do not accept lock-screen plaintext replies.
