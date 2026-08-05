# EutherPing roadmap completion audit — 2026-08-04

This audit maps the acceptance requirements in
[`NEXT-STEP-PLAN-2026-08-04.md`](NEXT-STEP-PLAN-2026-08-04.md) to current
authoritative evidence. A green automated check proves only the behavior it
exercises; emulator evidence is not treated as carrier or physical-biometric
evidence.

## Current release gate

| Evidence | Current result |
| --- | --- |
| Debug unit tests | 15/15 pass for 0.8.10 |
| Android lint | Pass for 0.8.10 |
| Debug APK | Builds and APK Signature Scheme v2 verifies for 0.8.10 |
| Minified release APK | Builds successfully for 0.8.10 with R8/resource shrinking |
| Android instrumentation | 35/35 full-suite pass on Android 11 x86_64 emulator, including notification-channel ID and vibration pattern |
| Installed manifest | Built APK reports version code 38, version name 0.8.10; physical install verification pending |
| Physical Samsung | Not currently connected; acceptance evidence missing |
| Physical GrapheneOS | Not currently connected; acceptance evidence missing |

## Requirement audit

| Phase | Implementation evidence | Acceptance status |
| --- | --- | --- |
| 1 — biometric Vessel gate | `VesselBiometricGate`, default-on setting, foreground relock state, private Secure notification path, scanner UI, and `VesselBiometricGateTest`. | Implemented and emulator-tested. Real enrolled fingerprint, cancellation, background/re-entry, and Signals-unaffected checks remain required on both physical phones. |
| 2 — dependable outbox | Provider-backed send states, multipart aggregation, failure classification/details, retry actions, MMS draft retention, subscription preservation, and `SmsDeliveryStatusTest`/MMS instrumentation. | Implemented and emulator-tested. Carrier delivery, APN/mobile-data failure, force-stop reconciliation, and failed-MMS-does-not-block checks remain required on Samsung and GrapheneOS. |
| 3 — daily convenience | Ordinary RemoteInput reply, per-conversation drafts, bounded global/in-thread search, pin/archive/read/block controls, local delete, forwarding, links, media open/save, notification privacy modes, and a versioned short-short-long incoming-message vibration channel with a direct Android settings link. Repository and instrumentation tests cover draft persistence, provider controls, and channel defaults. | Implementation present. Lock-screen reply, document picker, browser intent, process-death draft recovery, block/unblock, and vibration feel on both physical phones need UX acceptance. Secure notifications deliberately reject plaintext quick reply. |
| 4 — Samsung smoothness | Bounded provider paging, lazy MMS decode/cache, lifecycle refresh, Baseline Profile journeys, local timing/jank diagnostics, and secret-exclusion tests. | Implementation present. The plan's before/after cold/warm timings and scroll-jank comparison on the target Samsung and GrapheneOS are missing; smoothness is not proven by the emulator. |
| 5 — multi-SIM/group MMS | Explicit per-conversation SIM selection, unavailable-SIM fail-closed behavior, subscription labels, participant-based group identity/reply-all, and subscription/group provider tests. | Implementation present. Real dual-SIM choice, group send/receive, roaming, Wi-Fi calling, mobile-data-off, failure/retry, and carrier interoperability matrix are missing. |
| 6 — Secure storage and framing hardening | Dedicated keyset/peer/vault/replay/draft-index stores; encrypted attachment directories; explicit in-memory image preview; temporary open/save cleanup; signed fresh pairing; replay/stale/conflict rejection; fail-closed identity quarantine. Instrumentation covers these boundaries, including legacy draft-index migration. | Current beta hardening is implemented and tested. Carrier metadata and encrypted capsules necessarily remain in Telephony; no Secure plaintext enters ordinary message/draft/index storage. |
| 6 — reviewed ratcheting protocol | Migration design, candidate assessment, provider-neutral API, isolated libsignal adapter, measured comparison, always-included Vodozemac 0.10.0 provider, signed EP3I pre-key invitations, encrypted EP3A acceptance, ratcheted EP3M text, no-downgrade peer state, safety-code binding, encrypted `secure_sessions_v3`, app-start identity initialization, persistent Alice/Bob Android tests, threat/recovery cases, and external-review packet. | **Paired-device beta in 0.8.12; incomplete release gate.** Physical force-stop/reboot and Samsung/GrapheneOS carrier interoperability, atomic receive-state/plaintext commit, EP3F attachments, verified reset UX, distribution review, external report, and closed remediation list remain. Ratchet Beta must remain. |

## Remaining completion path

1. Publish 0.8.10 only after its release APK, checksum, public download, server
   route, and clean pushed commits are verified.
2. Run the documented Samsung/GrapheneOS performance, biometric, outbox,
   multi-SIM, group-MMS, and Secure attachment matrices with both phones
   connected and their intended SIM/carrier conditions available.
3. Run the 0.8.12 opt-in EP3 beta on Samsung and GrapheneOS, including fresh
   pairing, explicit legacy upgrade, multipart carrier delivery, alternating and
   out-of-order messages, force-stop/reboot, duplicate delivery, and downgrade
   refusal. Preserve exact failure evidence.
4. Commission the external review described in
   [`SECURE_EXTERNAL_REVIEW_PACKET.md`](SECURE_EXTERNAL_REVIEW_PACKET.md), fix
   every finding, and obtain reviewer retest sign-off.

The complete roadmap goal must remain active until every missing physical and
protocol item above has direct evidence.
