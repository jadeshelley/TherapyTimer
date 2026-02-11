# TTS debugging – Logcat filter and what to capture

## Filter to use in Logcat

**Tag filter:** `TTS`

- In Android Studio Logcat: set the filter dropdown to **"Edit Filter Configuration"** (or create a new logcat filter), then set **Log Tag** to: `TTS`
- Or in the Logcat search/filter box type: `tag:TTS`
- Or filter by package: `package:com.example.therapytimer` and then search for `TTS` in the message

Using **tag:TTS** or **Log Tag = TTS** is enough to see all TTS debug lines.

## What the app logs (so you can check it’s working)

1. **resolveEnginePackage** – Which engine the app is *trying* to use:
   - `[DEBUG] resolveEnginePackage: savedPref='...'` – value from your saved preference (auto / com.google.android.tts / com.samsung.SMT)
   - `[DEBUG] resolveEnginePackage: result='...'` – final engine chosen (null = system default)

2. **initializeTTS** – Whether that engine actually starts:
   - `[DEBUG] initializeTTS: requesting engine='...'` – engine we requested
   - `[DEBUG] initializeTTS: SUCCESS engine='...' voicesReported=N currentVoice=...` – init succeeded and how many voices the engine reports
   - `[DEBUG] initializeTTS: FAILED engine='...' status=...` – init failed

3. **setEnginePackage** – When you change engine in Settings:
   - `[DEBUG] setEnginePackage: userSelected='...' savedAndUsing='...'` – what you tapped vs what was saved

4. **getAvailableVoices** – When you open “Change” for Voice for Numbers:
   - `[DEBUG] getAvailableVoices: engine='...' totalFromEngine=N names=[...]` – which engine we think we’re using and the first 8 voice names from that engine
   - `[DEBUG] getAvailableVoices: filtered en/es count=N names=[...]` – after filtering to English/Spanish
   - Then one line per voice: `voice: <name> locale=... quality=... neural=true/false`

5. **speak** – When a number or “Good job” is spoken:
   - `[DEBUG] speak: text='...' voice='...' engine='...'` – text spoken, selected voice name, and which engine we think we’re using

## What to capture and share

1. Reproduce the issue (e.g. switch between Samsung TTS and Google TTS, then open “Change” for Voice for Numbers).
2. In Logcat, filter by tag **TTS**.
3. Copy the relevant stretch of logs (from app launch or from when you opened Settings / changed engine / opened voice list) and share it.

From that we can see:
- Whether the saved engine changes when you select Samsung vs Google.
- Whether init SUCCESS or FAILED for each engine.
- Whether the voice list (names and count) changes when you switch engines – if it doesn’t, the system may be ignoring the engine parameter.
