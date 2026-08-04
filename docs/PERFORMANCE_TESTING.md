# EutherPing performance testing

EutherPing records a bounded local performance log with no remote telemetry.
The log contains event timestamps, durations, frame totals, janky-frame totals,
app/device versions, and numeric row counts. It deliberately excludes phone
numbers, contact names, message text, attachment names, cryptographic material,
IP addresses, and server endpoints.

## User report

1. Open **System**.
2. Under **Local performance diagnostics**, tap **Export report**.
3. Save the text file with Android's document picker.

The retained on-device log is capped at 256 KiB and then compacted to the last
300 events. It is removed with app data. Export occurs only after the user
chooses a destination.

Recorded events:

- `cold_first_frame`: process creation to the first posted activity frame.
- `warm_first_frame`: a recreated activity's first frame in an existing process.
- `warm_resume_frame`: background-to-foreground time to the next frame.
- `conversation_index`: Android provider index read plus visible lane counts.
- `conversation_page`: bounded provider page read, lane, limit, and visible count.
- `conversation_cache_hit`: in-memory bounded page reuse.
- `mms_preview_decode` and `mms_preview_cache_hit`: bounded image decoding/cache.
- `render_frames`: JankStats UI-frame duration, total frames, and janky frames.

## Repeatable phone comparison

Use the same APK, conversation, and actions on Samsung and GrapheneOS. Before
each run, leave the message database unchanged and note whether the run is cold
or warm.

Cold run:

```sh
adb shell am force-stop se.apothictech.eutherping
adb shell monkey -p se.apothictech.eutherping -c android.intent.category.LAUNCHER 1
```

Warm run:

```sh
adb shell input keyevent KEYCODE_HOME
adb shell monkey -p se.apothictech.eutherping -c android.intent.category.LAUNCHER 1
```

For each run:

1. Wait for **Active signals**.
2. Open the same conversation.
3. Scroll from the newest messages through at least two 30-row page increments.
4. Scroll past the same carrier MMS images twice; the second pass should report
   cache hits instead of new decodes.
5. Background and reopen the app once.
6. Export the report from System.

Compare medians over at least five runs. Treat a single outlier as diagnostic,
not a result. Record separately:

- cold and warm first-frame milliseconds;
- conversation-index and first-page milliseconds;
- each next-page load;
- MMS decode and cache-hit milliseconds;
- `janky / frames` during the conversation scroll.

## Baseline Profile

The `baselineprofile` module uses AndroidX Macrobenchmark's
`BaselineProfileRule`. Its startup rule is kept separate from the broader
critical-user journey. The latter grants the emulator the SMS role, creates a
temporary 35-message provider-backed conversation, opens the inbox and
conversation, scrolls, opens System, then removes the fixture.

Generate profiles on a rooted emulator or supported API 33+ device:

```sh
./gradlew :app:generateBaselineProfile
```

Generated release and startup profiles are stored under
`app/src/release/generated/baselineProfiles/`. A small manual profile in
`app/src/main/baseline-prof.txt` keeps carrier-MMS provider and preview paths
compiled for physical runs containing real image MMS. Always benchmark the
result on the target physical phones; successful profile generation alone is
not proof of a speed improvement.
