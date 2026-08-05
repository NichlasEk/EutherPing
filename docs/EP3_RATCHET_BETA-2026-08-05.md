# EP3 Ratchet Beta — 0.8.13

EutherPing 0.8.13 extends the real two-phone EP3 beta with ratcheted attachment
manifests. Version 0.8.12 was the first text-only test build.
It uses Vodozemac 0.10.0 for the session protocol and retains Tink for the
long-lived identity signature, legacy history, and local plaintext vault.

## User flow

1. Install 0.8.13 on both phones.
2. In a new Vessel, phone A sends `EP3I`, a signed identity and ratchet pre-key.
3. Phone B accepts. It creates a Vodozemac outbound session and returns `EP3A`,
   whose first PRE_KEY ciphertext contains B's signed identity and pre-key.
4. Both phones compare the newly bound safety code and press Verify.
5. Text then travels only as `EP3M`. Every send and receive advances provider
   state. There is no fallback to EP1M, plaintext SMS, or carrier MMS.
6. An attachment encrypts locally with a fresh AES-256-GCM key. Its complete
   manifest travels as `EP3F`; only ciphertext moves over direct Wi-Fi or paired
   Bluetooth, with no fallback to EP1F or MMS.

A previously verified legacy Vessel shows `UPGRADE TO EP3`. Old EP1 messages
remain readable after the upgrade, but all new text uses EP3 after verification.

## Storage and privacy

Vodozemac account and per-contact session bytes live in the dedicated
`secure_sessions_v3` namespace. The complete serialized state is encrypted with
a dedicated Android Keystore key and written through `AtomicFile`. Telephony
stores only EP3 envelopes and unavoidable carrier metadata. Decrypted received
text and locally sent text are stored in the existing Keystore-protected Secure
vault by random message ID; the ordinary conversation cache keeps no plaintext
Vessel preview.

## Deliberate beta limits

- EP3F is implemented but remains beta. The manifest ratchets, while the file
  uses independently authenticated AES-256-GCM and the existing direct-transfer
  request proof. Physical Wi-Fi/Bluetooth interop and external review remain.
- There is no user-facing verified EP3 reset yet. Clear app data only when both
  users are prepared to pair again.
- Ratchet-state commit and received-plaintext vault commit are not one atomic
  transaction. A crash in that narrow window can leave an undecryptable bubble;
  it must never retry with a consumed message key or downgrade.
- The invitation and acceptance can each span several billable SMS segments.
- No independent cryptographic integration review has signed off the framing,
  storage transaction boundary, identity UX, or dependency-update policy.
- Physical Samsung, GrapheneOS, carrier, force-stop, and reboot evidence must be
  collected before this can advance beyond Ratchet Beta.

## First physical test

Use a new Vessel or the explicit upgrade button, compare the complete safety
code, then alternate at least five short messages. Force-stop the receiver,
send two messages, reopen it, and verify both appear once. Reboot both phones
and alternate two more messages. Then offer one small image over shared Wi-Fi,
download it, and verify that preview still requires explicit decryption. Disable
Wi-Fi, repeat over already paired Bluetooth, and confirm there is no MMS or EP1F
fallback. Record any missing SMS segment, invalid capsule, unavailable plaintext
warning, hash failure, identity warning, or composer lock before trying to reset
either installation.
