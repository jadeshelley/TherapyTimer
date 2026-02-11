# Prebuilt OpenBLAS for Android arm64-v8a

If the in-tree OpenBLAS build fails in WSL (e.g. "clang: no input files"), you can use a prebuilt library and skip building OpenBLAS.

## Layout

Place the prebuilt files so that this directory contains:

- `lib/libopenblas.a` – static library (required)
- `include/` – C/BLAS headers (required for later build stages)

Example:

```
scripts/prebuilt/openblas-arm64-v8a/
  lib/
    libopenblas.a
  include/
    cblas.h
    openblas_config.h
    ... (other headers from OpenBLAS install)
```

## How to obtain the prebuilt (no Linux machine needed)

### Option A: Docker on Windows (recommended if you have no Linux)

Install [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/), then in **PowerShell** or **Command Prompt**:

```powershell
# One-time: build OpenBLAS in a Linux container and copy output to your project.
# Run from project root (e.g. cd C:\Users\You\AndroidStudioProjects\TherapyTimer).
docker run --rm -v "%cd%":/out ubuntu:22.04 bash -c "apt-get update -qq && apt-get install -y -qq git make gcc unzip curl && curl -L -o ndk.zip https://dl.google.com/android/repository/android-ndk-r27-linux.zip && unzip -q ndk.zip && NDK=\$(ls -d android-ndk-*) && export ANDROID_NDK_HOME=\$(pwd)/\$NDK && export CC=\$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang && export AR=\$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar && git clone -b v0.3.13 --depth 1 https://github.com/OpenMathLib/OpenBLAS && cd OpenBLAS && make TARGET=ARMV8 ONLY_CBLAS=1 USE_THREAD=0 NUM_THREADS=1 CC=\"\$CC -target aarch64-linux-android21\" AR=\$AR HOSTCC=gcc -j4 && make install PREFIX=/out/scripts/prebuilt/openblas-arm64-v8a"
```

Run that from your **project root** (e.g. `C:\Users\...\TherapyTimer`). It will create `lib/` and `include/` inside `scripts/prebuilt/openblas-arm64-v8a/`. Then run `./scripts/build-vosk-16kb.sh` in WSL as usual.

**Note:** The first run downloads the Docker image and NDK (~1.5 GB). If the base image fails, use the script below instead.

### Option B: GitHub Actions (no Docker, no Linux)

Use GitHub’s servers to build OpenBLAS once, then download the result and put it in your project.

**Step 1: Make sure the workflow is in your repo**

The workflow file is `.github/workflows/build-openblas-arm64.yml`. If it’s not on GitHub yet, commit and push it using one of the methods below.

**How to commit and push in Android Studio (step-by-step):**

1. **Open your project**  
   Open the TherapyTimer project in Android Studio (File → Open, then choose the TherapyTimer folder).

2. **Create a Git repository (if you only see “Share project on” and “Create git repository”)**  
   - Click **Version Control** in the top menu bar.  
   - Click **Create git repository…**  
   - In the dialog, select your **project root folder** (the TherapyTimer folder).  
   - Click **OK**.  
   The project is now a Git repo. The Version Control menu may change and show more options.

3. **Open the Commit screen**  
   Use the **keyboard shortcut**: **Ctrl+Alt+K** (Windows/Linux) or **Cmd+Option+K** (Mac).  
   That should open the Commit window.  
   If it doesn’t: look for a **“Commit”** tab or icon on the **left edge** of the window (in the vertical tool bar), or try **Version Control** in the menu again—after creating the repo, **Commit…** may appear there.

4. **See what will be committed**  
   In the Commit window you’ll see a list of **changed files** with checkboxes.  
   You should see at least:
   - `.github/workflows/build-openblas-arm64.yml` (new or modified)
   - Maybe other files under `scripts/` or elsewhere  
   Leave **checked** every file you want in this commit (at least the workflow file).

5. **Write a commit message**  
   In the **“Commit Message”** box at the bottom, type something like:  
   `Add GitHub Actions workflow to build OpenBLAS for Android`

6. **Commit**  
   Click the blue **“Commit”** button (bottom right).  
   If Android Studio says **“Commit contains problems: X errors and Y warnings”**, it’s reporting issues it found in the project (often in other modules or checkpoints). You can still commit:
   - Click **“Commit anyway”** or **“Commit”** (the dialog usually offers this).
   - To commit only the workflow and avoid including other changed files, **uncheck** everything in the file list except **`.github/workflows/build-openblas-arm64.yml`**, then commit.  
   If a dialog appears about “Unversioned files” or “Commit and Push”, choose **Commit** (or **Commit and Push** if you want to push in one step).  
   Your changes are now saved in Git on your computer.

