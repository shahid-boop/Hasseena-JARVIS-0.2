package com.hasseena.jarvis

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import java.util.Locale

class BackgroundAssistantService : Service() {
    companion object {
        const val ACTION_START = "com.hasseena.jarvis.START_BACKGROUND"
        const val ACTION_STOP = "com.hasseena.jarvis.STOP_BACKGROUND"
        const val ACTION_RESULT = "com.hasseena.jarvis.ACTION_RESULT"
        const val EXTRA_TEXT = "text"
        const val EXTRA_REPLY = "reply"
        const val EXTRA_STATUS = "status"
        private const val CHANNEL_ID = "hasseena_assistant"
        private const val NOTIFICATION_ID = 1407
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private lateinit var prefs: Prefs
    private lateinit var memory: Memory
    private lateinit var voice: Voice
    private lateinit var client: GeminiClient
    private lateinit var actions: ActionExecutor
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        memory = Memory(prefs)
        voice = Voice(this)
        client = GeminiClient(prefs)
        actions = ActionExecutor(this, memory)
        createChannel()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification("Listening for your commands"), foregroundServiceType())
        } else {
            startForeground(NOTIFICATION_ID, notification("Listening for your commands"))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopAssistant(); return START_NOT_STICKY }
        stopping = false
        startListeningSoon(250)
        return START_STICKY
    }

    private fun foregroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= 29) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
    }

    private fun startListeningSoon(delay: Long) {
        if (stopping || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ startListening() }, delay)
    }

    private fun startListening() {
        if (stopping || !SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                recognizer?.destroy(); recognizer = null
                if (text.isNotBlank()) handleCommand(text) else startListeningSoon(300)
            }
            override fun onError(error: Int) { recognizer?.destroy(); recognizer = null; if (!stopping) startListeningSoon(700) }
            override fun onEndOfSpeech() {}
            override fun onReadyForSpeech(params: Bundle?) { updateNotification("Listening…") }
            override fun onBeginningOfSpeech() { updateNotification("Hasseena is listening…") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("ur", "PK").toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale("ur", "PK").toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try { recognizer?.startListening(intent) } catch (_: Exception) { startListeningSoon(1500) }
    }

    private fun handleCommand(text: String) {
        sendResult(text, "", "command")
        val action = actions.execute(text)
        if (action.handled) {
            val reply = action.message
            if (reply.isNotBlank()) voice.speak(reply)
            sendResult(text, reply, "action")
            if (isStopCommand(text)) stopAssistant() else startListeningSoon(500)
            return
        }
        updateNotification("Thinking…")
        client.ask(text) { result ->
            handler.post {
                val reply = result.getOrElse { "I couldn't complete that: ${it.message ?: "Gemini request failed"}" }
                voice.speak(reply)
                sendResult(text, reply, "gemini")
                updateNotification("Listening for your commands")
                startListeningSoon(500)
            }
        }
    }

    private fun isStopCommand(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT).trim()
        return t.contains("stop listening") || t.contains("sunna band karo") || t.contains("sunna band kar do") || t.contains("hasseena chup ho jao")
    }

    private fun sendResult(text: String, reply: String, status: String) {
        sendBroadcast(Intent(ACTION_RESULT).apply {
            setPackage(packageName)
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_REPLY, reply)
            putExtra(EXTRA_STATUS, status)
        })
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Hasseena Assistant", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Hasseena background voice assistant"
            })
        }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.hasseena_icon)
        .setContentTitle("Hasseena")
        .setContentText(text)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun updateNotification(text: String) { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text)) }

    private fun stopAssistant() {
        stopping = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy(); recognizer = null
        voice.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopping = true; handler.removeCallbacksAndMessages(null); recognizer?.destroy(); voice.shutdown(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
