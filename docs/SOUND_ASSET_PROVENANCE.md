# EutherPing terminal sound provenance

The six terminal effects shipped in EutherPing 0.8.21 were generated locally on
2026-08-12 with `stabilityai/stable-audio-3-small-sfx`, seed `82461`, 12 steps,
and two-second source durations. No third-party recordings were used.

The source prompts described these isolated effects:

- a tiny dry analog terminal relay tick;
- a compact retro-futuristic radio transmitter chirp;
- an encrypted-message mechanical seal with a soft digital shimmer;
- a clean glassy cryptographic verification ping;
- a restrained identity-warning double pulse with relay chatter;
- a short descending terminal-error thunk with a muted static click.

All prompts excluded music, melody, rhythm, vocals, speech, ambience, drones,
long reverberation, loud bass, distortion, and harsh noise. The selected clips
were trimmed, faded, filtered, gain-balanced, converted to mono 44.1 kHz Ogg
Vorbis, and stored in `app/src/main/res/raw/`. Playback is deliberately quiet
and uses Android's sonification audio usage. Android notification-channel audio
is separate and remains under the user's system settings.

The model is distributed under the Stability AI Community License included with
the locally accepted model package. Preserve this provenance when replacing or
redistributing the generated assets.
