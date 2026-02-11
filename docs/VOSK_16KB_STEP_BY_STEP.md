# Step-by-step: Build 16 KB Vosk and run the app

The script now **builds only arm64-v8a** (the ABI required for 16 KB / Play Store), so it’s faster and avoids x86/NDK issues. Use **NDK 27** for the most reliable build.

---

## What you need to do (summary)

1. **Android Studio:** Install **NDK 27** (e.g. 27.0.12077973).
2. **WSL/Ubuntu:** Install build tools (`build-essential`, `cmake`, `autoconf`, `automake`, `libtool`).
3. **WSL:** Delete the old build folder, set `ANDROID_NDK_HOME` to NDK 27, run the script.
4. **Android Studio:** Sync, build, and run the app.

---

## Step 1: Install NDK 27 (Android Studio)

1. Open **Android Studio** and the **TherapyTimer** project.
2. **File → Settings** (Mac: **Android Studio → Preferences**).
3. **Languages & Frameworks → Android SDK** → **SDK Tools** tab.
4. Enable **Show Package Details** (bottom right).
5. Under **NDK (Side by side)**:
   - Select **27.0.12077973** (or another 27.x).
   - Click **Apply** and wait for the install.
6. Note your **Android SDK location** at the top (e.g. `C:\Users\spyxa\AppData\Local\Android\Sdk`).  
   NDK 27 will be at: `Sdk\ndk\27.0.12077973` (or the exact version you installed).

---

## Step 2: Install build tools in WSL (Ubuntu)

1. Open **WSL** (Ubuntu from the Start menu or run `wsl`).
2. Run:
   ```bash
   sudo apt update
   sudo apt install -y build-essential cmake autoconf automake libtool
   ```
3. Enter your Ubuntu password when asked. Wait until it finishes.

---

## Step 3: Run the Vosk build in WSL

1. **Go to your project** (use your Windows username if it’s not `spyxa`):
   ```bash
   cd /mnt/c/Users/spyxa/AndroidStudioProjects/TherapyTimer
   ```

2. **Remove the old build folder** (so the script does a clean build with NDK 27):
   ```bash
   rm -rf .vosk-api-build
   ```

3. **Set the NDK path to NDK 27** (change the version if you installed a different 27.x):
   ```bash
   export ANDROID_NDK_HOME=/mnt/c/Users/spyxa/AppData/Local/Android/Sdk/ndk/27.0.12077973
   ```
   If your SDK is elsewhere, use that path with `/mnt/c/...` and the correct `ndk/27.x` folder.

4. **Run the build script:**
   ```bash
   chmod +x scripts/build-vosk-16kb.sh
   ./scripts/build-vosk-16kb.sh
   ```
   The first run takes **about 30–60 minutes**. Let it finish.

5. **When it succeeds** you’ll see:
   ```text
   Done. 16 KB libvosk.so is at: .../app/src/main/jniLibs/arm64-v8a/libvosk.so
   Build and run the app in Android Studio as usual.
   ```

---

## Step 4: Build and run the app in Android Studio

1. In **Android Studio**, confirm the file exists:  
   **app → src → main → jniLibs → arm64-v8a → libvosk.so**
2. **File → Sync Project with Gradle Files**
3. **Build → Make Project** (or click Run).
4. Run the app on a device or emulator as usual.

The app will use the 16 KB–aligned `libvosk.so`; no other changes are needed.

---

## Quick reference

| Step | Where | What to do |
|------|--------|------------|
| 1 | Android Studio | Install NDK 27 (Settings → Android SDK → SDK Tools → NDK 27.0.12077973) |
| 2 | WSL | `sudo apt update && sudo apt install -y build-essential cmake autoconf automake libtool` |
| 3a | WSL | `cd /mnt/c/Users/spyxa/AndroidStudioProjects/TherapyTimer` |
| 3b | WSL | `rm -rf .vosk-api-build` |
| 3c | WSL | `export ANDROID_NDK_HOME=/mnt/c/Users/spyxa/AppData/Local/Android/Sdk/ndk/27.0.12077973` |
| 3d | WSL | `./scripts/build-vosk-16kb.sh` |
| 4 | Android Studio | Sync → Make Project → Run app |

---

## If something goes wrong

- **“make: command not found”** → Run Step 2 again (install build tools).
- **“ANDROID_NDK_HOME is not a directory”** → Install NDK 27 in Android Studio and use the path to the `ndk/27.x` folder. In PowerShell you can list versions with:  
  `dir "C:\Users\spyxa\AppData\Local\Android\Sdk\ndk"`
- **“android-incdir ... existing directory”** or build errors → Make sure you’re using **NDK 27** and that you ran `rm -rf .vosk-api-build` before the script.
