# Secure Vessels protocol candidate assessment — 2026-08-04

Status: primary-source comparison updated after the 2026-08-05 isolated
libsignal spike. No shipping wire format or runtime behavior is changed.
EutherPing remains **Secure Beta**.

## Required fit

The selected implementation must provide a reviewed asynchronous one-to-one
session with forward secrecy and post-compromise recovery. It must be practical
on Android 9+, tolerate delayed and out-of-order carrier delivery, preserve the
strict Vessels/Signals storage boundary, and have maintainable licensing and a
credible security-update path. EutherPing must not implement its own ratchet.

## Current candidates

| Candidate | Strength | Blocking issue for EutherPing today |
| --- | --- | --- |
| Signal [`libsignal`](https://github.com/signalapp/libsignal) | Closest match for asynchronous one-to-one messaging; maintained protocol implementation with Java/Android artifacts. The owner accepted the AGPLv3 direction for the isolated spike. | Production remains blocked by the approximately 111 MB arm64 JNI cost, impractical SMS prekey framing, encrypted-store and two-phone evidence, distribution review, and unsupported/API-change maintenance risk. |
| Matrix [`vodozemac`](https://github.com/matrix-org/vodozemac) | Apache-2.0 Rust implementation of Olm/Megolm. Its Olm API supplies a Double Ratchet and the implementation received a public [Least Authority audit](https://matrix.org/blog/2022/05/16/independent-public-audit-of-vodozemac-a-native-rust-reference-implementation-of-matrix-end-to-end-encryption/). | No supported Java/Android API is documented, so EutherPing would own Rust/JNI packaging and lifecycle glue. More importantly, Matrix [confirmed in February 2026](https://matrix.org/blog/2026/02/18/analysis-of-reported-issues-in-vodozemac/) that Olm 3DH did not reject non-contributory X25519 results and specifically noted risk must be considered outside Matrix's authenticated key-distribution context. The upstream source/release must explicitly contain the promised defence-in-depth fix before a spike. Olm v1 also retains 64-bit authentication tags. |
| [`OpenMLS`](https://github.com/openmls/openmls) | MIT-licensed Rust implementation of RFC 9420. Version 0.8 received an independent audit; the project reports remediation of all but one low-severity issue in [0.8.1](https://blog.openmls.tech/). | MLS is group state, not a drop-in one-to-one SMS ratchet. OpenMLS requires the application to supply identity authentication and a delivery service for key packages, commits, Welcome messages, and fan-out. That is a larger service and protocol architecture than the current SMS-only Phase 6 migration. There is no documented Java/Android API. |

## Decision

There is currently no permissively licensed, reviewed, supported Android
drop-in that meets all Phase 6 requirements.

The owner accepted the `libsignal`/AGPLv3 direction on 2026-08-05. A pinned
`0.99.4` dependency is integrated behind a provider-neutral boundary in an
isolated, non-shipping probe. It is not approved for the production app: the
measured native size, SMS framing size, durable encrypted store, distribution
license review, and external security review remain release gates.
`vodozemac` is the strongest permissive alternative, but it is not approved for
an EutherPing spike until all of these are true:

1. an upstream release explicitly includes the non-contributory X25519 defence;
2. its exact Olm configuration and authenticated key-distribution assumptions
   are reviewed against EutherPing's signed pairing and safety-code flow;
3. Rust/JNI ABI, min-SDK, crash, state-persistence, and update ownership are
   accepted;
4. the 64-bit authentication-tag trade-off is explicitly reviewed and accepted;
5. an external reviewer agrees the integration is suitable outside Matrix.

OpenMLS remains a future encrypted group candidate. It should not be bent into
the one-to-one SMS migration merely to obtain a permissive license.

## Next authorized spike

The authorized non-UI libsignal slice now covers:

- pin the exact source artifact and checksum;
- produce an SBOM and packaged license notices;
- measure ABI/APK impact and serialized handshake/message sizes;
- run two isolated Alice/Bob stores through initial, bidirectional,
  out-of-order, replay, crash/reload, and identity-change cases;
- record carrier multipart counts without sending production Secure traffic.

The results are in
[`LIBSIGNAL_SPIKE-2026-08-05.md`](LIBSIGNAL_SPIKE-2026-08-05.md). No existing
Vessel is automatically upgraded and no plaintext fallback is permitted.
