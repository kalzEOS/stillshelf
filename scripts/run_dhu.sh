#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
DHU_DIR="$SDK_ROOT/extras/google/auto"
DHU_BIN="$DHU_DIR/desktop-head-unit"
CONFIG_FILE="${1:-$DHU_DIR/config/default_720p.ini}"
ADB_ARG="${DHU_ADB_ARG:-127.0.0.1:5277}"

if [[ ! -x "$DHU_BIN" ]]; then
  echo "Desktop Head Unit not found at: $DHU_BIN" >&2
  echo "Expected SDK package: extras;google;auto" >&2
  exit 1
fi

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "DHU config file not found: $CONFIG_FILE" >&2
  exit 1
fi

LIB_PATHS=(
  "$SDK_ROOT/build-tools/36.1.0/lib64"
  "$SDK_ROOT/build-tools/36.0.0/lib64"
  "$SDK_ROOT/build-tools/35.0.0/lib64"
  "$SDK_ROOT/build-tools/34.0.0/lib64"
  "$SDK_ROOT/ndk/25.1.8937393/toolchains/llvm/prebuilt/linux-x86_64/lib64"
  "$SDK_ROOT/ndk/26.1.10909125/toolchains/llvm/prebuilt/linux-x86_64/lib/x86_64-unknown-linux-gnu"
  "$DHU_DIR"
)

LD_PATH=""
for candidate in "${LIB_PATHS[@]}"; do
  if [[ -d "$candidate" ]]; then
    if [[ -n "$LD_PATH" ]]; then
      LD_PATH="$LD_PATH:$candidate"
    else
      LD_PATH="$candidate"
    fi
  fi
done

if [[ -z "$LD_PATH" ]]; then
  echo "Unable to build a runtime library path for DHU." >&2
  exit 1
fi

echo "Starting DHU"
echo "  SDK: $SDK_ROOT"
echo "  Config: $CONFIG_FILE"
echo "  ADB: $ADB_ARG"

LD_LIBRARY_PATH="$LD_PATH" exec "$DHU_BIN" -c "$CONFIG_FILE" -a "$ADB_ARG"
