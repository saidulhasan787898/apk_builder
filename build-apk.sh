#!/usr/bin/env bash
set -e
echo "=== Building Installable Android APK for Media_Server ==="
if ! command -v gradle >/dev/null 2>&1; then
  echo "Installing Gradle / OpenJDK (requires Ubuntu/Debian)..."
  sudo apt-get update && sudo apt-get install -y openjdk-17-jdk gradle
fi
gradle assembleDebug
echo "APK Ready at: app/build/outputs/apk/debug/app-debug.apk"
