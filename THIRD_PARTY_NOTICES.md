# Third-party notices

EutherPing uses the following third-party components. This notice is also packaged in the Android app under `assets/THIRD_PARTY_NOTICES.txt`; the complete common license text is packaged under `assets/licenses/Apache-2.0.txt`.

| Component | Version | Copyright / origin | License | Use in EutherPing |
| --- | --- | --- | --- | --- |
| Android SMS/MMS Sending Library (`com.klinkerapps:android-smsmms`) | 5.2.6 | Copyright 2017 Jacob Klinker; includes Android Open Source Project-derived MMS PDU code | Apache License 2.0 | Standards-based MMS PDU parsing, composition, persistence, and downloaded-PDU acknowledgement support. EutherPing uses Android's current `SmsManager` for carrier transport. |
| Logger (`com.klinkerapps:logger`) | 1.0.3 | Copyright Jacob Klinker and contributors; <https://github.com/klinker41/android-logger> | Apache License 2.0 | Minimal logging API required by the MMS PDU implementation. |

Both dependencies are intentionally pinned and declared non-transitive. The MMS library's legacy HTTP-client dependencies are not packaged because EutherPing delegates carrier networking to Android's `SmsManager`. Before a Google Play release, repeat the dependency and license audit and review whether the small required PDU subset should be maintained directly.
