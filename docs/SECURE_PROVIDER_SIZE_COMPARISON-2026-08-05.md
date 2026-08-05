# Secure provider size comparison — 2026-08-05

## Outcome

The 111 MB result is the size of Signal's supported all-purpose Android JNI
artifact, not a lower bound for the protocol. A source-built wrapper that calls
only `libsignal-protocol` can be much smaller, but it would make EutherPing the
owner of an unsupported native API bridge, an exact nightly Rust toolchain, and
AGPL distribution compliance.

Vodozemac `0.10.0` is the recommended implementation for the next isolated
provider spike. It is not approved for production yet. The shipping `app`
module has no dependency on either probe and its behavior and APK are unchanged.

## Like-for-like probes

Both probes perform real asymmetric session setup, an initial message, and a
reply. The Vodozemac probe additionally exercises out-of-order delivery,
pickle/reload, duplicate rejection, non-contributory public-key rejection, and
an identity mismatch. The libsignal source probe uses PQXDH with a Kyber-1024
prekey and Double Ratchet; it exists only in `/tmp` and is not a maintained
EutherPing module.

| Artifact / property | Vodozemac 0.10.0 | Signal libsignal 0.99.4 |
| --- | ---: | ---: |
| Supported upstream Android artifact | none | 110,830,208-byte arm64 JNI library |
| Minimal source-built arm64 protocol library | 437,592 bytes | 730,016 bytes |
| Minimal release Android library (AAR) | 248,916 bytes | not packaged |
| Host source-built protocol library | not used for decision | 1,001,320 bytes |
| Rust toolchain | stable 1.97.1 | exact nightly 2026-07-15 |
| License | Apache-2.0 | AGPL-3.0-only |
| Post-quantum handshake | no | yes, PQXDH/Kyber-1024 |
| Android integration | EutherPing-owned JNI | EutherPing-owned JNI if minimized |

Reproducibility identifiers:

| Measured artifact/source | SHA-256 or commit |
| --- | --- |
| Vodozemac arm64 JNI library | `d27f796b1824ccbdb8ee6bb6b78f6852d61ff66b1f757fce20e6a7baf95f8e2a` |
| Vodozemac arm64 release AAR | `1a6ff623f216de30587fbe360379c363b7682f88f726a44397e2b71c96aa4c5e` |
| Minimal libsignal arm64 library | `c0edacd246d18d845be7b76f4aab91a7a3938a83a5becbb46820038d04c36c06` |
| Signal source tag/commit | `v0.99.4` / `387da3e29ac87c821aedc697aee77646a1c72fff` |

Vodozemac's crates.io archive is pinned by `Cargo.lock` with checksum
`b98bf83c0992966775b8012f194b07b44928996163e5a05b741b43891571ae5b`.

The source-built Signal number is useful engineering evidence, but it does not
turn Signal's internal Rust crates into a supported public Android API. It also
does not remove the AGPL review or the large prekey/first-message carrier cost
already measured by the official Java probe.

## Vodozemac Android evidence

The opt-in `crypto-vodozemac` module pins the exact Cargo dependency graph and
is excluded from normal Gradle configuration unless
`-PincludeCryptoVodoProbe=true` is supplied. On Android 11 x86_64, the native
JNI probe passed 1/1 instrumentation tests. Its measured protocol data was:

```
signed key publication   160 bytes
initial ciphertext       168 bytes
session ciphertext        63 bytes
Alice state             1429 bytes
Bob state               1481 bytes
```

The arm64 library is stripped and its ELF load segments are compatible with
16 KiB Android pages. Normal EutherPing builds do not run Cargo and do not
package this library.

## Why Vodozemac advances

Version `0.10.0` contains the upstream checks that reject non-contributory
X25519 input and uses strict Ed25519 verification by default. The probe verifies
the former on both host and Android. Compared with a custom libsignal bridge it
also provides the distribution fit requested for a future Play release and a
far simpler stable-Rust build.

The choice deliberately accepts weaker properties than current Signal:
Vodozemac's production Olm v1 has 64-bit authentication tags and no
post-quantum handshake, while Olm v2 is explicitly experimental. Therefore
`crypto-vodozemac` remains a probe until an external reviewer accepts the v1
trade-off for EutherPing's threat model, the authenticated prekey/pairing design
is frozen, and durable encrypted-state plus two-phone tests pass.

## Toolchain reproduction

Arch's AUR `android-ndk` package was built locally as version `r29-3`. The final
system installation requires administrator authentication:

```sh
sudo pacman -U --noconfirm \
  /home/nichlas/.cache/yay/android-ndk/android-ndk-r29-3-x86_64.pkg.tar.zst
```

`cargo-ndk` was kept in an isolated Rust environment because the Arch package
depends on `rustup` and would replace the machine's existing system `rust` and
`cargo` packages. This is a development-tool decision, not an app runtime
dependency.

## Next production gates

1. Implement the provider behind `crypto-api`; never expose raw keys or raw JNI
   handles to UI code.
2. Specify and authenticate the compact key-publication envelope and safety-code
   binding. No unauthenticated carrier key lookup is permitted.
3. Envelope-encrypt opaque pickles in the dedicated Vessel store and make every
   ratchet transition atomic.
4. Add malformed input, corrupt pickle, rollback, skipped-key exhaustion,
   reinstall, identity-change, and version-skew fixtures.
5. Prove Samsung/GrapheneOS interoperability with delayed, duplicated, and
   out-of-order delivery.
6. Obtain external cryptographic integration review before EP3 production or
   removal of the Secure Beta label.
