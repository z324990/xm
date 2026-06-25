#!/bin/bash
# Generate app icons from SVG source
# Requirements: Inkscape or ImageMagick, and ico/icns tools

ASSETS_DIR="$(cd "$(dirname "$0")/../assets" && pwd)"
SVG_FILE="$ASSETS_DIR/icon.svg"

if [ ! -f "$SVG_FILE" ]; then
  echo "Error: $SVG_FILE not found"
  exit 1
fi

echo "Generating icons from $SVG_FILE..."

# Generate 1024x1024 PNG (base for all platforms)
if command -v inkscape &> /dev/null; then
  echo "Using Inkscape..."
  inkscape "$SVG_FILE" -o "$ASSETS_DIR/icon-1024.png" -w 1024 -h 1024
elif command -v convert &> /dev/null; then
  echo "Using ImageMagick..."
  convert -background none -size 1024x1024 "$SVG_FILE" "$ASSETS_DIR/icon-1024.png"
else
  echo "Warning: Neither Inkscape nor ImageMagick found."
  echo "Please manually create icon-1024.png (1024x1024) from the SVG."
  exit 1
fi

# Generate Windows .ico (from 1024px PNG)
if command -v convert &> /dev/null; then
  echo "Generating icon.ico..."
  convert "$ASSETS_DIR/icon-1024.png" -define icon:auto-resize=256,48,32,16 "$ASSETS_DIR/icon.ico"
else
  echo "Skipping .ico generation (ImageMagick needed)"
fi

# Generate macOS .icns
if command -v iconutil &> /dev/null; then
  echo "Generating iconset for macOS..."
  mkdir -p "$ASSETS_DIR/icon.iconset"
  for size in 16 32 64 128 256 512 1024; do
    s2=$((size*2))
    convert "$ASSETS_DIR/icon-1024.png" -resize ${size}x${size} "$ASSETS_DIR/icon.iconset/icon_${size}x${size}.png"
    if [ "$size" -le 512 ]; then
      convert "$ASSETS_DIR/icon-1024.png" -resize ${s2}x${s2} "$ASSETS_DIR/icon.iconset/icon_${size}x${size}@2x.png"
    fi
  done
  iconutil -c icns "$ASSETS_DIR/icon.iconset" -o "$ASSETS_DIR/icon.icns"
  rm -rf "$ASSETS_DIR/icon.iconset"
fi

# Generate smaller PNGs for Linux
convert "$ASSETS_DIR/icon-1024.png" -resize 256x256 "$ASSETS_DIR/icon.png"
convert "$ASSETS_DIR/icon-1024.png" -resize 128x128 "$ASSETS_DIR/128x128.png"
convert "$ASSETS_DIR/icon-1024.png" -resize 64x64 "$ASSETS_DIR/64x64.png"

echo "Icons generated successfully!"
ls -la "$ASSETS_DIR"
