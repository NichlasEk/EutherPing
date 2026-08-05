# EP3 Ratchet Beta — 0.8.12

EutherPing 0.8.12 is the first build intended for real two-phone EP3 testing.
It uses Vodozemac 0.10.0 for the session protocol and retains Tink for the
long-lived identity signature, legacy history, and local plaintext vault.

## User flow

1. Install 0.8.12 on both phones.
2. In a new Vessel, phone A sends `EP3I`, a signed identity and ratchet pre-key.
3. Phone B accepts. It creates a Vodozemac outbound session and returns `EP3A`,
   whose first PRE_KEY ciphertext contains B's signed identity and pre-key.
4. Both phones compare the newly bound safety code and press Verify.
5. Text then travels only as `EP3M`. Every send and receive advances provider
   state. There is no fallback to EP1M, plaintext SMS, or carrier MMS.

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

- EP3 attachments are blocked until a ratcheted and reviewed EP3F manifest is
  implemented. This prevents fallback to legacy HPKE attachment keys.
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
and alternate two more messages. Record any missing SMS segment, invalid capsule,
unavailable plaintext warning, identity warning, or composer lock before trying
to reset either installation.
