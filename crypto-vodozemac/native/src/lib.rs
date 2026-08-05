// SPDX-License-Identifier: Apache-2.0

use std::ptr;

use jni::{JNIEnv, objects::JClass, sys::jstring};
use serde::Serialize;
use vodozemac::{
    Curve25519PublicKey,
    olm::{Account, OlmMessage, Session, SessionConfig},
};

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ProbeMetrics {
    version: &'static str,
    publication_bytes: usize,
    initial_ciphertext_bytes: usize,
    session_ciphertext_bytes: usize,
    alice_state_bytes: usize,
    bob_state_bytes: usize,
    reply_plaintext: String,
    out_of_order_accepted: bool,
    reload_accepted: bool,
    duplicate_rejected: bool,
    non_contributory_key_rejected: bool,
    identity_mismatch_rejected: bool,
}

fn run_probe() -> Result<ProbeMetrics, String> {
    let alice = Account::new();
    let mut bob = Account::new();
    bob.generate_one_time_keys(1);
    let bob_one_time_key = bob
        .one_time_keys()
        .values()
        .next()
        .copied()
        .ok_or("missing one-time key")?;

    // A production publication must authenticate the Curve25519 identity and
    // one-time key with the Ed25519 identity. Count all three keys plus the
    // detached signature, not merely vodozemac's raw OTK.
    let mut signed_publication = Vec::with_capacity(96);
    signed_publication.extend_from_slice(bob.curve25519_key().as_bytes());
    signed_publication.extend_from_slice(bob.ed25519_key().as_bytes());
    signed_publication.extend_from_slice(bob_one_time_key.as_bytes());
    let publication_signature = bob.sign(&signed_publication);
    let publication_bytes = signed_publication.len() + publication_signature.to_bytes().len();

    let mut alice_session = alice
        .create_outbound_session(
            SessionConfig::version_1(),
            bob.curve25519_key(),
            bob_one_time_key,
        )
        .map_err(|error| error.to_string())?;
    bob.mark_keys_as_published();

    let initial = alice_session
        .encrypt(b"first")
        .map_err(|error| error.to_string())?;
    let initial_ciphertext_bytes = initial.to_parts().1.len();
    let OlmMessage::PreKey(initial_pre_key) = initial else {
        return Err("first message was not a pre-key message".into());
    };
    let created = bob
        .create_inbound_session(
            SessionConfig::version_1(),
            alice.curve25519_key(),
            &initial_pre_key,
        )
        .map_err(|error| error.to_string())?;
    let mut bob_session = created.session;

    let reply = bob_session
        .encrypt(b"reply")
        .map_err(|error| error.to_string())?;
    let session_ciphertext_bytes = reply.to_parts().1.len();
    let reply_plaintext = alice_session
        .decrypt(&reply)
        .map_err(|error| error.to_string())?;

    let second = alice_session
        .encrypt(b"second")
        .map_err(|error| error.to_string())?;
    let third = alice_session
        .encrypt(b"third")
        .map_err(|error| error.to_string())?;
    let third_plaintext = bob_session.decrypt(&third);
    let second_plaintext = bob_session.decrypt(&second);
    let out_of_order_accepted = matches!(third_plaintext, Ok(value) if value == b"third")
        && matches!(second_plaintext, Ok(value) if value == b"second");

    let alice_pickle = alice_session.pickle();
    let bob_pickle = bob_session.pickle();
    let alice_state = serde_json::to_vec(&alice_pickle).map_err(|error| error.to_string())?;
    let bob_state = serde_json::to_vec(&bob_pickle).map_err(|error| error.to_string())?;
    let mut alice_session = Session::from_pickle(alice_pickle);
    let mut bob_session = Session::from_pickle(bob_pickle);
    let after_reload = bob_session
        .encrypt(b"after reload")
        .map_err(|error| error.to_string())?;
    let reload_accepted =
        matches!(alice_session.decrypt(&after_reload), Ok(value) if value == b"after reload");
    let duplicate_rejected = bob_session.decrypt(&second).is_err();

    let non_contributory_key_rejected = alice
        .create_outbound_session(
            SessionConfig::version_1(),
            Curve25519PublicKey::from([0_u8; 32]),
            Curve25519PublicKey::from([0_u8; 32]),
        )
        .is_err();

    let mallory = Account::new();
    let mut bob_for_mismatch = Account::new();
    bob_for_mismatch.generate_one_time_keys(1);
    let mismatch_otk = *bob_for_mismatch
        .one_time_keys()
        .values()
        .next()
        .ok_or("missing mismatch one-time key")?;
    let mut mallory_session = mallory
        .create_outbound_session(
            SessionConfig::version_1(),
            bob_for_mismatch.curve25519_key(),
            mismatch_otk,
        )
        .map_err(|error| error.to_string())?;
    bob_for_mismatch.mark_keys_as_published();
    let mismatch_message = mallory_session
        .encrypt(b"mismatch")
        .map_err(|error| error.to_string())?;
    let identity_mismatch_rejected = match mismatch_message {
        OlmMessage::PreKey(message) => bob_for_mismatch
            .create_inbound_session(SessionConfig::version_1(), alice.curve25519_key(), &message)
            .is_err(),
        OlmMessage::Normal(_) => false,
    };

    Ok(ProbeMetrics {
        version: vodozemac::VERSION,
        publication_bytes,
        initial_ciphertext_bytes,
        session_ciphertext_bytes,
        alice_state_bytes: alice_state.len(),
        bob_state_bytes: bob_state.len(),
        reply_plaintext: String::from_utf8(reply_plaintext).map_err(|error| error.to_string())?,
        out_of_order_accepted,
        reload_accepted,
        duplicate_rejected,
        non_contributory_key_rejected,
        identity_mismatch_rejected,
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_se_apothictech_eutherping_crypto_vodozemac_VodozemacNativeProbe_runProbe(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let json = run_probe()
        .and_then(|metrics| serde_json::to_string(&metrics).map_err(|error| error.to_string()));
    let value = match json {
        Ok(value) => value,
        Err(error) => format!("{{\"error\":{}}}", serde_json::Value::String(error)),
    };

    env.new_string(value)
        .map(|string| string.into_raw())
        .unwrap_or(ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::run_probe;

    #[test]
    fn ratchet_and_fail_closed_cases_pass() {
        let metrics = run_probe().expect("probe must pass");
        assert_eq!(metrics.reply_plaintext, "reply");
        assert!(metrics.out_of_order_accepted);
        assert!(metrics.reload_accepted);
        assert!(metrics.duplicate_rejected);
        assert!(metrics.non_contributory_key_rejected);
        assert!(metrics.identity_mismatch_rejected);
        assert!(metrics.publication_bytes <= 256);
        assert!(metrics.initial_ciphertext_bytes <= 256);
        assert!(metrics.session_ciphertext_bytes <= 128);
        println!("{metrics:?}");
    }
}
