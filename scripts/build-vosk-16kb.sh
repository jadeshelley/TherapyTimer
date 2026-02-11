#!/usr/bin/env bash
# Build Vosk Android libvosk.so with 16 KB page size for arm64-v8a only (Play Store requirement).
#
# *** RECOMMENDED: Avoid building. Use Vosk 0.3.70+ and JNA 5.16+ (see gradle/libs.versions.toml).
#     The official vosk-android AAR from 0.3.70 is built with 16 KB alignment; no custom build needed.
#     If you must build (e.g. older Vosk), try native Linux or Docker — WSL can hit llvm-ar/argv limits.
#
# Run on Linux or macOS (or WSL on Windows). Requires NDK r25+ (NDK 27 recommended) and ANDROID_NDK_HOME.
#
# Usage:
#   export ANDROID_NDK_HOME=/path/to/ndk/27.0.12077973
#   ./scripts/build-vosk-16kb.sh
#
# If OpenBLAS in-tree build fails (e.g. "no input files" in WSL), use a prebuilt:
#   Put lib/libopenblas.a (and include/) in scripts/prebuilt/openblas-arm64-v8a/
#   or set OPENBLAS_PREBUILT_DIR to that directory. See scripts/prebuilt/openblas-arm64-v8a/README.md.
#
# Output: app/src/main/jniLibs/arm64-v8a/libvosk.so (16 KB–aligned)
#
# Debug llvm-ar "archive name must be specified" (WSL long command line):
#   VOSK_DEBUG_AR=1 ./scripts/build-vosk-16kb.sh
# Then check android/lib/build/ar-debug.log and the ninja -v output for the exact argv passed to ar.

set -e

VOSK_VERSION="${VOSK_VERSION:-v0.3.38}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_JNILIBS="$PROJECT_ROOT/app/src/main/jniLibs"
BUILD_SCRIPT_PATH="android/lib/build-vosk.sh"

