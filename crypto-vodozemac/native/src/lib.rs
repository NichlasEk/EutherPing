// SPDX-License-Identifier: Apache-2.0

use std::ptr;

use jni::{
    JNIEnv,
    objects::{JByteArray, JClass},
    sys::jbyteArray,
};
use vodozemac::{
    Curve25519PublicKey, Ed25519PublicKey, Ed25519Signature,
    olm::{Account, AccountPickle, OlmMessage, Session, SessionConfig, SessionPickle},
};

const PUBLICATION_MAGIC: &[u8; 4] = b"EVK1";
const CIPHERTEXT_MAGIC: &[u8; 4] = b"EVC1";
const RESPONSE_MAGIC: &[u8; 4] = b"EVR1";
const PUBLICATION_LENGTH: usize = 4 + 32 + 32 + 32 + 64;
const PRE_KEY_OVERHEAD: usize = 4 + 1 + 32 + 64;
const MAX_ACCOUNT_STATE: usize = 1024 * 1024;
const MAX_SESSION_STATE: usize = 2 * 1024 * 1024;
const MAX_MESSAGE: usize = 128 * 1024;

fn serialize_account(account: &Account) -> Result<Vec<u8>, String> {
    serde_json::to_vec(&account.pickle()).map_err(|error| error.to_string())
}

fn deserialize_account(bytes: &[u8]) -> Result<Account, String> {
    if bytes.len() > MAX_ACCOUNT_STATE {
        return Err("Vodozemac account state exceeds limit".into());
    }
    let pickle: AccountPickle = serde_json::from_slice(bytes).map_err(|error| error.to_string())?;
    Ok(Account::from_pickle(pickle))
}

fn serialize_session(session: &Session) -> Result<Vec<u8>, String> {
    serde_json::to_vec(&session.pickle()).map_err(|error| error.to_string())
}

fn deserialize_session(bytes: &[u8]) -> Result<Session, String> {
    if bytes.len() > MAX_SESSION_STATE {
        return Err("Vodozemac session state exceeds limit".into());
    }
    let pickle: SessionPickle = serde_json::from_slice(bytes).map_err(|error| error.to_string())?;
    Ok(Session::from_pickle(pickle))
}

fn create_account() -> Result<Vec<u8>, String> {
    serialize_account(&Account::new())
}

fn create_pre_key_publication(account_bytes: &[u8]) -> Result<Vec<Vec<u8>>, String> {
    let mut account = deserialize_account(account_bytes)?;
    account.generate_one_time_keys(1);
    let one_time_key = account
        .one_time_keys()
        .values()
        .next()
        .copied()
        .ok_or("Vodozemac did not generate a one-time key")?;

    let mut publication = Vec::with_capacity(PUBLICATION_LENGTH);
    publication.extend_from_slice(PUBLICATION_MAGIC);
    publication.extend_from_slice(account.curve25519_key().as_bytes());
    publication.extend_from_slice(account.ed25519_key().as_bytes());
    publication.extend_from_slice(one_time_key.as_bytes());
    publication.extend_from_slice(&account.sign(&publication).to_bytes());
    account.mark_keys_as_published();

    Ok(vec![serialize_account(&account)?, publication])
}

struct VerifiedPublication {
    curve_key: Curve25519PublicKey,
    one_time_key: Curve25519PublicKey,
}

fn verify_publication(publication: &[u8]) -> Result<VerifiedPublication, String> {
    if publication.len() != PUBLICATION_LENGTH || !publication.starts_with(PUBLICATION_MAGIC) {
        return Err("Malformed Vodozemac publication".into());
    }
    let curve_key =
        Curve25519PublicKey::from_slice(&publication[4..36]).map_err(|error| error.to_string())?;
    let signing_bytes: [u8; 32] = publication[36..68]
        .try_into()
        .map_err(|_| "Invalid signing key length")?;
    let signing_key =
        Ed25519PublicKey::from_slice(&signing_bytes).map_err(|error| error.to_string())?;
    let one_time_key = Curve25519PublicKey::from_slice(&publication[68..100])
        .map_err(|error| error.to_string())?;
    let signature =
        Ed25519Signature::from_slice(&publication[100..]).map_err(|error| error.to_string())?;
    signing_key
        .verify(&publication[..100], &signature)
        .map_err(|error| error.to_string())?;
    Ok(VerifiedPublication {
        curve_key,
        one_time_key,
    })
}

fn establish_outbound(account_bytes: &[u8], publication: &[u8]) -> Result<Vec<Vec<u8>>, String> {
    let account = deserialize_account(account_bytes)?;
    let verified = verify_publication(publication)?;
    let session = account
        .create_outbound_session(
            SessionConfig::version_1(),
            verified.curve_key,
            verified.one_time_key,
        )
        .map_err(|error| error.to_string())?;
    Ok(vec![serialize_session(&session)?])
}

