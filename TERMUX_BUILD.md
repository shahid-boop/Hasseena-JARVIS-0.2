# Hasseena JARVIS — Termux Build Guide

This project is pinned to **JDK 21 + Android Gradle Plugin 8.6.1 + Gradle 8.7**.

## 1) Install required Termux packages

```bash
pkg update
pkg install openjdk-21 gradle curl unzip
```

Set Java 21:

```bash
export JAVA_HOME=$PREFIX/lib/jvm/java-21-openjdk
export PATH=$JAVA_HOME/bin:$PATH
java -version
gradle --version
```

## 2) Extract and enter the project

```bash
cd ~/Hasseena-JARVIS
```

The ZIP now includes `gradlew`. It pins the project to Gradle 8.7. If Gradle 8.7 is not already installed, the bootstrap script can download it using `curl`.

```bash
./gradlew --version
```

## 3) Build the APK

```bash
./gradlew clean
./gradlew assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Copy to Android Downloads:

```bash
mkdir -p ~/storage/downloads
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/Hasseena-JARVIS.apk
```

## 4) Gemini setup

1. Install the APK.
2. Open Hasseena.
3. Open **Settings**.
4. Enter your Gemini API key.
5. Keep the model as `gemini-3.8-flash` unless you intentionally want another supported model.
6. Save and send a test message.

The app uses `gemini-3.8-flash` by default and `gemini-3.7-flash` as the model-access fallback. Both are current Gemini API models. The API key is **not** bundled in the source ZIP.

## 5) Gemini temporary-error handling

The Android client now retries transient `408`, `429`, and `5xx` responses (including `503`) with exponential backoff and jitter. After the retry limit is reached, the app shows the real HTTP error instead of silently changing configuration.

A `404/model-not-found` or model-access error is handled separately by trying the configured fallback model once.

## 6) Important security note

Never commit a real Gemini API key to GitHub. This starter app stores the key locally in Android app preferences. For a production app, move Gemini calls behind an authenticated backend so the API key is not exposed in the APK.
