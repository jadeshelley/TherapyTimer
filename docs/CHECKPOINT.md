# Therapy Timer — Checkpoint (current state)

**Date:** February 2025  
**Version:** 1.17 (versionCode 17)  
**Application ID:** `com.jadeshelley.therapytimer`

---

## Build & platform

- **minSdk:** 24  
- **targetSdk / compileSdk:** 36  
- **Kotlin + Jetpack Compose**  
- **Voice:** Vosk (on-device), model `model-en-us` (small English), from assets or download  
- **Billing:** Play Billing for Pro in-app purchase (see `docs/PLAY_STORE_GUIDE.md`)

---

## Features

- **Basic mode:** Single exercise, configurable duration (seconds), simple rep count.
- **Custom mode:** Multi-exercise routines (name, duration, repeats per exercise). Create/edit routines; progress per exercise; chip UI for exercise selection.
- **Timer:** Countdown per rep; when it hits zero: notification sound → spoken/file count (1–20) → optional exercise-end sound → “finished” clips for routine complete.
- **Voice commands (Vosk):** Start, Next, Restart, Finish, Reset. Voice matching strictness: Strict / Medium / Relaxed (Settings).
  - **Medium:** Fuzzy “next” + alias **max**.
  - **Relaxed:** Fuzzy “next” + aliases **max**, **mixed**, **mags**.
- **Audio:** Confirmation beep (ToneGenerator); notification sound (user-pickable, default `end_bell`); count 1–20 from `assets/numbers/`; exercise-end and “finished” from assets. **Mic:** `VOICE_RECOGNITION` (with no fallback to MIC in code if it fails).
- **Theme:** Follows system light/dark (no in-app theme toggle). Material 3; dynamic color on Android 12+.
- **Settings:** Mute all sounds, notification sound picker, voice matching strictness, basic-mode duration, TTS/voice prefs, instructions, version display, Pro purchase/restore.

---

## Temporary / remove later

- **Voice routine log:** `VoiceRoutineLog.kt` + wiring in `MainActivity`: logs every phrase heard per exercise and which triggered an action; on routine complete writes a timestamped `.txt` under app external `Documents/TherapyTimerVoiceLogs/`. Marked TEMPORARY — remove class and all references when no longer needed.

---

## Sounds

- **Bundled:** Count files (1–20), `exercise_end.mp3`, `finished/*.mp3`, default notification `R.raw.end_bell`.
- **Device:** Optional notification sound if user picks one in Settings (ringtone picker). Confirmation beep is system `ToneGenerator`. TTS uses device engine.

---

## Play / release

- Closed testing (e.g. Google Group testers); no automatic “update available” notification — announce new builds yourself.
- Play Console warnings (no deobfuscation file, no native debug symbols) are non-blocking; they only affect crash report readability.

---

## Docs

- `docs/PLAY_STORE_GUIDE.md` — Play account, test upload, Pro IAP.
- `docs/VOSK_MODEL_SETUP.md` — Voice model setup.
- `docs/privacy-policy.html` — Privacy policy.
