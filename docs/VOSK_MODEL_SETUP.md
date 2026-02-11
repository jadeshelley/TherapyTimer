# Vosk Voice Recognition Model Setup

Therapy Timer uses **Vosk** for on-device voice commands (start, next, done, restart, reset). No Google or network is used.

## Automatic download (default)

If you **don’t** add a model to assets, the app will **download** the small English model (~40 MB) from the internet the first time it needs it (when the Vosk model is loaded in the background). You need a network connection once; after that the model is stored on the device and voice commands work offline.

No manual steps are required.

## Optional: bundle the model in the app

To avoid the first-time download (e.g. for offline installs or to speed up first launch), you can bundle the model in assets:

1. **Download** the small English model:  
   https://alphacephei.com/vosk/models → **vosk-model-small-en-us-0.15**

2. **Unzip** it so you have a folder with `am/`, `conf/`, `graph/`, etc.

3. **Add to assets:**  
   Create `app/src/main/assets/model-en-us/` and copy the **contents** of that folder (the inner files and subfolders) into `assets/model-en-us/`.

4. **Rebuild.**  
   The app will use the bundled model and won’t download it.

## If the model isn’t ready yet

If you turn on voice control before the model has finished loading or downloading, the app will log that the model isn’t ready. Wait a bit (or ensure network for the first run) and try again.
