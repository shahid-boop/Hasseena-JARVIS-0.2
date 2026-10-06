package com.hasseena.jarvis

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice as TtsVoice
import java.util.Locale

/**
 * Hasseena voice layer. Prefers a natural Urdu (Pakistan) female voice when
 * the installed Android TTS engine provides one, with graceful fallbacks.
 */
class Voice(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    private val urduPakistan = Locale("ur", "PK")
    private val urduIndia = Locale("ur", "IN")

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                configureNaturalUrduVoice()
            }
        }
    }

    private fun configureNaturalUrduVoice() {
        val engine = tts ?: return

        // Urdu is the primary conversational language. If the device has a
        // Google/other high-quality Urdu voice installed, prefer a female voice.
        val urduVoices = engine.voices.orEmpty().filter {
            it.locale.language == "ur"
        }
        val preferred = urduVoices.firstOrNull { isLikelyFemale(it) }
            ?: urduVoices.firstOrNull()

        if (preferred != null) {
            engine.voice = preferred
            engine.language = preferred.locale
        } else {
            val result = engine.setLanguage(urduPakistan)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(urduIndia)
            }
        }

        // Slightly slower and warmer than the Android default; this sounds
        // more conversational in Urdu without becoming artificially slow.
        engine.setSpeechRate(0.90f)
        engine.setPitch(1.06f)
    }

    private fun isLikelyFemale(voice: TtsVoice): Boolean {
        val n = voice.name.lowercase(Locale.ROOT)
        return n.contains("female") || n.contains("woman") || n.contains("zira") || n.contains("heera") || n.contains("aisha")
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        val cleaned = naturalizeForSpeech(text)
        tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "hasseena-response")
    }

    private fun naturalizeForSpeech(text: String): String {
        // Keep the written reply intact in chat; only speech punctuation is
        // lightly normalized so TTS pauses more naturally.
        return text
            .replace("...", "۔")
            .replace("  ", " ")
            .trim()
    }

    fun listen(onText: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this phone.")
            return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                onText(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                recognizer.destroy()
            }
            override fun onError(error: Int) { onError("Speech recognition error: $error"); recognizer.destroy() }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, urduPakistan.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, urduPakistan.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
        })
    }

    fun shutdown() {
        ready = false
        tts?.stop()
        tts?.shutdown()
    }
}
