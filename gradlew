#!/usr/bin/env sh
# Hasseena JARVIS Gradle bootstrap wrapper.
# Uses Gradle 8.7 to match AGP 8.6.1. If a matching Gradle is already installed,
# it is reused; otherwise the official Gradle distribution is downloaded.
set -eu
GRADLE_VERSION=8.7
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}/hasseena-gradle/$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/gradle-$GRADLE_VERSION/bin/gradle"

if command -v gradle >/dev/null 2>&1; then
  if gradle --version 2>/dev/null | grep -q "Gradle $GRADLE_VERSION"; then
    exec gradle "$@"
  fi
fi

if [ ! -x "$GRADLE_BIN" ]; then
  tmp="${TMPDIR:-/tmp}/hasseena-gradle-$GRADLE_VERSION.zip"
  base="${GRADLE_HOME%/*}"
  mkdir -p "$base"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 --connect-timeout 15 -o "$tmp" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$tmp" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  else
    echo "Error: curl or wget is required to download Gradle $GRADLE_VERSION." >&2
    exit 1
  fi
  rm -rf "$GRADLE_HOME"
  mkdir -p "$GRADLE_HOME"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$tmp" -d "$GRADLE_HOME"
  else
    echo "Error: unzip is required to install Gradle $GRADLE_VERSION." >&2
    exit 1
  fi
  rm -f "$tmp"
fi

exec "$GRADLE_BIN" "$@"
