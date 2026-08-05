# Secure Vessels protocol candidate assessment — 2026-08-04

Status: primary-source comparison updated after isolated libsignal and
Vodozemac Android spikes on 2026-08-05. No shipping wire format or runtime
behavior is changed.
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
| Signal [`libsignal`](https://github.com/signalapp/libsignal) | Closest match for asynchronous one-to-one messaging, with PQXDH and a maintained supported Java/Android artifact. The owner accepted the AGPLv3 direction for an isolated spike. | The supported JNI artifact is approximately 111 MB. A measured minimal protocol-only arm64 build is dramatically smaller, but uses internal Rust crates, exact nightly Rust, an EutherPing-owned JNI bridge, and AGPL distribution. Prekey framing, encrypted-store and two-phone evidence also remain open. |
| Matrix [`vodozemac`](https://github.com/matrix-org/vodozemac) | Apache-2.0 Rust implementation of Olm/Megolm. Its Olm API supplies a Double Ratchet and the implementation received a public [Least Authority audit](https://matrix.org/blog/2022/05/16/independent-public-audit-of-vodozemac-a-native-rust-reference-implementation-of-matrix-end-to-end-encryption/). Version 0.10.0 now rejects non-contributory X25519 input and uses strict Ed25519 verification by default. | No supported Java/Android API is documented, so EutherPing owns Rust/JNI packaging and lifecycle glue. Olm v1 retains 64-bit authentication tags and no post-quantum handshake; Olm v2 remains experimental. Use outside Matrix still needs authenticated key-distribution and external integration review. |
| [`OpenMLS`](https://github.com/openmls/openmls) | MIT-licensed Rust implementation of RFC 9420. Version 0.8 received an independent audit; the project reports remediation of all but one low-severity issue in [0.8.1](https://blog.openmls.tech/). | MLS is group state, not a drop-in one-to-one SMS ratchet. OpenMLS requires the application to supply identity authentication and a delivery service for key packages, commits, Welcome messages, and fan-out. That is a larger service and protocol architecture than the current SMS-only Phase 6 migration. There is no documented Java/Android API. |

## Decision

There is still no permissively licensed, reviewed, supported Android drop-in
that meets all Phase 6 requirements.

The owner accepted the `libsignal`/AGPLv3 direction on 2026-08-05. A pinned
`0.99.4` dependency is integrated behind a provider-neutral boundary in an
isolated, non-shipping probe. It is not approved for the production app: the
measured native size, SMS framing size, durable encrypted store, distribution
license review, and external security review remain release gates.
The direct source-build comparison removes size as an absolute libsignal
blocker, but only by replacing Signal's supported bridge with an EutherPing-owned
one. Vodozemac `0.10.0` is therefore selected for the next isolated provider
spike: its arm64 JNI probe is small, stable-Rust based, Apache-2.0 licensed, and
passes the fixed non-contributory-key case on Android. It is not production
approved. Authenticated key distribution, durable encrypted state, the Olm v1
64-bit-tag/no-PQ trade-off, two-phone interoperability, and external review
remain gates. Exact measurements and the decision are in
[`SECURE_PROVIDER_SIZE_COMPARISON-2026-08-05.md`](SECURE_PROVIDER_SIZE_COMPARISON-2026-08-05.md).

OpenMLS remains a future encrypted group candidate. It should not be bent into
the one-to-one SMS migration merely to obtain a permissive license.

## Completed isolated probes

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