7. **Push to GitHub**  
   - If you have a **green up-arrow** icon in the toolbar, click it for **Push**.  
   - Otherwise press **Ctrl+Shift+K** (Windows/Linux) or **Cmd+Shift+K** (Mac), or use **Version Control** → **Push…** if it’s there.  
   In the Push dialog, pick the branch (e.g. **main**) and click **Push**.  
   If it asks for a **remote** or **URL**, enter your GitHub repo (e.g. `https://github.com/YourUsername/TherapyTimer.git`). Log in with your GitHub account if asked.  
   When it finishes, your workflow file is on GitHub.

8. **If you prefer “Share project on GitHub”**  
   If you see **Version Control** → **Share project on…** (or **Share on GitHub**), you can use that instead: it will create a repo on GitHub and push your project in one go. Choose **GitHub**, sign in if needed, pick a repo name (e.g. TherapyTimer), and follow the prompts. Then your workflow file will be on GitHub.

**Step 2: Open the Actions tab**

- In a browser, go to your repo on GitHub (e.g. `https://github.com/YourUsername/TherapyTimer`).
- Click the **Actions** tab in the top bar.

**Step 3: Run the workflow**

- In the left sidebar, click **“Build OpenBLAS for Android arm64”** (under “All workflows” or the workflow list).
- If you don’t see it, make sure the branch that has `.github/workflows/build-openblas-arm64.yml` is selected (e.g. `main`).
- Click the **“Run workflow”** button on the right (dropdown next to it if needed).
- Choose the branch to run on (usually `main`) and click the green **“Run workflow”**.
- The run will appear in the list; it takes a few minutes (downloads NDK and builds OpenBLAS).

**Step 4: Download the artifact**

- When the run finishes, click on the run (e.g. the top entry with a green checkmark).
- On the run page, scroll to the **Artifacts** section at the bottom.
- Click **openblas-arm64-v8a** to download a zip file (e.g. `openblas-arm64-v8a.zip`).

**Step 5: Put the files in your project**

- Unzip the downloaded file. You should get a folder that contains two folders: **lib** and **include**.
- Your Vosk build expects them inside the project at:
  - `scripts/prebuilt/openblas-arm64-v8a/lib/`
  - `scripts/prebuilt/openblas-arm64-v8a/include/`
- Do one of the following:
  - **Windows (Explorer):** Open the unzipped folder, then copy the **lib** and **include** folders into:
    - `TherapyTimer\scripts\prebuilt\openblas-arm64-v8a\`
    - So that you have:
      - `TherapyTimer\scripts\prebuilt\openblas-arm64-v8a\lib\libopenblas.a`
      - `TherapyTimer\scripts\prebuilt\openblas-arm64-v8a\include\` (with files like `cblas.h`, etc.)
  - **WSL / terminal:** If the zip is in your project folder (e.g. `Downloads` moved to project root):
    ```bash
    cd /mnt/c/Users/YourName/AndroidStudioProjects/TherapyTimer
    unzip -o openblas-arm64-v8a.zip -d scripts/prebuilt/openblas-arm64-v8a
    ```
    (If the zip already has a single top-level folder that contains `lib` and `include`, unzip to a temp dir and then copy that folder’s `lib` and `include` into `scripts/prebuilt/openblas-arm64-v8a/`.)

**Step 6: Run the Vosk build**

- In WSL, from the project root:
  ```bash
  cd /mnt/c/Users/YourName/AndroidStudioProjects/TherapyTimer
  ./scripts/build-vosk-16kb.sh
  ```
- You should see a line like **“Using prebuilt OpenBLAS from: ...”** and the build will skip the in-tree OpenBLAS step.

### Option C: Build on native Linux (or WSL + Docker)

On a normal Linux machine (or WSL with Docker) with the Android NDK (Linux version) installed:

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk-r27
export CC=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang
export AR=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar
git clone -b v0.3.13 --single-branch https://github.com/OpenMathLib/OpenBLAS
cd OpenBLAS
make TARGET=ARMV8 ONLY_CBLAS=1 USE_THREAD=0 NUM_THREADS=1 \
  CC="$CC -target aarch64-linux-android21" \
  AR=$AR HOSTCC=gcc -j4
make install PREFIX=/tmp/openblas-arm64-v8a
# Copy /tmp/openblas-arm64-v8a/lib and include/ into scripts/prebuilt/openblas-arm64-v8a/
```

### Option D: Use OPENBLAS_PREBUILT_DIR

If your prebuilt is elsewhere, set the environment variable before running the build:

```bash
export OPENBLAS_PREBUILT_DIR=/absolute/path/to/dir/with/lib/and/include
./scripts/build-vosk-16kb.sh
```

The directory must contain `lib/libopenblas.a` and `include/` with headers.
