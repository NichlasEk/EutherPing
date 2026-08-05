# Libsignal probe dependency inventory (SBOM) — 0.99.4

Status: resolved `crypto-libsignal` release runtime classpath on 2026-08-05.
This inventory describes the isolated probe, not the shipping EutherPing APK.

| Group and artifact | Resolved version | Role |
| --- | --- | --- |
| `org.signal:libsignal-android` | `0.99.4` | Android ABI/JNI packaging |
| `org.signal:libsignal-client` | `0.99.4` | Java protocol API |
| `org.jetbrains.kotlin:kotlin-stdlib` | `2.2.20` | resolved Kotlin runtime |
| `org.jetbrains:annotations` | `23.0.0` | JVM annotations |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.10.2` | Android coroutine runtime |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | `1.10.2` | coroutine metadata |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | `1.10.2` | JVM coroutine runtime |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | `1.10.2` | coroutine constraints |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.9.0` | serialization metadata |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm` | `1.9.0` | JSON JVM runtime |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` | `1.9.0` | serialization metadata |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | `1.9.0` | serialization JVM runtime |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom` | `1.9.0` | serialization constraints |

The build also uses `com.android.tools:desugar_jdk_libs:2.1.5` as a build-time
core-library desugaring input. Exact Signal artifact checksums and packaged JNI
measurements are recorded in
[`LIBSIGNAL_SPIKE-2026-08-05.md`](LIBSIGNAL_SPIKE-2026-08-05.md).

Regenerate the authoritative Gradle graph with:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew \
  :crypto-libsignal:dependencies --configuration releaseRuntimeClasspath
```
