# Libsignal dependency spike — 2026-08-05

## Outcome

The isolated spike proves that EutherPing can drive libsignal on the JVM and on
Android, but version `0.99.4` is not ready to enter the shipping app. No EP3
traffic, app dependency, existing-Vessel migration, APK publication, or server
deployment is enabled by this checkpoint.

Pinned artifacts from Signal's Maven repository:

| Artifact | SHA-256 | Download size |
| --- | --- | ---: |
| `libsignal-client-0.99.4.jar` | `ff3dc232e391de035c7f38d19b67b499af91ab74c081a6b8ae9e4f6f413b01b1` | 138.4 MB |
| `libsignal-client-0.99.4-sources.jar` | `c3a4f4dca12dc22c7ae951be37757afdbc99f1f2029bad349fc6a78c8ca75a91` | measured locally |
| `libsignal-android-0.99.4.aar` | `200a27d5b342606aab21732203bc5a7ac028d811f4049efa1453277f840a3765` | 181.5 MB |

The Java classes use class-file version 65, so JVM probe tests launch a pinned
JDK 21 Gradle toolchain. The Android modules and normal EutherPing build still
run with JDK 17 using core library desugaring. The pinned runtime graph includes
libsignal client/Android, Kotlin stdlib, coroutines, and Kotlin serialization.
The resolved inventory is frozen in
[`LIBSIGNAL_SBOM-0.99.4.md`](LIBSIGNAL_SBOM-0.99.4.md).

## Evidence

The Alice/Bob harness passes initial PQXDH session setup, bidirectional Double
Ratchet traffic, out-of-order messages, process-style engine reload against
persisted state, duplicate rejection, provider mismatch rejection, and
copy-on-write rollback. It prints sizes only, never keys or plaintext:

```
prekey publication  1844 bytes
initial ciphertext  1759 bytes
session ciphertext    72 bytes
Alice state          4481 bytes
Bob state            5088 bytes
```

Base64 alone expands the prekey publication to about 2,460 characters and the
first ciphertext to about 2,348. At the normal 153 GSM-7 characters per
concatenated segment, that is at least roughly 17 and 16 SMS parts respectively,
before EP3 metadata and without assuming all encoded characters stay GSM-7.
Carrier SMS is therefore not an acceptable production prekey path for this
probe. The protocol must use an authenticated data/prekey service, QR/Nearby
bootstrap, or another reviewed design; it must not trim PQXDH itself.

The first universal probe accidentally demonstrated why packaging must be
gated: all mobile ABIs plus testing and desktop JNI produced a 984 MB debug APK.
After excluding test/desktop binaries and selecting arm64 only:

| Probe artifact | Size |
| --- | ---: |
| arm64 debug APK | 116,367,571 bytes |
| arm64 minified unsigned release APK | 111,018,552 bytes |
| packaged `libsignal_jni.so` | 110,830,208 bytes |

An Android 11 x86_64 emulator then loaded the real native library and created a
post-quantum prekey publication successfully. Play ABI splitting can avoid
shipping every ABI to one device, but it does not remove the approximately
111 MB arm64 cost.

## Production stop gates

- `app` must remain dependency-free until the size and distribution plan is
  accepted; the probe module must never be published as EutherPing.
- Replace probe framing with a bounded, specified, authenticated EP3 envelope.
- Implement a Keystore-encrypted atomic durable repository and crash tests; the
  present in-memory repository is test scaffolding only.
- Choose and review authenticated prekey delivery that is practical without
  weakening the library protocol.
- Add identity-replacement, corrupt-state, reinstall, downgrade, version-skew,
  and malformed native-input fixtures.
- Pass two-phone Samsung/GrapheneOS delayed/out-of-order/reinstall testing.
- Complete AGPL source/notice, SBOM, Play-policy, update-response, and external
  security review before distributing any libsignal-containing build.
