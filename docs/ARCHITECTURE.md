# Hasseena architecture

## Mark-LV concepts retained
- AI/LLM client abstraction → `GeminiClient.kt`
- Memory → `Memory.kt` + `Prefs.kt`
- Speech input → `Voice.listen()`
- Text-to-speech → `Voice.speak()`
- Assistant identity/system prompt → `GeminiClient.kt`
- User-facing assistant HUD/chat → Android layout
- Settings/runtime configuration → `Prefs.kt`

## Desktop-only Mark-LV concepts intentionally replaced
- PyQt6 UI → Android Views
- sounddevice desktop audio → Android SpeechRecognizer/TTS
- pyautogui/computer-control → Android intents/permissions (future modules)
- Playwright desktop browser automation → Android Custom Tabs/Intents (future module)
- Windows/macOS/Linux shell controls → Android APIs (future modules)
- Python package installer → Android/Gradle dependency management

## Future modules
- Wake word using an Android-compatible on-device engine
- Tool/action registry for Android intents
- Notifications/reminders
- Contacts/calendar integration with explicit permissions
- Camera/vision input
- Streaming Gemini Live audio
- Encrypted memory database
- Backend proxy for API security

### Background voice and actions
`BackgroundAssistantService` provides an Android microphone foreground service and restarts speech recognition after each utterance. `ActionExecutor` handles a bounded set of deterministic Android actions; unknown requests are routed to Gemini. This avoids granting Hasseena unrestricted device control.
