# Secure protocol provider architecture

Status: non-shipping architecture checkpoint, 2026-08-05.

```
EutherPing app
    |
    +-- crypto-api: SecureProtocolProvider boundary
            |
            +-- crypto-libsignal: libsignal 0.99.4 (AGPL-3.0-only)
            +-- crypto-vodozemac: Vodozemac 0.10.0 probe (Apache-2.0)
            +-- legacy Tink reader/migration adapter (future)
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

`crypto-vodozemac` is the selected next provider spike after the measured
size/license comparison. It is opt-in at Gradle settings level, consists of a
small Kotlin/JNI boundary and a pinned Rust crate, and is also non-shipping.
It does not yet implement `SecureProtocolProvider`; advancing it requires the
production gates in
[`SECURE_PROVIDER_SIZE_COMPARISON-2026-08-05.md`](SECURE_PROVIDER_SIZE_COMPARISON-2026-08-05.md).

## State boundary

Every protocol transition runs inside one per-local-identity copy-on-write
transaction. A production repository must envelope-encrypt every opaque record
under a dedicated Android Keystore key and atomically commit ratchet state
before exposing received plaintext. Exceptions commit nothing. Protocol state,
legacy Tink state, Telephony data, ordinary caches, Secure plaintext, and
attachment ciphertext remain separate namespaces.

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
