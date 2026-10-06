# Verification record

## Static verification completed
- Project structure contains the Android application module and Gradle settings.
- Android namespace and applicationId are `com.hasseena.jarvis`.
- Kotlin source packages match the application namespace.
- Every Android layout/drawable/color/style referenced by the Kotlin/XML sources exists.
- Manifest activities, launcher entry point, themes, icon and permissions are internally consistent.
- `SplashActivity` is exported as the launcher; `MainActivity` is not exported.
- Microphone permission is requested at runtime before `SpeechRecognizer` is started.
- Missing speech-recognition support is handled without crashing.
- TTS is shut down in `MainActivity.onDestroy()`.
- Gemini API calls run off the main thread.
- Gemini HTTP errors are surfaced with the server message when available.
- Gemini JSON parsing handles multiple response parts instead of assuming only part 0.
- API keys and model names are URL-encoded.
- Legacy/preview saved model names are migrated to the current default.
- Current default model is `gemini-3.8-flash`; a single model-access fallback is provided.
- The Android runtime does not import or execute the desktop Python files under `marklv-reference/`.
- Branding assets and splash resources are present.

## Build compatibility
- Android Gradle Plugin: 8.6.1
- Kotlin Android plugin: 2.0.21
- Kotlin JVM toolchain: 21
- `compileSdk`: 35
- `targetSdk`: 35
- `minSdk`: 26

A final APK build still requires an Android SDK and a compatible Gradle installation. This packaging environment does not contain an Android SDK, so an APK compilation is **not** claimed here.

## Gemini model check
Google's current Gemini API documentation lists `gemini-3.8-flash` as a generally available model and documents the `models.list` endpoint for checking which models are enabled for a particular API key/project. The app therefore uses `gemini-3.8-flash` as its default and reports API errors rather than hiding them.
