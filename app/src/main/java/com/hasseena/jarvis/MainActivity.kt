package com.hasseena.jarvis

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs; private lateinit var voice: Voice; private lateinit var client: GeminiClient; private lateinit var memory: Memory
    private lateinit var chat: LinearLayout; private lateinit var scroll: ScrollView; private lateinit var input: EditText; private lateinit var status: TextView
    private var receiverRegistered = false

    private val assistantReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val text = intent.getStringExtra(BackgroundAssistantService.EXTRA_TEXT).orEmpty()
            val reply = intent.getStringExtra(BackgroundAssistantService.EXTRA_REPLY).orEmpty()
            val state = intent.getStringExtra(BackgroundAssistantService.EXTRA_STATUS).orEmpty()
            if (state == "command" && text.isNotBlank()) addMessage("You", text, true)
            if (reply.isNotBlank()) addMessage("Hasseena", reply, false)
            status.text = when (state) { "action" -> "Action completed"; "gemini" -> "Ready"; else -> "Listening in background…" }
        }
    }

    override fun onCreate(state: Bundle?) { super.onCreate(state); setContentView(R.layout.activity_main)
        prefs = Prefs(this); voice = Voice(this); client = GeminiClient(prefs); memory = Memory(prefs)
        chat = findViewById(R.id.chat); scroll = findViewById(R.id.scroll); input = findViewById(R.id.input); status = findViewById(R.id.status)
        findViewById<Button>(R.id.send).setOnClickListener { send() }
        findViewById<Button>(R.id.mic).setOnClickListener { requestMicAndListen() }
        findViewById<Button>(R.id.settings).setOnClickListener { showSettings() }
        addMessage("Hasseena", "Hello. I'm Hasseena. I can answer you and perform supported Android actions.", false)
        if (prefs.backgroundEnabled && hasMicPermission()) startBackgroundAssistant()
    }

    override fun onStart() { super.onStart(); val filter = IntentFilter(BackgroundAssistantService.ACTION_RESULT); ContextCompat.registerReceiver(this, assistantReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED); receiverRegistered = true }
    override fun onStop() { if (receiverRegistered) { unregisterReceiver(assistantReceiver); receiverRegistered = false }; super.onStop() }

    private fun send() { val text = input.text.toString().trim(); if (text.isBlank()) return; input.text.clear(); addMessage("You", text, true); status.text = "Hasseena is thinking…"
        val action = ActionExecutor(this, memory).execute(text)
        if (action.handled) { addMessage("Hasseena", action.message, false); status.text = "Action completed"; if (prefs.voiceEnabled) voice.speak(action.message); return }
        client.ask(text) { result -> runOnUiThread { result.onSuccess { reply -> addMessage("Hasseena", reply, false); status.text = "Ready"; if (prefs.voiceEnabled) voice.speak(reply) }.onFailure { e -> addMessage("Hasseena", "I couldn't complete that: ${e.message}", false); status.text = "Needs attention" } } }
    }

    private fun addMessage(who: String, text: String, user: Boolean) { val box = TextView(this).apply { this.text = "$who\n$text"; setTextColor(resources.getColor(R.color.text)); textSize = 16f; setPadding(18,14,18,14); setTypeface(null, Typeface.NORMAL); gravity = Gravity.START; setBackgroundResource(if (user) R.drawable.bubble_user else R.drawable.bubble_ai) }; val lp = LinearLayout.LayoutParams(-1, -2); lp.setMargins(0,8,0,8); chat.addView(box, lp); scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) } }

    private fun requestMicAndListen() { if (!hasMicPermission()) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 20); return }; status.text = "Listening…"; voice.listen({ text -> input.setText(text); status.text = "Voice captured"; send() }, { status.text = it }) }
    private fun hasMicPermission() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startBackgroundAssistant() {
        if (!hasMicPermission()) return
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 21)
        val intent = Intent(this, BackgroundAssistantService::class.java).setAction(BackgroundAssistantService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
        status.text = "Listening in background…"
    }
    private fun stopBackgroundAssistant() { stopService(Intent(this, BackgroundAssistantService::class.java)); status.text = "Background listening stopped" }

    private fun showSettings() { val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,8,32,0) }
        val key = EditText(this).apply { hint = "Gemini API key"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setText(prefs.apiKey) }
        val model = EditText(this).apply { hint = "Model"; setText(prefs.model) }
        val voiceSwitch = CheckBox(this).apply { text = "Speak replies with device TTS voice"; isChecked = prefs.voiceEnabled }
        val bgSwitch = CheckBox(this).apply { text = "Background assistant (persistent notification)"; isChecked = prefs.backgroundEnabled }
        val automationButton = Button(this).apply {
            text = "Enable Android Automation"
            setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val notificationButton = Button(this).apply {
            text = "Enable Notification Access"
            setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        val note = TextView(this).apply { text = "Android automation is user-controlled. Accessibility enables supported click/type/scroll/navigation actions. Notification access lets Hasseena read recent notification text. Neither bypasses Android security or permissions."; setPadding(0,8,0,8) }
        panel.addView(key); panel.addView(model); panel.addView(voiceSwitch); panel.addView(bgSwitch); panel.addView(automationButton); panel.addView(notificationButton); panel.addView(note)
        AlertDialog.Builder(this).setTitle("Hasseena Settings").setView(panel).setPositiveButton("Save") { _, _ ->
            prefs.apiKey = key.text.toString(); prefs.model = model.text.toString().ifBlank { AppConfig.DEFAULT_MODEL }; prefs.voiceEnabled = voiceSwitch.isChecked; prefs.backgroundEnabled = bgSwitch.isChecked
            if (prefs.backgroundEnabled && hasMicPermission()) startBackgroundAssistant() else if (!prefs.backgroundEnabled) stopBackgroundAssistant()
            status.text = "Settings saved"
        }.setNegativeButton("Cancel", null).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == 20 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) requestMicAndListen(); if (requestCode == 21 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && prefs.backgroundEnabled) startBackgroundAssistant() }
    override fun onDestroy() { voice.shutdown(); super.onDestroy() }
}
