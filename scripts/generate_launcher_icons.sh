#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <source-png> [background-hex]"
  echo "Example: $0 '/path/icon.png' '#091521'"
  exit 1
fi

SRC="$1"
BG_HEX="${2:-#091521}"

if [[ ! -f "$SRC" ]]; then
  echo "Source file not found: $SRC" >&2
  exit 1
fi

if ! command -v magick >/dev/null 2>&1; then
  echo "ImageMagick (magick) is required." >&2
  exit 1
fi

RES_DIR="app/src/main/res"
mkdir -p \
  "$RES_DIR/mipmap-mdpi" \
  "$RES_DIR/mipmap-hdpi" \
  "$RES_DIR/mipmap-xhdpi" \
  "$RES_DIR/mipmap-xxhdpi" \
  "$RES_DIR/mipmap-xxxhdpi" \
  "$RES_DIR/mipmap-anydpi-v26" \
  "$RES_DIR/drawable" \
  "$RES_DIR/values"

# Keep content safely away from adaptive icon mask cut edges.
# 432px canvas (Android adaptive foreground recommendation), with centered 344px artwork.
TMP_FOREGROUND="$(mktemp --suffix=.png)"
TMP_LEGACY="$(mktemp --suffix=.png)"
trap 'rm -f "$TMP_FOREGROUND" "$TMP_LEGACY"' EXIT

magick "$SRC" -resize 344x344 -background none -gravity center -extent 432x432 "$TMP_FOREGROUND"
# Slightly larger for legacy launcher icons so they do not look too small.
magick "$SRC" -resize 900x900 -background none -gravity center -extent 1024x1024 "$TMP_LEGACY"

magick "$TMP_LEGACY" -resize 48x48 "$RES_DIR/mipmap-mdpi/ic_launcher.png"
magick "$TMP_LEGACY" -resize 48x48 "$RES_DIR/mipmap-mdpi/ic_launcher_round.png"
magick "$TMP_LEGACY" -resize 72x72 "$RES_DIR/mipmap-hdpi/ic_launcher.png"
magick "$TMP_LEGACY" -resize 72x72 "$RES_DIR/mipmap-hdpi/ic_launcher_round.png"
magick "$TMP_LEGACY" -resize 96x96 "$RES_DIR/mipmap-xhdpi/ic_launcher.png"
magick "$TMP_LEGACY" -resize 96x96 "$RES_DIR/mipmap-xhdpi/ic_launcher_round.png"
magick "$TMP_LEGACY" -resize 144x144 "$RES_DIR/mipmap-xxhdpi/ic_launcher.png"
magick "$TMP_LEGACY" -resize 144x144 "$RES_DIR/mipmap-xxhdpi/ic_launcher_round.png"
magick "$TMP_LEGACY" -resize 192x192 "$RES_DIR/mipmap-xxxhdpi/ic_launcher.png"
magick "$TMP_LEGACY" -resize 192x192 "$RES_DIR/mipmap-xxxhdpi/ic_launcher_round.png"

cp "$TMP_FOREGROUND" "$RES_DIR/drawable/ic_launcher_foreground.png"

cat > "$RES_DIR/mipmap-anydpi-v26/ic_launcher.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
XML

cat > "$RES_DIR/mipmap-anydpi-v26/ic_launcher_round.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
XML

cat > "$RES_DIR/values/colors.xml" <<XML
<resources>
    <color name="ic_launcher_background">$BG_HEX</color>
</resources>
XML

echo "Launcher icons generated from: $SRC"
echo "Background color: $BG_HEX"
