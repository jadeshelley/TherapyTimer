# Building Vosk with 16 KB Page Size for Android

Google Play requires native libraries to support 16 KB page size for apps targeting Android 15+ (from November 2025). The prebuilt `vosk-android` AAR does not ship a 16 KB–aligned `libvosk.so`. This project builds Vosk from source with the correct linker flag and uses that library instead.

## Quick steps

1. **Install Android NDK r25 or newer** (e.g. via Android Studio: SDK Manager → SDK Tools → NDK).
2. **Use Linux, macOS, or WSL** — the Vosk build is Bash-based (on Windows, use WSL).
3. **Run the build script** from the project root:

   ```bash
   export ANDROID_NDK_HOME=/path/to/your/ndk   # e.g. $HOME/Android/Sdk/ndk/27.0.12077973
   chmod +x scripts/build-vosk-16kb.sh
   ./scripts/build-vosk-16kb.sh
   ```

4. The script clones vosk-api (v0.3.38), patches the Android build to add `-Wl,-z,max-page-size=16384` for **arm64-v8a**, builds, and copies **libvosk.so** to `app/src/main/jniLibs/arm64-v8a/libvosk.so`.
5. **Build the app** in Android Studio as usual. The app’s packaging is set to prefer this `libvosk.so` over the one from the vosk-android AAR.

## Prerequisites

- **ANDROID_NDK_HOME** — Android NDK r25 or newer; **NDK 27 is recommended** (e.g. 27.0.12077973) for a reliable build.
- **Git** — to clone vosk-api.
- **Build tools** — the Vosk build uses `make`, `cmake`, `autoreconf`, and a C/C++ toolchain (the NDK supplies the Android toolchain).
- **Bash** — run on Linux, macOS, or WSL on Windows.

## What the script does

- Clones [alphacep/vosk-api](https://github.com/alphacep/vosk-api) at tag **v0.3.38** (to match the app’s `vosk-android` dependency).
- Applies `scripts/vosk-16kb-build.patch` to `android/lib/build-vosk.sh`:
  - Adds `PAGESIZE_LDFLAGS="-Wl,-z,max-page-size=16384"` for **arm64-v8a**.
  - Adds empty `PAGESIZE_LDFLAGS` for other ABIs and passes `${PAGESIZE_LDFLAGS}` into the Vosk link step.
- Runs the full Android build (OpenBLAS, CLAPACK, OpenFST, Kaldi, vosk-api). This can take a while.
- Copies the built **arm64-v8a** `libvosk.so` into `app/src/main/jniLibs/arm64-v8a/`.

## App build configuration

- **`app/build.gradle.kts`** sets `packaging.jniLibs.pickFirsts += "**/libvosk.so"` so the app’s 16 KB–aligned `libvosk.so` is used instead of the one from the `vosk-android` AAR when both are present.
- The app still depends on `implementation(libs.vosk.android)` for the Java/Kotlin API; only the native library is replaced.

## Optional: build only arm64-v8a

The script runs the full multi-ABI build. To save time you could modify the Vosk build script to build only `arm64-v8a` (e.g. change the `for arch in ...` line). For Play and most devices, shipping a 16 KB–aligned **arm64-v8a** `libvosk.so` is what matters; other ABIs from the AAR can remain as-is if you still include them.

## Verifying 16 KB alignment

After building, you can check the shared library:

```bash
# Linux/macOS (with readelf from NDK or binutils)
readelf -l app/src/main/jniLibs/arm64-v8a/libvosk.so | grep -A1 LOAD
```

Segment alignment should be **0x4000** (16384) for LOAD segments. Alternatively, build an APK and use Android Studio’s **Build → Analyze APK** and/or the Play Console’s pre-launch report to confirm 16 KB compatibility.
