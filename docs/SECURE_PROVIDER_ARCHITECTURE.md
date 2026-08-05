# Secure protocol provider architecture

Status: always-included local runtime checkpoint, 2026-08-05.

```
EutherPing app
    |
    +-- crypto-api: SecureProtocolProvider boundary
    |       |
    |       +-- crypto-libsignal: libsignal 0.99.4 (AGPL-3.0-only)
    |       +-- crypto-vodozemac: Vodozemac 0.10.0 probe (Apache-2.0)
    |       +-- legacy Tink reader/migration adapter (future)
    |
    +-- crypto-storage-android: Keystore + AtomicFile secure_sessions_v3
```

`crypto-api` contains no cryptographic primitive. It identifies the provider
and wire family, supplies state transactions, and exposes only session setup,
encrypt, and decrypt operations. Each implementation owns its primitives,
serialized records, skipped keys, replay behavior, and ratchet transitions.
Provider IDs are authenticated protocol input; an unknown or mismatched provider
fails before payload parsing. No provider may silently downgrade to another
provider, EP1, ordinary SMS, or MMS.

`crypto-libsignal` is an AGPL adapter and currently reports wire family
`EP3-LS-PROBE`, version `0.99.4`, and `productionReady=false`. The probe framing
is measurement scaffolding, not an interoperable EutherPing protocol. The
shipping `app` module deliberately has no dependency on it.

`crypto-vodozemac` implements `SecureProtocolProvider` as the always-included
local runtime from EutherPing 0.8.11. Kotlin sees only opaque account/session
bytes through a bounded JNI frame; every key generation, session setup,
encrypt, and decrypt operation executes inside one `ProtocolStateRepository`
transaction. Signed
pre-key frames bind the Olm Curve25519 identity to the Ed25519 signing identity,
and provider/kind/identity mismatch fails before state commits. Provider tests
cover both copy-on-write memory repositories and real Keystore-backed Android
repositories. App startup now prepares and reuses one signed publication, while
carrier use still requires the production gates in
[`SECURE_PROVIDER_SIZE_COMPARISON-2026-08-05.md`](SECURE_PROVIDER_SIZE_COMPARISON-2026-08-05.md).

## State boundary

Every protocol transition runs inside one per-local-identity copy-on-write
transaction. `crypto-storage-android` now implements this contract with
AES-256-GCM under Android Keystore, namespace-bound associated data, bounded
records, `AtomicFile`, and `fsync`. It stores files only in
`noBackupFilesDir/secure_sessions_v3`; exceptions commit nothing, and received
plaintext is returned only after the encrypted state commit succeeds. Protocol
state, legacy Tink state, Telephony data, ordinary caches, Secure plaintext,
and attachment ciphertext remain separate namespaces. Detailed evidence and
remaining physical-device gates are in
[`SECURE_SESSIONS_V3_STORAGE-2026-08-05.md`](SECURE_SESSIONS_V3_STORAGE-2026-08-05.md).

## Distribution boundary

The interface does not weaken or remove an implementation's license. Any build
containing `crypto-libsignal` and its native library must be distributed under
terms compatible with AGPLv3 and include the required source and notices. A
future Play flavor must either satisfy that reviewed distribution plan or omit
the module and use a separately reviewed provider. The probe APK is not a
release artifact.

Vodozemac's Apache-2.0 license is materially simpler for a future Play flavor,
but does not waive protocol, key-distribution, native-memory, or external-review
requirements.

Production activation additionally requires a frozen EP3 specification,
practical authenticated prekey delivery, encrypted durable storage, downgrade
and identity-change fixtures, Samsung/GrapheneOS interoperability, dependency
update ownership, and external review.