fn encode_ciphertext(account: &Account, message: OlmMessage) -> Vec<u8> {
    let (message_type, raw) = message.to_parts();
    let mut encoded = Vec::with_capacity(
        raw.len()
            + if message_type == 0 {
                PRE_KEY_OVERHEAD
            } else {
                5
            },
    );
    encoded.extend_from_slice(CIPHERTEXT_MAGIC);
    encoded.push(message_type as u8);
    if message_type == 0 {
        encoded.extend_from_slice(account.ed25519_key().as_bytes());
        let mut signed = Vec::with_capacity(5 + raw.len());
        signed.extend_from_slice(CIPHERTEXT_MAGIC);
        signed.push(0);
        signed.extend_from_slice(&raw);
        encoded.extend_from_slice(&account.sign(&signed).to_bytes());
    }
    encoded.extend_from_slice(&raw);
    encoded
}

struct VerifiedCiphertext {
    message: OlmMessage,
    signing_key: Option<Ed25519PublicKey>,
}

fn verify_ciphertext(ciphertext: &[u8]) -> Result<VerifiedCiphertext, String> {
    if ciphertext.len() > MAX_MESSAGE {
        return Err("Vodozemac ciphertext exceeds limit".into());
    }
    if ciphertext.len() <= 5 || !ciphertext.starts_with(CIPHERTEXT_MAGIC) {
        return Err("Malformed Vodozemac ciphertext".into());
    }
    let message_type = ciphertext[4] as usize;
    let (raw, signing_key) = match message_type {
        0 => {
            if ciphertext.len() <= PRE_KEY_OVERHEAD {
                return Err("Truncated Vodozemac pre-key ciphertext".into());
            }
            let signing_bytes: [u8; 32] = ciphertext[5..37]
                .try_into()
                .map_err(|_| "Invalid signing key length")?;
            let signing_key =
                Ed25519PublicKey::from_slice(&signing_bytes).map_err(|error| error.to_string())?;
            let signature = Ed25519Signature::from_slice(&ciphertext[37..101])
                .map_err(|error| error.to_string())?;
            let raw = &ciphertext[101..];
            let mut signed = Vec::with_capacity(5 + raw.len());
            signed.extend_from_slice(CIPHERTEXT_MAGIC);
            signed.push(0);
            signed.extend_from_slice(raw);
            signing_key
                .verify(&signed, &signature)
                .map_err(|error| error.to_string())?;
            (raw, Some(signing_key))
        }
        1 => (&ciphertext[5..], None),
        _ => return Err("Unsupported Vodozemac ciphertext kind".into()),
    };
    let message = OlmMessage::from_parts(message_type, raw).map_err(|error| error.to_string())?;
    Ok(VerifiedCiphertext {
        message,
        signing_key,
    })
}

fn encrypt(
    account_bytes: &[u8],
    session_bytes: &[u8],
    plaintext: &[u8],
) -> Result<Vec<Vec<u8>>, String> {
    if plaintext.len() > MAX_MESSAGE {
        return Err("Vodozemac plaintext exceeds limit".into());
    }
    let account = deserialize_account(account_bytes)?;
    let mut session = deserialize_session(session_bytes)?;
    let message = session
        .encrypt(plaintext)
        .map_err(|error| error.to_string())?;
    Ok(vec![
        serialize_session(&session)?,
        encode_ciphertext(&account, message),
    ])
}

fn decrypt(
    account_bytes: &[u8],
    session_bytes: &[u8],
    ciphertext: &[u8],
) -> Result<Vec<Vec<u8>>, String> {
    let mut account = deserialize_account(account_bytes)?;
    let verified = verify_ciphertext(ciphertext)?;
    let (session, plaintext) = if session_bytes.is_empty() {
        let OlmMessage::PreKey(pre_key) = &verified.message else {
            return Err("No session for normal Vodozemac ciphertext".into());
        };
        let created = account
            .create_inbound_session(SessionConfig::version_1(), pre_key.identity_key(), pre_key)
            .map_err(|error| error.to_string())?;
        (created.session, created.plaintext)
    } else {
        let mut session = deserialize_session(session_bytes)?;
        let plaintext = session
            .decrypt(&verified.message)
            .map_err(|error| error.to_string())?;
        (session, plaintext)
    };
    Ok(vec![
        serialize_account(&account)?,
        serialize_session(&session)?,
        plaintext,
        verified
            .signing_key
            .map(|key| key.as_bytes().to_vec())
            .unwrap_or_default(),
    ])
}

fn encode_fields(fields: Vec<Vec<u8>>) -> Result<Vec<u8>, String> {
    if fields.len() > u8::MAX as usize {
        return Err("Too many native response fields".into());
    }
    let capacity = fields.iter().try_fold(5usize, |total, field| {
        total
            .checked_add(4 + field.len())
            .ok_or("Native response is too large")
    })?;
    let mut encoded = Vec::with_capacity(capacity);
    encoded.extend_from_slice(RESPONSE_MAGIC);
    encoded.push(fields.len() as u8);
    for field in fields {
        let length = u32::try_from(field.len()).map_err(|_| "Native field is too large")?;
        encoded.extend_from_slice(&length.to_be_bytes());
        encoded.extend_from_slice(&field);
    }
    Ok(encoded)
}