# On WSL, build under a short path to avoid "clang: no input files" (long /mnt/c/... paths break NDK/clang)
USE_SHORT_BUILD=0
REPO_DIR="${VOSK_BUILD_DIR:-$(pwd)/.vosk-api-build}"
if [ "$(uname -s)" = "Linux" ] && [[ "$PROJECT_ROOT" == /mnt/* ]]; then
  REPO_DIR="${VOSK_BUILD_DIR:-$HOME/.therapytimer-vosk-build}"
  USE_SHORT_BUILD=1
fi

# Check build tools (required for Vosk build)
if ! command -v make &>/dev/null; then
  echo "ERROR: 'make' not found. Install build tools in WSL/Ubuntu with:"
  echo "  sudo apt update && sudo apt install -y build-essential cmake autoconf automake libtool"
  exit 1
fi
if ! command -v cmake &>/dev/null; then
  echo "ERROR: 'cmake' not found. Install with:"
  echo "  sudo apt update && sudo apt install -y build-essential cmake autoconf automake libtool"
  exit 1
fi
if ! command -v autoreconf &>/dev/null; then
  echo "ERROR: 'autoreconf' not found. Install with:"
  echo "  sudo apt update && sudo apt install -y build-essential cmake autoconf automake libtool"
  exit 1
fi

if [ -z "${ANDROID_NDK_HOME}" ]; then
  echo "ERROR: ANDROID_NDK_HOME is not set."
  echo "Use NDK 27 (recommended). Example:"
  echo "  export ANDROID_NDK_HOME=/mnt/c/Users/spyxa/AppData/Local/Android/Sdk/ndk/27.0.12077973"
  echo "Install NDK 27 in Android Studio: Settings → Android SDK → SDK Tools → NDK 27.0.12077973"
  exit 1
fi

if [ ! -d "$ANDROID_NDK_HOME" ]; then
  echo "ERROR: ANDROID_NDK_HOME is not a directory: $ANDROID_NDK_HOME"
  exit 1
fi

# WSL/Linux cannot run Windows .exe compilers (Exec format error). We must use the Linux NDK.
# If the user pointed to the Windows NDK (only windows-x86_64), download the Linux NDK and use it.
NDK_PREBUILT=""
if [ -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64" ]; then
  NDK_PREBUILT="linux-x86_64"
elif [ "$(uname -s)" = "Linux" ] && [ -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/windows-x86_64" ]; then
  echo "You're building from WSL/Linux but ANDROID_NDK_HOME points to the Windows NDK (Windows .exe compilers cannot run in WSL)."
  echo "Using or downloading the Linux NDK r27 (one-time, ~600 MB)..."
  NDK_LINUX_CACHE="${NDK_LINUX_CACHE:-$PROJECT_ROOT/.ndk-linux-r27}"
  # Use existing extracted NDK if present
  if [ -d "$NDK_LINUX_CACHE/toolchains/llvm/prebuilt/linux-x86_64" ]; then
    ANDROID_NDK_HOME="$NDK_LINUX_CACHE"
    NDK_PREBUILT="linux-x86_64"
    export ANDROID_NDK_HOME
    echo "Using cached Linux NDK at $ANDROID_NDK_HOME"
  elif [ -d "$NDK_LINUX_CACHE/android-ndk-r27/toolchains/llvm/prebuilt/linux-x86_64" ]; then
    ANDROID_NDK_HOME="$NDK_LINUX_CACHE/android-ndk-r27"
    NDK_PREBUILT="linux-x86_64"
    export ANDROID_NDK_HOME
    echo "Using cached Linux NDK at $ANDROID_NDK_HOME"
  fi
  if [ -z "$NDK_PREBUILT" ] && [ ! -d "$NDK_LINUX_CACHE/toolchains/llvm/prebuilt/linux-x86_64" ]; then
    mkdir -p "$NDK_LINUX_CACHE"
    NDK_ZIP="$NDK_LINUX_CACHE/ndk-r27-linux.zip"
    if [ ! -f "$NDK_ZIP" ]; then
      (cd "$NDK_LINUX_CACHE" && wget -q --show-progress -O ndk-r27-linux.zip https://dl.google.com/android/repository/android-ndk-r27-linux.zip) || \
      (cd "$NDK_LINUX_CACHE" && curl -L -o ndk-r27-linux.zip https://dl.google.com/android/repository/android-ndk-r27-linux.zip) || \
      { echo "ERROR: Could not download NDK. Get it from https://developer.android.com/ndk/downloads and extract to $NDK_LINUX_CACHE"; exit 1; }
    fi
    echo "Extracting Linux NDK..."
    (cd "$NDK_LINUX_CACHE" && (unzip -q -o ndk-r27-linux.zip 2>/dev/null || python3 -c "import zipfile; zipfile.ZipFile('ndk-r27-linux.zip').extractall('.')")) || \
    { echo "ERROR: Could not extract. Install unzip (e.g. sudo apt install unzip) or ensure python3 is available."; exit 1; }
    NDK_DIR=$(cd "$NDK_LINUX_CACHE" && ls -d android-ndk-* 2>/dev/null | head -1)
    if [ -n "$NDK_DIR" ] && [ -d "$NDK_LINUX_CACHE/$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64" ]; then
      NDK_LINUX_CACHE="$NDK_LINUX_CACHE/$NDK_DIR"
    fi
  fi
  if [ -d "$NDK_LINUX_CACHE/toolchains/llvm/prebuilt/linux-x86_64" ]; then
    ANDROID_NDK_HOME="$NDK_LINUX_CACHE"
    NDK_PREBUILT="linux-x86_64"
    export ANDROID_NDK_HOME
    echo "Using Linux NDK at $ANDROID_NDK_HOME"
  elif [ -n "$NDK_PREBUILT" ]; then
    # Already set from cached android-ndk-r27 subdir above; nothing to do
    :
  else
    echo "ERROR: Linux NDK download or extract failed. Manually download from:"
    echo "  https://developer.android.com/ndk/downloads"
    echo "Extract it in WSL and set: export ANDROID_NDK_HOME=/path/to/extracted/android-ndk-r27"
    exit 1
  fi
fi
if [ -z "$NDK_PREBUILT" ]; then
  if [ -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/windows-x86_64" ]; then
    echo "ERROR: Building from WSL/Linux requires the Linux NDK, not the Windows one."
    echo "Run the script again; it will try to download the Linux NDK automatically."
    echo "Or download from https://developer.android.com/ndk/downloads and set ANDROID_NDK_HOME to the extracted Linux NDK."
  else
    echo "ERROR: No NDK prebuilt found at $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/"
    echo "Expected linux-x86_64 (for Linux/WSL) or darwin-x86_64/darwin-arm64 (for Mac)."
  fi
  exit 1
fi
export NDK_PREBUILT

echo "Building Vosk $VOSK_VERSION with 16 KB page size (arm64-v8a only)"
echo "  ANDROID_NDK_HOME=$ANDROID_NDK_HOME"
echo "  Build dir: $REPO_DIR"
if [ "$USE_SHORT_BUILD" = 1 ]; then
  echo "  (using short path to avoid WSL long-path /mnt/c/... issues)"
fi
echo "  App jniLibs: $APP_JNILIBS"

# Clone or update vosk-api
if [ ! -d "$REPO_DIR" ]; then
  git clone --depth 1 --branch "$VOSK_VERSION" https://github.com/alphacep/vosk-api.git "$REPO_DIR"
else
  (cd "$REPO_DIR" && git fetch origin tag "$VOSK_VERSION" --no-tags 2>/dev/null; git checkout "$VOSK_VERSION" 2>/dev/null || true)
fi

BUILD_SCRIPT="$REPO_DIR/$BUILD_SCRIPT_PATH"
if [ ! -f "$BUILD_SCRIPT" ]; then
  echo "ERROR: Build script not found: $BUILD_SCRIPT"
  exit 1
fi

# Exit on first error in inner build so we don't waste time after a fatal failure
if ! grep -q '^set -e' "$BUILD_SCRIPT"; then
  echo "Patching: build-vosk.sh to exit on first error (set -e)..."
  sed -i.bak_ee '/^set -x$/a\
set -e
' "$BUILD_SCRIPT"
fi

# 0) Use correct NDK prebuilt (linux-x86_64 or windows-x86_64 for WSL)
if ! grep -q 'NDK_PREBUILT:-' "$BUILD_SCRIPT"; then
  echo "Patching: use NDK prebuilt folder (linux or windows for WSL)..."
  case "$(uname -s)" in
    Darwin) sed -i.bak0 's/\${OS_NAME}-x86_64/\${NDK_PREBUILT:-&}/g' "$BUILD_SCRIPT" ;;
    *)      sed -i.bak0 's/\${OS_NAME}-x86_64/\${NDK_PREBUILT:-&}/g' "$BUILD_SCRIPT" ;;
  esac
fi

# 1) Only build arm64-v8a (avoid x86/i686 and NDK path issues)
if ! grep -q "for arch in arm64-v8a" "$BUILD_SCRIPT"; then
  echo "Patching: build only arm64-v8a..."
  case "$(uname -s)" in
    Darwin) sed -i.bak 's/for arch in armeabi-v7a arm64-v8a x86_64 x86/for arch in arm64-v8a/' "$BUILD_SCRIPT" ;;
    *)      sed -i.bak 's/for arch in armeabi-v7a arm64-v8a x86_64 x86/for arch in arm64-v8a/' "$BUILD_SCRIPT" ;;
  esac
fi

# 1b) Use real clang/clang++ with -target for arm64-v8a (NDK wrapper drops args from OpenBLAS c_check / CMake / libtool)
if ! grep -q 'ANDROID_TOOLCHAIN_PATH/bin/clang' "$BUILD_SCRIPT"; then
  echo "Patching: use clang with -target for arm64-v8a (avoid wrapper dropping input files)..."
  case "$(uname -s)" in Darwin) SED_I="sed -i.bak1b" ;; *) SED_I="sed -i.bak1b" ;; esac
  $SED_I 's|CC=aarch64-linux-android21-clang|CC=$ANDROID_TOOLCHAIN_PATH/bin/clang|' "$BUILD_SCRIPT"
  $SED_I 's|CXX=aarch64-linux-android21-clang++|CXX=$ANDROID_TOOLCHAIN_PATH/bin/clang++|' "$BUILD_SCRIPT"
  $SED_I '/CXX=\$ANDROID_TOOLCHAIN_PATH\/bin\/clang++/{n;s/ARCHFLAGS=""/ARCHFLAGS="-target aarch64-linux-android21"/}' "$BUILD_SCRIPT"
  # OpenBLAS target build needs -target flag when using clang (not wrapper)
  $SED_I 's|CC=\$CC HOSTCC=gcc ARM_SOFTFP|CC=$CC CFLAGS="$ARCHFLAGS" HOSTCC=gcc ARM_SOFTFP|' "$BUILD_SCRIPT"
fi

# 1c) For arm64-v8a, use wrapper scripts (pre-installed by this script with correct printf '%s\n'); inner script only uses them if present
if ! grep -q 'clang-arm64.sh' "$BUILD_SCRIPT"; then
  echo "Patching: use compiler wrapper scripts for arm64-v8a (fix c_check no input files)..."
  case "$(uname -s)" in Darwin) SED_I="sed -i.bak1c" ;; *) SED_I="sed -i.bak1c" ;; esac
  $SED_I '/^esac$/a\
# 16KB build: use pre-installed response-file wrappers (or generate fallback)\
if [ \"$arch\" = \"arm64-v8a\" ]; then\
  mkdir -p $WORKDIR_BASE/bin\
  if [ ! -x \"$WORKDIR_BASE/bin/clang-arm64.sh\" ]; then\
    { echo '\''#!/bin/sh'\''; echo '\''tmp=$(mktemp) || exit 1'\''; echo '\''for a in \"$@\"; do printf '\'''\''%s\\n'\'''\'' \"$a\" >> \"$tmp\"; done'\''; echo '\''exec '\''\"$ANDROID_TOOLCHAIN_PATH\"'\''/bin/clang -target aarch64-linux-android21 \"@$tmp\"'\''; echo '\''rm -f \"$tmp\"'\''; } > $WORKDIR_BASE/bin/clang-arm64.sh\
    { echo '\''#!/bin/sh'\''; echo '\''tmp=$(mktemp) || exit 1'\''; echo '\''for a in \"$@\"; do printf '\'''\''%s\\n'\'''\'' \"$a\" >> \"$tmp\"; done'\''; echo '\''exec '\''\"$ANDROID_TOOLCHAIN_PATH\"'\''/bin/clang++ -target aarch64-linux-android21 \"@$tmp\"'\''; echo '\''rm -f \"$tmp\"'\''; } > $WORKDIR_BASE/bin/clang++-arm64.sh\
    chmod +x $WORKDIR_BASE/bin/clang-arm64.sh $WORKDIR_BASE/bin/clang++-arm64.sh\
  fi\
  ln -sf \"\$ANDROID_TOOLCHAIN_PATH/bin/clang\" \"\$WORKDIR_BASE/ndk-clang\"\
  ln -sf \"\$ANDROID_TOOLCHAIN_PATH/bin/clang++\" \"\$WORKDIR_BASE/ndk-clang++\"\
  CC=$WORKDIR_BASE/bin/clang-arm64.sh\
  CXX=$WORKDIR_BASE/bin/clang++-arm64.sh\
  ARCHFLAGS=""\
fi
' "$BUILD_SCRIPT"
fi

# 2) Add 16 KB linker flag (we only build arm64-v8a so set it once and use in EXTRA_LDFLAGS)
if ! grep -q "max-page-size=16384" "$BUILD_SCRIPT"; then
  echo "Patching: add 16 KB page size linker flag for arm64-v8a..."
  case "$(uname -s)" in
    Darwin) SED_I="sed -i.bak2" ;;
    *)      SED_I="sed -i.bak2" ;;
  esac
  # Add PAGESIZE_LDFLAGS after OPENFST_VERSION= so it's set for our single-arch build
  $SED_I '/^OPENFST_VERSION=1.8.0$/a\
PAGESIZE_LDFLAGS="-Wl,-z,max-page-size=16384"
' "$BUILD_SCRIPT"
  # Use it in the make line
  $SED_I 's|EXTRA_LDFLAGS="-llog -static-libstdc++ -Wl,-soname,libvosk.so"|EXTRA_LDFLAGS="-llog -static-libstdc++ -Wl,-soname,libvosk.so ${PAGESIZE_LDFLAGS}"|' "$BUILD_SCRIPT"
fi

# 3) OpenBLAS: use host linker (bfd) for getarch, NDK ld.lld for target (fixes "lld is a generic driver")
if ! grep -q 'HOST_LDFLAGS="-fuse-ld=bfd"' "$BUILD_SCRIPT"; then
  echo "Patching: OpenBLAS host/target linker (lld) fix..."
  case "$(uname -s)" in Darwin) SED_I="sed -i.bak3" ;; *) SED_I="sed -i.bak3" ;; esac
  # After OpenBLAS clone, patch Makefile.prebuild and Makefile.system so HOST_LDFLAGS reaches getarch
  $SED_I '/git clone -b v0.3.13 --single-branch https:\/\/github.com\/xianyi\/OpenBLAS/a\
sed -i '\''s#$(HOSTCC) $(HOST_CFLAGS) $(EXFLAGS) -o#$(HOSTCC) $(HOST_CFLAGS) $(HOST_LDFLAGS) $(EXFLAGS) -o#g'\'' OpenBLAS/Makefile.prebuild \&\& sed -i '\''s#$(HOSTCC) -I. $(HOST_CFLAGS) -o#$(HOSTCC) -I. $(HOST_CFLAGS) $(HOST_LDFLAGS) -o#g'\'' OpenBLAS/Makefile.prebuild\
sed -i '\''s/HOSTCC="$(HOSTCC)" HOST_CFLAGS/HOSTCC="\$(HOSTCC)" HOST_LDFLAGS="\$(HOST_LDFLAGS)" HOST_CFLAGS/'\'' OpenBLAS/Makefile.system
' "$BUILD_SCRIPT"
  # Add HOST_LDFLAGS and LDFLAGS to the make -C OpenBLAS line
  $SED_I 's|make -C OpenBLAS TARGET=\$BLAS_ARCH ONLY_CBLAS=1 AR=\$AR CC=\$CC HOSTCC=gcc ARM_SOFTFP_ABI=1 USE_THREAD=0 NUM_THREADS=1 -j4|make -C OpenBLAS TARGET=$BLAS_ARCH ONLY_CBLAS=1 AR=$AR CC=$CC HOSTCC=gcc HOST_LDFLAGS="-fuse-ld=bfd" LDFLAGS="-fuse-ld=ld.lld" ARM_SOFTFP_ABI=1 USE_THREAD=0 NUM_THREADS=1 -j1|' "$BUILD_SCRIPT"
  # Prepend /usr/bin to PATH so getarch links with system ld (not NDK lld); quote PATH for spaces (e.g. "Program Files")
  $SED_I 's|make -C OpenBLAS |env "PATH=/usr/bin:\$PATH" make -C OpenBLAS |g' "$BUILD_SCRIPT"
fi
# If we already patched OpenBLAS with unquoted env, fix it
if grep -q 'env PATH=/usr/bin:\$PATH make -C OpenBLAS' "$BUILD_SCRIPT" 2>/dev/null; then
  case "$(uname -s)" in Darwin) SED_I="sed -i.bak3q" ;; *) SED_I="sed -i.bak3q" ;; esac
  $SED_I 's|env PATH=/usr/bin:\$PATH make -C OpenBLAS|env "PATH=/usr/bin:\$PATH" make -C OpenBLAS|g' "$BUILD_SCRIPT"
fi

# 3b) OpenBLAS: use wrapper CC=\$CC with CFLAGS=-target (wrapper uses response file so clang gets all args).
if grep -q 'make -C OpenBLAS' "$BUILD_SCRIPT" 2>/dev/null; then
  echo "Patching: OpenBLAS make use wrapper CC + CFLAGS=-target..."
  case "$(uname -s)" in Darwin) SED_I="sed -i.bak3b" ;; *) SED_I="sed -i.bak3b" ;; esac
  # Ensure we use wrapper and pass -target (don't force direct clang path)
  $SED_I '/make -C OpenBLAS/s|CC=\$ANDROID_TOOLCHAIN_PATH\/bin\/clang HOSTCC=gcc|CC=\$CC CFLAGS="-target aarch64-linux-android21" HOSTCC=gcc|' "$BUILD_SCRIPT"
  $SED_I '/make -C OpenBLAS/s|CC=\$ANDROID_TOOLCHAIN_PATH\/bin\/clang|CC=\$CC CFLAGS="-target aarch64-linux-android21"|' "$BUILD_SCRIPT"
  if ! grep -q 'make -C OpenBLAS.*CFLAGS="-target aarch64-linux-android21"' "$BUILD_SCRIPT" 2>/dev/null; then
  $SED_I '/make -C OpenBLAS/s|CC=\$CC HOSTCC=gcc|CC=\$CC CFLAGS="-target aarch64-linux-android21" HOSTCC=gcc|' "$BUILD_SCRIPT"
fi
# OpenBLAS: build with -j1 to avoid wrapper/race issues in WSL (no input files)
if grep -q 'make -C OpenBLAS.*-j[0-9]' "$BUILD_SCRIPT" 2>/dev/null; then
  case "$(uname -s)" in Darwin) SED_I="sed -i.bak3j" ;; *) SED_I="sed -i.bak3j" ;; esac
  $SED_I '/make -C OpenBLAS/s/-j[0-9]/-j1/g' "$BUILD_SCRIPT"
fi
fi

# 4) CLAPACK/CMake: quote empty CMAKE_C_FLAGS; use $CC (full path when 1b applied)
if ! grep -q 'DCMAKE_C_FLAGS="\$ARCHFLAGS"' "$BUILD_SCRIPT"; then
  echo "Patching: CLAPACK CMake C_FLAGS and compiler..."
  case "$(uname -s)" in Darwin) SED_I="sed -i.bak4" ;; *) SED_I="sed -i.bak4" ;; esac
  $SED_I 's|-DCMAKE_C_FLAGS=\$ARCHFLAGS -DCMAKE_C_COMPILER_TARGET=\$HOST|-DCMAKE_C_FLAGS="$ARCHFLAGS" -DCMAKE_C_COMPILER_TARGET=$HOST|' "$BUILD_SCRIPT"
  $SED_I 's|-DCMAKE_C_COMPILER=\$CC|-DCMAKE_C_COMPILER=$CC|' "$BUILD_SCRIPT"
fi
# 4b) CLAPACK: skip TryCompile + disable response files so compiler gets direct args (fixes "no input files" in WSL)
if ! grep -q 'CMAKE_C_COMPILER_WORKS' "$BUILD_SCRIPT" 2>/dev/null; then
  echo "Patching: CLAPACK skip compiler check (WSL)..."
  case "$(uname -s)" in Darwin) SED_I="sed -i.bak4b" ;; *) SED_I="sed -i.bak4b" ;; esac
  $SED_I 's|-DCMAKE_C_COMPILER=\$CC|-DCMAKE_C_COMPILER=$CC -DCMAKE_C_COMPILER_WORKS=1 -DCMAKE_CXX_COMPILER_WORKS=1|' "$BUILD_SCRIPT"
fi
if ! grep -q 'CMAKE_C_USE_RESPONSE_FILE_FOR_OBJECTS=' "$BUILD_SCRIPT" 2>/dev/null; then
  echo "Patching: CLAPACK compiler response file setting..."
  case "$(uname -s)" in Darwin) SED_I="sed -i.bak4r" ;; *) SED_I="sed -i.bak4r" ;; esac
  $SED_I 's|-DCMAKE_C_COMPILER=\$CC|-DCMAKE_C_COMPILER=$CC -DCMAKE_C_USE_RESPONSE_FILE_FOR_OBJECTS=0 -DCMAKE_CXX_USE_RESPONSE_FILE_FOR_OBJECTS=0|' "$BUILD_SCRIPT"
fi
# 4c) CLAPACK: use response-file wrapper so clang gets args via @file (direct argv loses them in WSL/Ninja)
if ! grep -q 'DCMAKE_C_COMPILER=\$ANDROID_TOOLCHAIN_PATH' "$BUILD_SCRIPT" 2>/dev/null; then
  echo "Patching: CLAPACK use raw clang for cmake..."
  case "$(uname -s)" in Darwin) SED_I="sed -i.bak4c" ;; *) SED_I="sed -i.bak4c" ;; esac
  $SED_I 's|-DCMAKE_C_COMPILER=\$CC|-DCMAKE_C_COMPILER=$ANDROID_TOOLCHAIN_PATH/bin/clang|' "$BUILD_SCRIPT"
  $SED_I 's|-DCMAKE_C_FLAGS="\$ARCHFLAGS"|-DCMAKE_C_FLAGS="-target aarch64-linux-android21 $ARCHFLAGS"|' "$BUILD_SCRIPT"
fi
# 4d) CLAPACK: use Ninja instead of Make (Ninja passes compiler args correctly; Makefile generator loses them in WSL)
if grep -q 'make -j 8 -C F2CLIBS/libf2c' "$BUILD_SCRIPT" 2>/dev/null && ! grep -q '-G Ninja' "$BUILD_SCRIPT" 2>/dev/null; then
  if command -v ninja &>/dev/null; then
    echo "Patching: CLAPACK use Ninja generator (fix WSL no input files)..."
    case "$(uname -s)" in Darwin) SED_I="sed -i.bak4d" ;; *) SED_I="sed -i.bak4d" ;; esac
    $SED_I 's| -DCMAKE_CROSSCOMPILING=True \.\.| -DCMAKE_CROSSCOMPILING=True -G Ninja -DCMAKE_NINJA_FORCE_RESPONSE_FILE=1 ..|' "$BUILD_SCRIPT"
    $SED_I 's|make -j 8 -C F2CLIBS/libf2c|ninja ${NINJA_FLAGS:-}|' "$BUILD_SCRIPT"
    $SED_I 's|make -j 8 -C BLAS/SRC|ninja ${NINJA_FLAGS:-}|' "$BUILD_SCRIPT"
    $SED_I 's|make -j 8 -C SRC|ninja ${NINJA_FLAGS:-}|' "$BUILD_SCRIPT"
  else
    echo "Note: install ninja (sudo apt install ninja-build) for CLAPACK to avoid WSL 'no input files'; will try Make."
  fi
fi
# 4e) CLAPACK: do NOT use CMAKE_NINJA_FORCE_RESPONSE_FILE for archiver (CMake's @rsp format breaks llvm-ar "archive name must be specified").
# We use an ar wrapper (4e2) that receives the long command line and writes objects to a temp @file itself.
if grep -q 'CMAKE_NINJA_FORCE_RESPONSE_FILE=1' "$BUILD_SCRIPT" 2>/dev/null; then
  echo "Patching: CLAPACK remove Ninja force response file (use ar wrapper instead)..."
  case "$(uname -s)" in Darwin) SED_I="sed -i.bak4e" ;; *) SED_I="sed -i.bak4e" ;; esac
  $SED_I 's| -DCMAKE_NINJA_FORCE_RESPONSE_FILE=1||g' "$BUILD_SCRIPT"
fi
# 4f) So we can run ninja -v when VOSK_DEBUG_AR=1 (existing "ninja" lines may lack NINJA_FLAGS)
if grep -q 'ninja' "$BUILD_SCRIPT" 2>/dev/null && ! grep -q 'NINJA_FLAGS' "$BUILD_SCRIPT" 2>/dev/null; then
  case "$(uname -s)" in Darwin) SED_I="sed -i.bak4f" ;; *) SED_I="sed -i.bak4f" ;; esac
  $SED_I 's|^ninja$|ninja ${NINJA_FLAGS:-}|' "$BUILD_SCRIPT"
fi

# --- DEBUG: why CLAPACK/llvm-ar fails (no guessing) ---
echo ""
echo "=== DEBUG: CLAPACK/Ninja/llvm-ar ==="
echo "Patch 4d condition: script has 'make -j 8 -C F2CLIBS/libf2c'?"
grep -q 'make -j 8 -C F2CLIBS/libf2c' "$BUILD_SCRIPT" 2>/dev/null && echo "  YES" || echo "  NO"
echo "Patch 4d condition: script does NOT have '-G Ninja'?"
! grep -q '-G Ninja' "$BUILD_SCRIPT" 2>/dev/null && echo "  YES (so 4d would run)" || echo "  NO (script already has -G Ninja)"
echo "Script has 'CMAKE_NINJA_FORCE_RESPONSE_FILE=1'?"
grep -q 'CMAKE_NINJA_FORCE_RESPONSE_FILE=1' "$BUILD_SCRIPT" 2>/dev/null && echo "  YES" || echo "  NO"
echo "Script has '-G Ninja' (grep -E to avoid -G as option)?"
grep -qE ' -G Ninja|G Ninja' "$BUILD_SCRIPT" 2>/dev/null && echo "  YES" || echo "  NO"
echo "Full cmake line(s) for CLAPACK (from build-vosk.sh):"
grep -nE 'cmake.*clapack|cmake.*CROSSCOMPILING|cmake.*CMAKE_AR| -G Ninja|Ninja.*\.\.' "$BUILD_SCRIPT" 2>/dev/null | head -20
echo "CLAPACK cmake block (from first cmake to ..):"
sed -n '/^[[:space:]]*cmake.*CMAKE_C_FLAGS/,/\.\./p' "$BUILD_SCRIPT" 2>/dev/null | head -15
echo "=== END DEBUG ==="
echo ""

# 5) OpenFST: configure uses host gcc (needs -fuse-ld=bfd); make must use NDK linker
if ! grep -q 'LDFLAGS="-fuse-ld=bfd" ./configure' "$BUILD_SCRIPT"; then
  echo "Patching: OpenFST configure/make linker..."
  case "$(uname -s)" in Darwin) SED_I="sed -i.bak5" ;; *) SED_I="sed -i.bak5" ;; esac
  $SED_I 's|CXX=\$CXX CXXFLAGS="\$ARCHFLAGS -O3 -DFST_NO_DYNAMIC_LINKING" ./configure|CXX=$CXX CXXFLAGS="$ARCHFLAGS -O3 -DFST_NO_DYNAMIC_LINKING" LDFLAGS="-fuse-ld=bfd" ./configure|' "$BUILD_SCRIPT"
  $SED_I 's|make -j 8$|make -j 8 LDFLAGS="-fuse-ld=ld.lld"|' "$BUILD_SCRIPT"
fi

# Create jniLibs dir so cp in build script does not fail
mkdir -p "$REPO_DIR/android/lib/src/main/jniLibs/arm64-v8a"

# OpenBLAS clone: use OpenMathLib mirror (same project, avoids GitHub 500 from xianyi/OpenBLAS)
if grep -q 'github.com/xianyi/OpenBLAS' "$BUILD_SCRIPT" 2>/dev/null; then
  case "$(uname -s)" in Darwin) SED_I="sed -i.bak0ob" ;; *) SED_I="sed -i.bak0ob" ;; esac
  $SED_I 's|https://github.com/xianyi/OpenBLAS|https://github.com/OpenMathLib/OpenBLAS|g' "$BUILD_SCRIPT"
fi

# OpenBLAS c_check: replace with stub that writes fixed Android arm64+CLANG config (no compiler
# run). Fixes "clang: error: no input files" in WSL when the real c_check runs the compiler.
if [ -f "$SCRIPT_DIR/openblas_c_check_android_arm64.pl" ]; then
  mkdir -p "$REPO_DIR/scripts"
  cp "$SCRIPT_DIR/openblas_c_check_android_arm64.pl" "$REPO_DIR/scripts/"
  # Remove old fix_c_check.pl line if present (we use stub only)
  if grep -q 'fix_c_check.pl' "$BUILD_SCRIPT" 2>/dev/null; then
    case "$(uname -s)" in Darwin) SED_I="sed -i.bak1c2r" ;; *) SED_I="sed -i.bak1c2r" ;; esac
    $SED_I '/perl ../../../../scripts\/fix_c_check.pl OpenBLAS\/c_check/d' "$BUILD_SCRIPT"
  fi
  if ! grep -q 'openblas_c_check_android_arm64.pl' "$BUILD_SCRIPT"; then
    echo "Patching: OpenBLAS c_check → stub (no compiler, Android arm64 CLANG)..."
    case "$(uname -s)" in Darwin) SED_I="sed -i.bak1c2" ;; *) SED_I="sed -i.bak1c2" ;; esac
    # Use # as delimiter so path ../../../../ in appended text is not parsed as sed commands
    $SED_I '\#git clone -b v0.3.13 --single-branch https://github.com/[^/]*/OpenBLAS#a\
cp ../../../../scripts/openblas_c_check_android_arm64.pl OpenBLAS/c_check\
sed -i '\''/ifeq (\$(OSNAME), Android)/,/endif/ s/EXTRALIB += -lm$/EXTRALIB += -lm\\nCCOMMON_OPT += -target aarch64-linux-android21/'\'' OpenBLAS/Makefile.system\
sed -i '\''s/\\(.\\)\\(\$(CC)\\) \\(\$(CFLAGS)\\) -c \\(.*\\) \$< -o/\\1\\2 -c \$< \\3 \\4 -o/g'\'' OpenBLAS/interface/Makefile
' "$BUILD_SCRIPT"
  fi
fi

# OpenBLAS prebuilt: skip in-tree build (avoids "no input files" in WSL) when prebuilt is provided
if [ -z "${OPENBLAS_PREBUILT_DIR:-}" ]; then
  if [ -f "$SCRIPT_DIR/prebuilt/openblas-arm64-v8a/lib/libopenblas.a" ]; then
    OPENBLAS_PREBUILT_DIR="$(cd "$SCRIPT_DIR/prebuilt/openblas-arm64-v8a" && pwd)"
  fi
elif [ ! -f "${OPENBLAS_PREBUILT_DIR}/lib/libopenblas.a" ]; then
  echo "WARNING: OPENBLAS_PREBUILT_DIR is set but lib/libopenblas.a not found; will build OpenBLAS in-tree."
  OPENBLAS_PREBUILT_DIR=""
fi
if [ -n "$OPENBLAS_PREBUILT_DIR" ]; then
  echo "Using prebuilt OpenBLAS from: $OPENBLAS_PREBUILT_DIR"
  export OPENBLAS_PREBUILT_DIR
  # Use NDK target-prefixed compiler so CLAPACK/CMake get a proper compiler (avoids script wrapper "no input files")
  case "$(uname -s)" in Darwin) SED_I="sed -i.bakprebuiltcc" ;; *) SED_I="sed -i.bakprebuiltcc" ;; esac
  $SED_I 's|CC=\$ANDROID_TOOLCHAIN_PATH/bin/clang|CC=$ANDROID_TOOLCHAIN_PATH/bin/aarch64-linux-android21-clang|g' "$BUILD_SCRIPT"
  $SED_I 's|CXX=\$ANDROID_TOOLCHAIN_PATH/bin/clang++|CXX=$ANDROID_TOOLCHAIN_PATH/bin/aarch64-linux-android21-clang++|g' "$BUILD_SCRIPT"
  $SED_I 's|CC=\$WORKDIR_BASE/bin/clang-arm64.sh|CC=$ANDROID_TOOLCHAIN_PATH/bin/aarch64-linux-android21-clang|g' "$BUILD_SCRIPT"
  $SED_I 's|CXX=\$WORKDIR_BASE/bin/clang++-arm64.sh|CXX=$ANDROID_TOOLCHAIN_PATH/bin/aarch64-linux-android21-clang++|g' "$BUILD_SCRIPT"
  if ! grep -q 'OPENBLAS_PREBUILT_DIR' "$BUILD_SCRIPT" 2>/dev/null; then
    echo "Patching: use OpenBLAS prebuilt when OPENBLAS_PREBUILT_DIR is set..."
    case "$(uname -s)" in Darwin) SED_I="sed -i.bakprebuilt" ;; *) SED_I="sed -i.bakprebuilt" ;; esac
    $SED_I '\#git clone -b v0.3.13 --single-branch https://github.com/[^/]*/OpenBLAS#i\
if [ -n "$OPENBLAS_PREBUILT_DIR" ] \&\& [ -f "$OPENBLAS_PREBUILT_DIR/lib/libopenblas.a" ]; then cp -r "$OPENBLAS_PREBUILT_DIR"/* "$WORKDIR/local/"; else
' "$BUILD_SCRIPT"
    $SED_I '/make -C OpenBLAS install PREFIX=\$WORKDIR\/local/a\
fi
' "$BUILD_SCRIPT"
  fi
