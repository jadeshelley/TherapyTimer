# Speech Recognition – When It Turns On/Off

## When listening STARTS

| Trigger | Where | What happens |
|--------|--------|----------------|
| **App start** (after RECORD_AUDIO granted) | `MainActivity.initializeVoiceRecognition()` | If voice control is enabled → `startListening()`. |
| **startListening()** | `VoiceRecognitionManager` | Sets `isActive = true`, then **100 ms delay**, then `startRecognitionSession()`. |
| **startRecognitionSession()** | Same | Builds intent, calls `speechRecognizer.startListening(intent)`, sets `_isListening = true`. **Only then** is the mic actually listening. |
| **Return from Settings** | `MainActivity.onSettingsClosed` | If voice enabled → `startListening()`. |
| **User toggles voice ON** | `MainActivity.onToggleVoiceControl` | `startListening()`. |
| **App resumes** (e.g. from background) | `MainActivity.onResume` | If voice enabled → `startListening()`. |

So there is always at least a **100 ms** delay after “turn on” before the first session actually starts.

---

## When listening STOPS (session ends)

Android’s `SpeechRecognizer` runs **one session at a time**. Each session ends when:

1. **onEndOfSpeech** – User stopped talking (silence detected).
2. **onResults** – Final result delivered (user said something that was recognized).
3. **onError** – No match, timeout, network error, etc.

In all three cases the code sets `_isListening = false` and then calls **scheduleRestart(delay)**.

---

## The restart loop (why there are gaps)

After every session end, the app **restarts** a new session so it can keep listening:

| Event | Delay used | Effect |
|-------|------------|--------|
| **onEndOfSpeech** | 100 ms | 100 ms gap before next session starts. |
| **onResults** | 100 ms | 100 ms gap after handling the command. |
| **onError** (NO_MATCH, SPEECH_TIMEOUT) | 200 ms | 200 ms gap. |
| **onError** (RECOGNIZER_BUSY) | 300 ms | 300 ms gap. |
| **onError** (other) | 200 ms (or recreate + 200 ms) | 200 ms (or more) gap. |

During that delay, **no new audio is being recognized**. If the user says a command in that window (e.g. right after saying “next” or right after the timer beep), it can be **missed**.

---

## When the user is most likely to need a command

1. **Right after a rep completes** – Timer beep + TTS (“one”, “two”, …) play. User often says “next” or “start” **during or right after** that. The recognizer may be in **onResults** or **onError** from the previous utterance (or from the TTS), so it’s in a restart delay and not listening.
2. **Right after saying a command** – User says “next”; **onResults** fires, **scheduleRestart(100)** runs; for 100 ms nothing is listening. If they say “next” again quickly, it can be missed.
3. **After returning from Settings / resume** – First session only starts after the initial 100 ms delay; anything said in that 100 ms is missed.

---

## Current behavior that helps

- **Partial results** – Commands are processed in `onPartialResults` as well as `onResults`, so the app can react before the final result (faster, and sometimes catches the word even if the session ends right after).
- **Long silence timeouts** – Intent uses `EXTRA_SPEECH_INPUT_*_SILENCE_LENGTH_MILLIS = Integer.MAX_VALUE` so the engine doesn’t end the session just because of silence; it keeps listening until result or error.
- **Debounce (500 ms)** – Same command text within 500 ms is ignored, which avoids double triggers from partial + final or from echo.

---

## Summary

- Listening **starts** only after a 100 ms delay when turning on / resuming / returning from Settings.
- After **every** “session end” (speech end, result, or error), there is a **100–300 ms** restart delay where the mic is **not** listening.
- Commands are most likely to be **missed** in those gaps, especially right after a rep completes or right after the user (or TTS) just spoke.

To improve “hearing the user when they need it,” the main lever is **shortening or removing the restart delay** where safe, and optionally **starting the first session sooner** (e.g. no or smaller initial delay in `startListening()`).
