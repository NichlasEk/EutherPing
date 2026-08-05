# Secure sessions v3 Android storage — 2026-08-05

## Outcome

`crypto-storage-android` is a real Android implementation of
`ProtocolStateRepository`, not an in-memory format mock. From EutherPing 0.8.11
the shipping `app` always includes it and prepares the local Vodozemac account
and signed pre-key publication during process startup. EutherPing 0.8.13 uses
that state for explicitly paired and re-verified EP3 text and attachment-manifest sessions.

## Storage boundary

Each local Secure identity receives one file below:

```text
noBackupFilesDir/secure_sessions_v3/<SHA-256 namespace>.state
```

The filename exposes neither phone number nor protocol address. The complete
copy-on-write record set is encoded with strict version, count, key, value, and
total-size limits. It is encrypted with AES-256-GCM under the Android Keystore
alias `eutherping_secure_sessions_v3_master`. Keystore generates every GCM
nonce. Authenticated associated data binds schema version and namespace hash,
so moving a valid encrypted file to another identity fails authentication.

`AtomicFile` replaces the encrypted container only after write, flush, and
`fsync`. A protocol exception writes nothing. A write/commit exception invokes
`failWrite`, preserving the previous authenticated container. In-memory working
record values and temporary plaintext/ciphertext buffers are zeroed when the
transaction ends where the platform representation permits it.

State is excluded from Android Auto Backup through `noBackupFilesDir`. Loss of
the Keystore key, modified ciphertext, an unsupported version, a cross-identity
file swap, duplicate record keys, or a malformed length fails closed. Nothing
automatically deletes or recreates corrupt state. Explicit deletion is named
`deleteAllStateForVerifiedReset` so normal error handling cannot masquerade as
a reset.

The standalone release AAR is 16,940 bytes with SHA-256
`ca1a28784eaf0d26ab1a806eeff60633fa6e991ee07c86f5993b5d2ff5a13f8a`.
It adds no native binary or third-party runtime dependency.

## Real Android evidence

Three storage instrumentation tests use the Android 11 emulator's real
`AndroidKeyStore` and filesystem:

- encrypt, persist, instantiate a new repository, and recover the same opaque
  records while proving a unique secret marker is absent from the file;
- prove both a thrown protocol transition and a simulated final commit failure
  preserve the exact last committed ciphertext;
- reject a one-bit ciphertext modification and a valid file moved between two
  namespaces, then recover after restoring the original bytes.

Three Vodozemac provider instrumentation tests include a persistent Alice/Bob
case. Both identities use separate `SecureSessionStateRepository` instances,
establish and decrypt a real Olm session, discard engine/repository instances,
reopen from Keystore-protected files, and continue bidirectional ratcheted
traffic. The other cases retain out-of-order, replay, signature, identity,
silent-reset, provider mismatch, and copy-on-write rollback coverage.

An app-level instrumentation test starts the same always-on runtime used by the
shipping `Application`, proves the encrypted state file is created without
exposing the pre-key byte sequence, clears the process cache, and reloads the
exact same publication from that authenticated container instead of rotating
identity at restart.

## Remaining reality gates

- A new repository instance exercises the process-persistence boundary, but an
  actual force-stop/reboot sequence still needs physical-device evidence.
- EP3 text and attachment manifests are selected only after signed pre-key
  delivery and explicit safety-code verification. Verified-reset UX remains locked.
- The repository serializes transitions inside one app process. EutherPing must
  keep session mutation in that process or add an explicit cross-process lock.
- Samsung and GrapheneOS must still prove Keystore availability, reboot behavior,
  delayed/out-of-order delivery, identity replacement, state loss, and storage
  inspection before ratcheted carrier traffic ships.
- External review remains mandatory before production activation or removal of
  the Secure Beta label.
