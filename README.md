# EutherPing

EutherPing is an original Android messaging project with two deliberately distinct lanes:

- ordinary carrier SMS/MMS (`CELL`)
- future end-to-end encrypted app-to-app messaging (`SECURE PING`)

The current `0.1.0` checkpoint is an interactive visual prototype. It does **not** yet send real SMS and does **not** claim to implement encryption. Its secure lane is explicitly marked as a protocol preview until the transport and audited cryptographic core exist.

## Build

```sh
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk ANDROID_HOME=/opt/android-sdk ./gradlew testDebugUnitTest assembleDebug lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Visual direction

Near-black OLED surfaces, toxic-green sonar geometry, amber carrier signals, and violet secure echoes. The bundled sonar artwork is original and generated specifically for EutherPing from a user-provided mood reference.

## Security direction

EutherPing will own its wire framing, product semantics, versioning, test vectors, and downgrade protections while relying on established cryptographic constructions. No silent fallback from a secure session to SMS will be allowed.

License selection is intentionally deferred until the first product architecture is settled.
