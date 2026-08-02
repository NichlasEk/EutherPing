# Google Play release readiness

Checked against Google Play and Android default-handler guidance on 2026-08-02. Policies change; re-check the official policy pages at submission time.

## Already aligned in 0.6.5

- Messaging is the app's prominent core functionality, not a secondary permission use.
- The app asks for Android's SMS role before requesting SMS permissions.
- SMS/MMS provider access and sending are gated by the active default-SMS role and required runtime permissions.
- The manifest declares the SMS-delivery receiver, MMS WAP-Push receiver, `SENDTO` activity schemes, and respond-via-message service expected of a default SMS handler.
- Cloud backup and device transfer are disabled.
- There are no ads, analytics, telemetry, developer accounts, or remote EutherPing message service.
- Images and files are selected through Android's system picker; broad storage permissions are not requested.
- Third-party MMS code is Apache 2.0, pinned, non-transitive, and accompanied by packaged attribution and the complete license text.
- Target SDK is 36.

## Required before Play submission

1. Physically validate carrier-MMS send and receive on the supported phones, SIMs, APNs, roaming/mobile-data states, Wi-Fi calling, and multi-SIM configurations. Emulator PDU/provider tests do not prove MMSC interoperability.
2. Build and test a signed release Android App Bundle. Do not upload the debug APK.
3. Publish `docs/PRIVACY.md` at a stable HTTPS URL, replace its contact TODO, and add an in-app Privacy Policy link.
4. Complete Play Console's SMS/Call Log Permissions Declaration Form as a **default SMS handler**. Explain why each of `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `WRITE_SMS`, `RECEIVE_MMS`, and `RECEIVE_WAP_PUSH` is required for core messaging.
5. Complete the Data safety form from the shipped behavior and hosted policy. Re-audit every SDK and release artifact rather than copying assumptions from this document.
6. Make the store title, short description, full description, screenshots, and onboarding prominently describe EutherPing as a default SMS/MMS app. Do not present restricted permissions as being used only for the optional secure feature.
7. Add support contact details, content rating, app access instructions, and a review script explaining how to assign the SMS role and exercise SMS/MMS without exposing reviewers' real message data.
8. Re-run dependency/license/SBOM and vulnerability scans. Decide whether to maintain the minimal Apache PDU subset directly rather than retain the archived upstream package.
9. Re-check current target-API deadlines and the Contacts permission policy. If targeting API 37 or later, evaluate Android Contact Picker as the minimum-scope alternative to `READ_CONTACTS`.
10. Obtain actual Google Play approval. Passing this checklist improves readiness but cannot guarantee policy approval.
