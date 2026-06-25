#!/bin/bash
# Build AI Chat Desktop App

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DESKTOP_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_DIR="$(dirname "$(dirname "$DESKTOP_DIR")")"

echo "=== Building Desktop App for AI Chat Platform ==="
echo ""

# Step 1: Build the Spring Boot backend JAR
echo "[1/4] Building Spring Boot backend..."
cd "$PROJECT_DIR"
if [ ! -f "target/ai-chat-platform-1.0.0.jar" ]; then
  mvn clean package -DskipTests -q
  echo "  Backend JAR built successfully"
else
  echo "  Backend JAR already exists, skipping build"
fi

# Step 2: Generate icons
echo "[2/4] Generating app icons..."
cd "$DESKTOP_DIR"
if command -v python3 &> /dev/null; then
  python3 scripts/generate-icons.py 2>/dev/null || true
fi

# Step 3: Install npm dependencies
echo "[3/4] Installing Electron dependencies..."
cd "$DESKTOP_DIR"
npm install --silent 2>&1 | tail -1

# Step 4: Build the desktop installer
echo "[4/4] Building desktop installer..."
PLATFORM=""
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) PLATFORM="win" ;;
  Darwin*) PLATFORM="mac" ;;
  Linux*)  PLATFORM="linux" ;;
esac

if [ -n "$PLATFORM" ]; then
  npm run "build:${PLATFORM}"
  echo ""
  echo "=== Build Complete! ==="
  echo "Installer is in: $DESKTOP_DIR/dist/"
else
  echo "Unknown platform, run manually: npm run build:win|mac|linux"
fi
