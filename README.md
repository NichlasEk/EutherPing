# EutherPing

EutherPing is an original Android messaging project with two deliberately distinct lanes:

- ordinary carrier SMS/MMS (`CELL`)
- future end-to-end encrypted app-to-app messaging (`SECURE PING`)

The current `0.2.0` checkpoint is a functional SMS beta. Once the user explicitly selects EutherPing as Android's default SMS handler and grants the SMS permissions, it reads real SMS conversations, receives incoming SMS, sends single- and multipart SMS, persists messages in Android's Telephony provider, and posts incoming-message notifications.

The secure lane remains a visual protocol preview and does **not** claim to implement encryption. MMS entry points are declared so Android can offer the SMS role, but carrier MMS download, attachments, multi-SIM selection, contact names, blocking, backup, and secure app-to-app transport remain future work.

## Build

```sh
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk ANDROID_HOME=/opt/android-sdk ./gradlew testDebugUnitTest assembleDebug lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Privacy

EutherPing 0.2.0 has no internet permission, accounts, analytics, ads, or telemetry. SMS data is read from and written to Android's system Telephony provider only after the user makes EutherPing the default SMS app. Incoming-message notifications are generated locally. Cloud backup and device transfer are disabled.

## Visual direction

Near-black OLED surfaces, toxic-green sonar geometry, amber carrier signals, and violet secure echoes. The bundled sonar artwork is original and generated specifically for EutherPing from a user-provided mood reference.

## Security direction

EutherPing will own its wire framing, product semantics, versioning, test vectors, and downgrade protections while relying on established cryptographic constructions. No silent fallback from a secure session to SMS will be allowed.

License selection is intentionally deferred until the first product architecture is settled.