fi

# Pre-install compiler wrappers for arm64-v8a (inner script uses WORKDIR_BASE/bin = build/bin)
WRAPPER_BIN="$REPO_DIR/android/lib/build/bin"
NDK_CLANG="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_PREBUILT/bin/clang"
NDK_CLANGXX="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_PREBUILT/bin/clang++"
mkdir -p "$WRAPPER_BIN"
if [ -n "${OPENBLAS_PREBUILT_DIR:-}" ]; then
  # Response-file wrappers so CMake/CLAPACK get correct args (avoids "no input files" when compiler is a script)
  if [ -f "$SCRIPT_DIR/clang-arm64-cmake.sh.in" ]; then
    sed "s|CLANG_PATH_PLACEHOLDER|$NDK_CLANG|g" "$SCRIPT_DIR/clang-arm64-cmake.sh.in" | sed 's/\r$//' > "$WRAPPER_BIN/clang-arm64.sh"
    sed "s|CLANG_PATH_PLACEHOLDER|$NDK_CLANGXX|g" "$SCRIPT_DIR/clang++-arm64-cmake.sh.in" | sed 's/\r$//' > "$WRAPPER_BIN/clang++-arm64.sh"
  else
    printf '%s\n' '#!/bin/sh' "exec \"$NDK_CLANG\" -target aarch64-linux-android21 \"\$@\"" | sed 's/\r$//' > "$WRAPPER_BIN/clang-arm64.sh"
    printf '%s\n' '#!/bin/sh' "exec \"$NDK_CLANGXX\" -target aarch64-linux-android21 \"\$@\"" | sed 's/\r$//' > "$WRAPPER_BIN/clang++-arm64.sh"
  fi
  chmod +x "$WRAPPER_BIN/clang-arm64.sh" "$WRAPPER_BIN/clang++-arm64.sh"
