# Hasseena-JARVIS — Max Android Automation

This build is **local-first**: common phone actions are handled on-device without Gemini, reducing unnecessary Gemini quota usage.

## Added
- Universal launcher for installed launcher apps (`open <app>`, `<app> kholo`, `on kro`, etc.)
- WhatsApp launch and confirmation-gated message workflow
- YouTube search via Android web/app intent
- Accessibility bridge for user-approved `click`, `tap`, `type`, `scroll`, Home, Back, Recents, Notifications and Quick Settings
- Notification Listener service with recent notification summary
- Media play/pause/next/previous controls
- Flashlight, volume, mute/unmute
- Wi-Fi, Bluetooth, Display, Battery, Notification and Accessibility settings shortcuts
- Timer and alarm intents
- Google/web search and URL opening
- Dialer and messaging intents with confirmation/system UI
- Roman Urdu + Urdu + English command variants
- Gemini remains fallback for commands not handled locally

## Required user setup
1. Android Settings → Accessibility → Hasseena → Enable, if UI automation is wanted.
2. Android Settings → Notification access → Hasseena → Enable, if notification reading is wanted.
3. Grant microphone permission for voice input/background listening.

## Important
Android security is not bypassed. Accessibility and notification access are explicitly user-enabled system permissions. WhatsApp/other app UI layouts can vary by app version/language, so UI automation is best-effort.

## Termux build
```bash
gradle tasks
gradle assembleDebug
```
APK:
`app/build/outputs/apk/debug/app-debug.apk`


## Verification notes
- Local Android actions are handled before Gemini so simple commands do not consume Gemini quota.
- Accessibility automation requires the user to enable Hasseena in Android Accessibility settings.
- WhatsApp message sending is confirmation-gated; the app does not silently send messages.
- Notification reading requires Android Notification Access.
- Flashlight control requires the Android Camera permission because `CameraManager.setTorchMode` is protected by that permission on supported Android versions.
- No Android security or permission bypass is implemented.
