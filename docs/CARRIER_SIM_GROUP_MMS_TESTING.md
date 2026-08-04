# Multi-SIM and group MMS physical acceptance

Version 0.8.4 implements the app and Android-provider side of explicit SIM
selection and participant-based group MMS. Emulator tests prove persistence,
thread identity, reply-all recipient recovery, and backward-compatible
single-recipient behavior. Only real phones, SIMs, APNs and operators can prove
the carrier path.

Run the same matrix on the target Samsung and GrapheneOS phones. Record phone,
Android build, operator, SIM slot, APN, Wi-Fi-calling state and whether roaming
or mobile data is active. Do not include message contents or full phone numbers
in a shared report.

1. Grant the optional phone/SIM permission when 0.8.4 first opens.
2. With two active SIMs, open a one-to-one Signal, select SIM 1, send SMS and an
   image plus caption, then confirm the bubbles show SIM 1.
3. Leave and reopen the conversation. Confirm SIM 1 remains selected. Select
   SIM 2, repeat both sends, and confirm the choice and bubble labels change.
4. Disable the remembered SIM. Reopen the conversation and confirm EutherPing
   requires a new explicit choice rather than silently moving the send.
5. Start New Cell Signal with two comma-separated recipients. Send short text,
   long text, and an image plus caption. All must remain one group-MMS thread on
   every participant phone; replies must return to the complete group.
6. Receive a group MMS while EutherPing sleeps. Open its notification, verify
   the participant header, then use notification Reply and confirm reply-all.
7. Repeat image/caption send with mobile data off. The failed item must remain
   retryable and must not block the next message. Restore data and retry once.
8. Repeat the group send with Wi-Fi calling on/off and, where safe and included
   by the subscriptions, roaming on/off. Record carrier error details.
9. Re-run a single-SIM one-to-one SMS, long-text auto-MMS, image MMS, delivery
   status and notification reply to confirm there is no regression.

Phase 5 is physically accepted only after both target phones pass the applicable
rows on their real subscriptions. A carrier rejection is recorded separately
from an app/provider failure.