elif [ -f "$SCRIPT_DIR/clang-arm64-response.sh.in" ]; then
  sed "s|CLANG_PATH_PLACEHOLDER|$NDK_CLANG|g" "$SCRIPT_DIR/clang-arm64-response.sh.in" | sed 's/\r$//' > "$WRAPPER_BIN/clang-arm64.sh"
  sed "s|CLANG_PATH_PLACEHOLDER|$NDK_CLANGXX|g" "$SCRIPT_DIR/clang++-arm64-response.sh.in" | sed 's/\r$//' > "$WRAPPER_BIN/clang++-arm64.sh"
  chmod +x "$WRAPPER_BIN/clang-arm64.sh" "$WRAPPER_BIN/clang++-arm64.sh"
fi

# When build is on /home but NDK is on /mnt/c, Make/CMake can lose compiler args ("no input files"). Copy toolchain into short path.
TOOLCHAIN_SRC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_PREBUILT"
TOOLCHAIN_COPY="$REPO_DIR/ndk-toolchain"
if [ "$USE_SHORT_BUILD" = 1 ] && [[ "$ANDROID_NDK_HOME" == /mnt/* ]]; then
  if [ ! -x "$TOOLCHAIN_COPY/bin/clang" ] || [ "$TOOLCHAIN_SRC/bin/clang" -nt "$TOOLCHAIN_COPY/bin/clang" ]; then
    echo "Copying NDK toolchain to short path (one-time, ~500 MB) so compiler receives args in WSL..."
    rm -rf "$TOOLCHAIN_COPY"
    cp -a "$TOOLCHAIN_SRC" "$TOOLCHAIN_COPY"
  fi
  export ANDROID_TOOLCHAIN_PATH="$TOOLCHAIN_COPY"
  echo "  Using toolchain at: $ANDROID_TOOLCHAIN_PATH"
fi

# Inner script: use ANDROID_TOOLCHAIN_PATH from env if set (so copied toolchain is used)
if ! grep -q 'ANDROID_TOOLCHAIN_PATH:-\$ANDROID_NDK' "$BUILD_SCRIPT" 2>/dev/null; then
  case "$(uname -s)" in Darwin) SED_I="sed -i.bak4tc" ;; *) SED_I="sed -i.bak4tc" ;; esac
  $SED_I 's|^ANDROID_TOOLCHAIN_PATH=\$ANDROID_NDK_HOME\(.*\)$|ANDROID_TOOLCHAIN_PATH="${ANDROID_TOOLCHAIN_PATH:-$ANDROID_NDK_HOME\1}"|' "$BUILD_SCRIPT"
fi

# CLAPACK: ar wrapper for 80+ args — use xargs -0 to pass object list to llvm-ar (avoids shell argv/IFS issues).
CLAPACK_BUILD_DIR="$REPO_DIR/android/lib/build"
REAL_AR="${ANDROID_TOOLCHAIN_PATH:-$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_PREBUILT}/bin/llvm-ar"
AR_WRAPPER="$CLAPACK_BUILD_DIR/ar-wrapper.sh"
mkdir -p "$CLAPACK_BUILD_DIR"
cat > "$AR_WRAPPER" << 'ARWRAPEOF'
#!/bin/sh
REAL_AR="REAL_AR_PLACEHOLDER"
if [ $# -ge 80 ]; then
  arch="$2"; shift 2
  arch=$(printf '%s' "$arch" | tr -d '\r')
  list=$(mktemp) || exit 1
  for f in "$@"; do printf '%s\n' "$f" | tr -d '\r'; done > "$list"
  first=1
  while [ -s "$list" ]; do
    batch=$(mktemp) || { rm -f "$list"; exit 1; }
    head -50 "$list" | tr '\n' '\0' > "$batch"
    [ ! -s "$batch" ] && { rm -f "$batch" "$list"; break; }
    if [ $first -eq 1 ]; then
      xargs -0 "$REAL_AR" qc "$arch" < "$batch"
      first=0
    else
      xargs -0 "$REAL_AR" q "$arch" < "$batch"
    fi
    ret=$?; rm -f "$batch"
    [ $ret -ne 0 ] && { rm -f "$list"; exit $ret; }
    tail -n +51 "$list" > "${list}.2" 2>/dev/null && mv "${list}.2" "$list" || : > "$list"
  done
  rm -f "$list"
  exit 0
fi
exec "$REAL_AR" "$@"
ARWRAPEOF
sed -i "s|REAL_AR_PLACEHOLDER|$REAL_AR|g" "$AR_WRAPPER"
chmod +x "$AR_WRAPPER"
echo "CLAPACK ar wrapper: $AR_WRAPPER (long argv -> xargs -0 batches of 50)"
# Replace any -DCMAKE_AR=... with wrapper (pattern matches full value so nested ${VAR:-${FOO}} works)
case "$(uname -s)" in Darwin) SED_I="sed -i.bak4ar" ;; *) SED_I="sed -i.bak4ar" ;; esac
$SED_I 's|-DCMAKE_AR=[^ ]*|-DCMAKE_AR=$WORKDIR_BASE/ar-wrapper.sh|' "$BUILD_SCRIPT"

# CLAPACK: on WSL Ninja can lose compiler argv when the compiler is a script. Use a C launcher binary
# so argv is passed through (no shell). Otherwise use script or direct clang.
CLAPACK_BUILD_DIR="$REPO_DIR/android/lib/build"
CLAPACK_LAUNCHER="$CLAPACK_BUILD_DIR/clang-for-clapack"
CLAPACK_CLANG="${ANDROID_TOOLCHAIN_PATH:-$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_PREBUILT}/bin/clang"
if [ "$USE_SHORT_BUILD" = 1 ] && [ -f "$SCRIPT_DIR/clang-for-clapack-launcher.c" ]; then
  # Build launcher with host compiler so Ninja's exec() passes argv correctly
  if [ ! -x "$CLAPACK_LAUNCHER" ] || [ "$SCRIPT_DIR/clang-for-clapack-launcher.c" -nt "$CLAPACK_LAUNCHER" ] || [ "$TOOLCHAIN_COPY/bin/clang" -nt "$CLAPACK_LAUNCHER" ] 2>/dev/null; then
    echo "Building CLAPACK compiler launcher (fix WSL no input files)..."
    mkdir -p "$CLAPACK_BUILD_DIR"
    cc -O2 -o "$CLAPACK_LAUNCHER" "$SCRIPT_DIR/clang-for-clapack-launcher.c" 2>/dev/null || \
    gcc -O2 -o "$CLAPACK_LAUNCHER" "$SCRIPT_DIR/clang-for-clapack-launcher.c"
  fi
  # On /mnt/c, NDK bin/clang is a symlink stored as text — WSL can't exec it. Copy the real ELF
  # (e.g. clang-18) into the build dir on the Linux filesystem so the launcher can exec it.
  if [ -x "$CLAPACK_LAUNCHER" ]; then
    NDK_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_PREBUILT/bin"
    HOST_CLANG_DEST="$CLAPACK_BUILD_DIR/ndk-host-clang"
    NEED_COPY=1
    if [ -x "$HOST_CLANG_DEST" ]; then
      if file "$HOST_CLANG_DEST" | grep -q ELF; then
        NEED_COPY=0
      fi
    fi
    if [ "$NEED_COPY" = 1 ]; then
      REAL_CLANG=""
      if [ -L "$NDK_BIN/clang" ]; then
        REAL_CLANG="$(readlink -f "$NDK_BIN/clang" 2>/dev/null)"
        [ -z "$REAL_CLANG" ] && REAL_CLANG="$NDK_BIN/$(readlink "$NDK_BIN/clang" 2>/dev/null)"
      fi
      if [ -z "$REAL_CLANG" ] || [ ! -f "$REAL_CLANG" ]; then
        # Symlink may be stored as text on /mnt/c; content is often "clang-18" or similar
        TARGET="$(cat "$NDK_BIN/clang" 2>/dev/null)"
        if [ -n "$TARGET" ] && [ -f "$NDK_BIN/$TARGET" ] && file "$NDK_BIN/$TARGET" 2>/dev/null | grep -q ELF; then
          REAL_CLANG="$NDK_BIN/$TARGET"
        fi
      fi
      if [ -z "$REAL_CLANG" ]; then
        for f in clang-18 clang-17 clang-16 clang-15; do
          if [ -f "$NDK_BIN/$f" ] && file "$NDK_BIN/$f" 2>/dev/null | grep -q ELF; then
            REAL_CLANG="$NDK_BIN/$f"
            break
          fi
        done
      fi
      if [ -n "$REAL_CLANG" ] && [ -f "$REAL_CLANG" ]; then
        cp -f "$REAL_CLANG" "$HOST_CLANG_DEST"
        chmod +x "$HOST_CLANG_DEST"
        echo "Copied NDK host clang to Linux path: $HOST_CLANG_DEST"
      else
        echo "WARNING: could not find real clang ELF in $NDK_BIN; launcher may fail."
      fi
    fi
    if [ -x "$HOST_CLANG_DEST" ] && file "$HOST_CLANG_DEST" | grep -q ELF; then
      CLANG_PATH_FOR_LAUNCHER="$HOST_CLANG_DEST"
    else
      CLANG_PATH_FOR_LAUNCHER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_PREBUILT/bin/clang"
    fi
    printf '%s\n' "$CLANG_PATH_FOR_LAUNCHER" > "$CLAPACK_BUILD_DIR/clang-for-clapack.path"
    NDK_SYSROOT="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_PREBUILT/sysroot"
    if [ -d "$NDK_SYSROOT" ]; then
      printf '%s\n' "$NDK_SYSROOT" > "$CLAPACK_BUILD_DIR/clang-for-clapack.sysroot"
      echo "CLAPACK launcher sysroot: $NDK_SYSROOT"
    fi
    # Copied clang can't find its resource dir (stddef.h etc); pass it explicitly
    NDK_PREBUILT_DIR="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_PREBUILT"
    CLANG_RESOURCE_DIR=""
    for sub in lib64/clang lib/clang; do
      [ ! -d "$NDK_PREBUILT_DIR/$sub" ] && continue
      for ver in "$NDK_PREBUILT_DIR/$sub"/*; do
        if [ -d "$ver" ] && [ -f "$ver/include/stddef.h" ]; then
          CLANG_RESOURCE_DIR="$ver"
          break 2
        fi
      done
    done
    if [ -n "$CLANG_RESOURCE_DIR" ]; then
      printf '%s\n' "$CLANG_RESOURCE_DIR" > "$CLAPACK_BUILD_DIR/clang-for-clapack.resource-dir"
      echo "CLAPACK launcher resource-dir: $CLANG_RESOURCE_DIR"
    fi
    echo "CLAPACK launcher will use: $CLANG_PATH_FOR_LAUNCHER"
    file "$CLANG_PATH_FOR_LAUNCHER" 2>/dev/null || true
  fi
  if [ -x "$CLAPACK_LAUNCHER" ]; then
    if grep -q 'ANDROID_TOOLCHAIN_PATH/bin/clang\|clang-for-clapack\.sh' "$BUILD_SCRIPT" 2>/dev/null; then
      echo "Patching: CLAPACK use C launcher (fix WSL no input files)..."
      case "$(uname -s)" in Darwin) SED_I="sed -i.bak4clap" ;; *) SED_I="sed -i.bak4clap" ;; esac
      $SED_I 's|-DCMAKE_C_COMPILER=\$WORKDIR_BASE/clang-for-clapack\.sh|-DCMAKE_C_COMPILER=$WORKDIR_BASE/clang-for-clapack|' "$BUILD_SCRIPT"
      $SED_I 's|-DCMAKE_C_COMPILER=\$ANDROID_TOOLCHAIN_PATH/bin/clang|-DCMAKE_C_COMPILER=$WORKDIR_BASE/clang-for-clapack|' "$BUILD_SCRIPT"
    fi
    if ! grep -q 'export CLAPACK_CLANG=' "$BUILD_SCRIPT" 2>/dev/null; then
      echo "Patching: CLAPACK export CLAPACK_CLANG for launcher..."
      case "$(uname -s)" in Darwin) SED_I="sed -i.bak4clapenv" ;; *) SED_I="sed -i.bak4clapenv" ;; esac
      # Insert export immediately before the cmake line that uses WORKDIR_BASE/clang-for-clapack
      $SED_I '/cmake.*WORKDIR_BASE.*clang-for-clapack/ s/^/  export CLAPACK_CLANG=$ANDROID_TOOLCHAIN_PATH\/bin\/clang\n/' "$BUILD_SCRIPT"
    fi
  fi
fi
# Fallback: shell wrapper (CLAPACK's CMake often ignores response-file vars, so launcher is preferred on WSL)
if [ -f "$SCRIPT_DIR/clang-arm64-cmake.sh.in" ] && [ ! -x "$CLAPACK_LAUNCHER" ]; then
  CLAPACK_WRAPPER="$CLAPACK_BUILD_DIR/clang-for-clapack.sh"
  sed "s|CLANG_PATH_PLACEHOLDER|$CLAPACK_CLANG|g" "$SCRIPT_DIR/clang-arm64-cmake.sh.in" | sed 's/\r$//' > "$CLAPACK_WRAPPER"
  chmod +x "$CLAPACK_WRAPPER"
  if ! grep -q 'clang-for-clapack' "$BUILD_SCRIPT" 2>/dev/null; then
    echo "Patching: CLAPACK use compiler wrapper..."
    case "$(uname -s)" in Darwin) SED_I="sed -i.bak4clap" ;; *) SED_I="sed -i.bak4clap" ;; esac
    $SED_I 's|-DCMAKE_C_COMPILER=\$ANDROID_TOOLCHAIN_PATH/bin/clang|-DCMAKE_C_COMPILER=$WORKDIR_BASE/clang-for-clapack.sh|' "$BUILD_SCRIPT"
  fi
fi

# Remove previous arch build dir so OpenBLAS (and other) clones succeed on re-runs
rm -rf "$REPO_DIR/android/lib/build/kaldi_arm64-v8a"

# Optional: debug why llvm-ar fails ("archive name must be specified"). Creates wrapper that logs argv.
if [ -n "${VOSK_DEBUG_AR:-}" ]; then
  AR_WRAPPER="$REPO_DIR/android/lib/build/ar-debug-wrapper.sh"
  REAL_AR="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/${NDK_PREBUILT}/bin/llvm-ar"
  [ -n "${ANDROID_TOOLCHAIN_PATH:-}" ] && REAL_AR="${ANDROID_TOOLCHAIN_PATH}/bin/llvm-ar"
  AR_DEBUG_LOG="$REPO_DIR/android/lib/build/ar-debug.log"
  mkdir -p "$REPO_DIR/android/lib/build"
  cat > "$AR_WRAPPER" << ARWRAP
#!/bin/sh
# Debug wrapper: log what llvm-ar receives, then exec real llvm-ar.
LOG="$AR_DEBUG_LOG"
REAL="$REAL_AR"
echo "=== ar wrapper invoked ===" >> "\$LOG"
echo "  argc=\$#" >> "\$LOG"
echo "  argv[0]=\$0" >> "\$LOG"
echo "  argv[1]=\$1" >> "\$LOG"
echo "  argv[2]=\$2" >> "\$LOG"
echo "  argv[3]=\$3" >> "\$LOG"
clen=0
for a in "\$@"; do clen=\$((clen + \${#a} + 1)); done
echo "  total_cmdline_len=\$clen" >> "\$LOG"
echo "  (archive name for 'ar qc' is argv[2]; if empty, that causes 'archive name must be specified')" >> "\$LOG"
exec "\$REAL" "\$@"
ARWRAP
  chmod +x "$AR_WRAPPER"
  echo "DEBUG: VOSK_DEBUG_AR=1 → using ar wrapper; log: $AR_DEBUG_LOG"
  # Patch inner script to use wrapper for CMAKE_AR (only for this run; we already patched script above)
  if ! grep -q 'ar-debug-wrapper.sh' "$BUILD_SCRIPT" 2>/dev/null; then
    case "$(uname -s)" in Darwin) SED_I="sed -i.bak_ar" ;; *) SED_I="sed -i.bak_ar" ;; esac
    # Match original (OS_NAME) or patched (NDK_PREBUILT) CMAKE_AR line
    $SED_I 's|-DCMAKE_AR=\$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/\${OS_NAME}-x86_64/bin/\$AR|-DCMAKE_AR=$WORKDIR_BASE/ar-debug-wrapper.sh|' "$BUILD_SCRIPT"
    $SED_I 's|-DCMAKE_AR=\$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/\${NDK_PREBUILT[^}]*}/bin/\$AR|-DCMAKE_AR=$WORKDIR_BASE/ar-debug-wrapper.sh|' "$BUILD_SCRIPT" 2>/dev/null || true
  fi
fi

# Build (from android/lib)
cd "$REPO_DIR/android/lib"
export ANDROID_NDK_HOME
[ -n "${ANDROID_TOOLCHAIN_PATH:-}" ] && export ANDROID_TOOLCHAIN_PATH
[ -n "${OPENBLAS_PREBUILT_DIR:-}" ] && export OPENBLAS_PREBUILT_DIR
# Capture exact ninja command on failure when debugging ar
[ -n "${VOSK_DEBUG_AR:-}" ] && export NINJA_FLAGS="${NINJA_FLAGS:-} -v"
./build-vosk.sh

# Copy arm64-v8a lib to app
BUILT_SO="$REPO_DIR/android/lib/build/kaldi_arm64-v8a/vosk/libvosk.so"
if [ ! -f "$BUILT_SO" ]; then
  BUILT_SO="$REPO_DIR/android/lib/src/main/jniLibs/arm64-v8a/libvosk.so"
fi
if [ ! -f "$BUILT_SO" ]; then
  echo "ERROR: Build finished but libvosk.so not found. Look in $REPO_DIR/android/lib/build/kaldi_arm64-v8a/"
  exit 1
fi

mkdir -p "$APP_JNILIBS/arm64-v8a"
cp -v "$BUILT_SO" "$APP_JNILIBS/arm64-v8a/libvosk.so"
echo ""
echo "Done. 16 KB libvosk.so is at: $APP_JNILIBS/arm64-v8a/libvosk.so"
echo "Build and run the app in Android Studio as usual."