fn read_bytes(env: &mut JNIEnv, bytes: JByteArray) -> Result<Vec<u8>, String> {
    env.convert_byte_array(bytes)
        .map_err(|error| error.to_string())
}

fn return_bytes(mut env: JNIEnv, result: Result<Vec<u8>, String>) -> jbyteArray {
    match result {
        Ok(bytes) => env
            .byte_array_from_slice(&bytes)
            .map(|array| array.into_raw())
            .unwrap_or(ptr::null_mut()),
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", error);
            ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_se_apothictech_eutherping_crypto_vodozemac_VodozemacNative_createAccount(
    env: JNIEnv,
    _class: JClass,
) -> jbyteArray {
    return_bytes(env, create_account())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_se_apothictech_eutherping_crypto_vodozemac_VodozemacNative_createPreKeyPublication(
    mut env: JNIEnv,
    _class: JClass,
    account: JByteArray,
) -> jbyteArray {
    let result = read_bytes(&mut env, account)
        .and_then(|account| create_pre_key_publication(&account))
        .and_then(encode_fields);
    return_bytes(env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_se_apothictech_eutherping_crypto_vodozemac_VodozemacNative_establishOutbound(
    mut env: JNIEnv,
    _class: JClass,
    account: JByteArray,
    publication: JByteArray,
) -> jbyteArray {
    let result = read_bytes(&mut env, account).and_then(|account| {
        read_bytes(&mut env, publication)
            .and_then(|publication| establish_outbound(&account, &publication))
            .and_then(encode_fields)
    });
    return_bytes(env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_se_apothictech_eutherping_crypto_vodozemac_VodozemacNative_encrypt(
    mut env: JNIEnv,
    _class: JClass,
    account: JByteArray,
    session: JByteArray,
    plaintext: JByteArray,
) -> jbyteArray {
    let result = read_bytes(&mut env, account).and_then(|account| {
        read_bytes(&mut env, session).and_then(|session| {
            read_bytes(&mut env, plaintext)
                .and_then(|plaintext| encrypt(&account, &session, &plaintext))
                .and_then(encode_fields)
        })
    });
    return_bytes(env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_se_apothictech_eutherping_crypto_vodozemac_VodozemacNative_decrypt(
    mut env: JNIEnv,
    _class: JClass,
    account: JByteArray,
    session: JByteArray,
    ciphertext: JByteArray,
) -> jbyteArray {
    let result = read_bytes(&mut env, account).and_then(|account| {
        read_bytes(&mut env, session).and_then(|session| {
            read_bytes(&mut env, ciphertext)
                .and_then(|ciphertext| decrypt(&account, &session, &ciphertext))
                .and_then(encode_fields)
        })
    });
    return_bytes(env, result)
}

#[cfg(test)]
mod tests {
    use super::{
        create_account, create_pre_key_publication, decrypt, encrypt, establish_outbound,
        verify_publication,
    };

    #[test]
    fn ratchet_roundtrip_reload_out_of_order_and_fail_closed() {
        let alice = create_account().expect("Alice account");
        let bob = create_account().expect("Bob account");
        let bob_publication = create_pre_key_publication(&bob).expect("Bob publication");
        let bob = bob_publication[0].clone();
        let mut alice_session =
            establish_outbound(&alice, &bob_publication[1]).expect("Alice session")[0].clone();

        let initial = encrypt(&alice, &alice_session, b"first").expect("initial message");
        alice_session = initial[0].clone();
        let bob_initial = decrypt(&bob, &[], &initial[1]).expect("Bob initial decrypt");
        let bob = bob_initial[0].clone();
        let mut bob_session = bob_initial[1].clone();
        assert_eq!(bob_initial[2], b"first");

        let reply = encrypt(&bob, &bob_session, b"reply").expect("reply");
        bob_session = reply[0].clone();
        let alice_reply = decrypt(&alice, &alice_session, &reply[1]).expect("Alice reply decrypt");
        alice_session = alice_reply[1].clone();
        assert_eq!(alice_reply[2], b"reply");

        let second = encrypt(&alice, &alice_session, b"second").expect("second");
        alice_session = second[0].clone();
        let third = encrypt(&alice, &alice_session, b"third").expect("third");
        let third_decrypted = decrypt(&bob, &bob_session, &third[1]).expect("third decrypt");
        bob_session = third_decrypted[1].clone();
        assert_eq!(third_decrypted[2], b"third");
        let second_decrypted = decrypt(&bob, &bob_session, &second[1]).expect("second decrypt");
        bob_session = second_decrypted[1].clone();
        assert_eq!(second_decrypted[2], b"second");
        assert!(decrypt(&bob, &bob_session, &second[1]).is_err());

        let mut tampered = bob_publication[1].clone();
        *tampered.last_mut().expect("signature byte") ^= 1;
        assert!(verify_publication(&tampered).is_err());
        println!(
            "publication={} initial={} session={} alice_state={} bob_state={}",
            bob_publication[1].len(),
            initial[1].len(),
            reply[1].len(),
            alice_session.len(),
            bob_session.len(),
        );
    }
}
