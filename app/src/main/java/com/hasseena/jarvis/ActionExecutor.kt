package com.hasseena.jarvis

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import java.util.Locale
import kotlin.math.max

/** Local-first Android command executor. Unknown requests are left for Gemini. */
class ActionExecutor(private val context: Context, private val memory: Memory) {
    data class Result(val handled: Boolean, val message: String)

    companion object {
        @Volatile private var pendingWhatsAppMessage: String? = null
    }

    fun execute(raw: String): Result {
        val text = raw.trim()
        val lower = normalize(text)
        if (text.isBlank()) return Result(false, "")

        // Confirmation gate for sending a message.
        if (lower in setOf("confirm", "confirm send", "send it", "send message", "haan bhejo", "han bhejo", "yes send", "bhej do")) {
            val pending = pendingWhatsAppMessage
            if (pending != null) {
                if (!HasseenaAccessibilityService.isEnabled()) return Result(true, "WhatsApp message بھیجنے کے لیے Accessibility Service enable کریں۔")
                if (!HasseenaAccessibilityService.isWhatsAppActive()) return Result(true, "Send کرنے سے پہلے WhatsApp chat کھولیں۔")
                pendingWhatsAppMessage = null
                return if (HasseenaAccessibilityService.typeIntoFocused(pending) && HasseenaAccessibilityService.clickText("send", false))
                    Result(true, "جی، پیغام بھیج دیا ہے۔")
                else Result(true, "پیغام تیار ہے لیکن Send بٹن نہیں ملا۔ WhatsApp کھول کر Send دبا دیں۔")
            }
        }

        if (isStopCommand(lower)) return Result(true, "ٹھیک ہے، میں سننا بند کر رہی ہوں۔")

        if (lower.startsWith("remember ") || lower.startsWith("remember that ")) {
            val note = text.substringAfter("remember", missingDelimiterValue = "").trim().removePrefix("that ").trim()
            if (note.isNotBlank()) { memory.add(note); return Result(true, "ٹھیک ہے، میں یہ یاد رکھوں گی۔") }
        }

        if (hasAny(lower, "flashlight on", "torch on", "flash on", "ٹارچ آن", "torch chalao")) return setFlashlight(true)
        if (hasAny(lower, "flashlight off", "torch off", "ٹارچ آف", "torch band")) return setFlashlight(false)
        if (hasAny(lower, "volume up", "increase volume", "louder", "volume barhao", "awaz tez", "آواز تیز کرو")) return changeVolume(AudioManager.ADJUST_RAISE)
        if (hasAny(lower, "volume down", "decrease volume", "quieter", "volume kam", "awaz kam", "آواز کم کرو")) return changeVolume(AudioManager.ADJUST_LOWER)
        if (hasAny(lower, "mute", "mute volume", "awaz band")) return changeVolume(AudioManager.ADJUST_MUTE)
        if (hasAny(lower, "unmute", "unmute volume", "awaz on")) return changeVolume(AudioManager.ADJUST_UNMUTE)

        // Media controls are local and never consume Gemini quota.
        if (hasAny(lower, "play music", "music play", "media play", "gaana chalao", "music chalao")) return mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY, "میڈیا چلا دیا ہے۔")
        if (hasAny(lower, "pause music", "music pause", "media pause", "gaana roko", "music roko")) return mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, "میڈیا روک دیا ہے۔")
        if (hasAny(lower, "next song", "next track", "agla gana", "اگلا گانا")) return mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT, "اگلا ٹریک کر دیا ہے۔")
        if (hasAny(lower, "previous song", "previous track", "pichla gana", "پچھلا گانا")) return mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS, "پچھلا ٹریک کر دیا ہے۔")

        if (hasAny(lower, "open settings", "settings kholo", "settings khol do", "سیٹنگز کھولو")) return openIntent(Intent(Settings.ACTION_SETTINGS), "Settings کھول رہی ہوں۔")
        if (hasAny(lower, "wifi settings", "open wifi", "wifi kholo", "وائی فائی")) return openIntent(Intent(Settings.ACTION_WIFI_SETTINGS), "Wi‑Fi settings کھول رہی ہوں۔")
        if (hasAny(lower, "bluetooth settings", "open bluetooth", "bluetooth kholo", "بلوٹوتھ")) return openIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Bluetooth settings کھول رہی ہوں۔")
        if (hasAny(lower, "display settings", "screen settings", "display kholo")) return openIntent(Intent(Settings.ACTION_DISPLAY_SETTINGS), "Display settings کھول رہی ہوں۔")
        if (hasAny(lower, "battery settings", "battery kholo")) return openIntent(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), "Battery settings کھول رہی ہوں۔")
        if (hasAny(lower, "notification settings", "notifications settings", "notification access")) return openIntent(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), "Notification access settings کھول رہی ہوں۔")
        if (hasAny(lower, "show notifications", "notifications dikhao", "notification dikhao", "نوٹیفکیشن دکھاؤ")) return Result(true, HasseenaNotificationListenerService.latest())
        if (hasAny(lower, "accessibility settings", "accessibility on", "automation settings", "accessibility kholo", "ایکسسبیلیٹی")) return openIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "Accessibility settings کھول رہی ہوں۔")

        val timerMatch = Regex("(?:set|start|lagao|laga do) (?:a )?(?:timer|ٹائمر) (?:for|ka|کے لیے)?\\s*(\\d+)\\s*(seconds?|minutes?|hours?|sec|min|mins|hour|minute|منٹ|گھنٹہ|گھنٹے)?", RegexOption.IGNORE_CASE).find(text)
        if (timerMatch != null) {
            val n = timerMatch.groupValues[1].toIntOrNull() ?: return Result(true, "ٹائمر کی مدت سمجھ نہیں آئی۔")
            val unit = timerMatch.groupValues[2].lowercase(Locale.getDefault())
            val seconds = when { unit.startsWith("hour") || unit.contains("گھنٹ") -> n * 3600; unit.startsWith("min") || unit.contains("منٹ") -> n * 60; else -> n }
            return openIntent(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, max(1, seconds)), "${n} ${if (unit.isBlank()) "seconds" else unit} کا ٹائمر کھول رہی ہوں۔")
        }

        val search = Regex("(?:search (?:the )?web for|google|search for|google پر تلاش کرو|search karo) (.+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)
        if (!search.isNullOrBlank()) return openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(search.trim()))), "${search.trim()} تلاش کر رہی ہوں۔")

        val urlMatch = Regex("(?:open|go to|kholo|open karo) (https?://\\S+|www\\.\\S+)", RegexOption.IGNORE_CASE).find(text)
        if (urlMatch != null) {
            var url = urlMatch.groupValues[1]; if (url.startsWith("www.", true)) url = "https://$url"
            return openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Website کھول رہی ہوں۔")
        }

        // YouTube search: open the search URL, preferably in the installed YouTube app.
        val ytSearch = Regex("(?:youtube|یوٹیوب) (?:search|پر تلاش کرو|par search karo) (.+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)
        if (!ytSearch.isNullOrBlank()) return openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(ytSearch.trim()))), "YouTube پر ${ytSearch.trim()} تلاش کر رہی ہوں۔")

        // WhatsApp message: navigate to the requested contact when Accessibility is enabled;
        // sending remains confirmation-gated.
        val waStructured = Regex("(?:whatsapp|واٹس ایپ) (?:پر |par )?(.+?) (?:کو |ko )?(?:message|msg|پیغام|message bhejo|پیغام بھیجو)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)
        val waNatural = Regex("(.+?) (?:کو |ko )?(?:whatsapp|واٹس ایپ) (?:پر |par )?(?:message|msg|پیغام)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)
        val waMatch = waStructured ?: waNatural
        if (waMatch != null) {
            val contact = waMatch.groupValues[1].trim().removeSuffix("ko").trim()
            val message = waMatch.groupValues[2].trim()
            if (contact.isNotBlank() && message.isNotBlank()) {
                if (!HasseenaAccessibilityService.isEnabled()) {
                    return Result(true, "WhatsApp automation کے لیے پہلے Hasseena کی Accessibility Service enable کریں۔")
                }
                pendingWhatsAppMessage = message
                val opened = openAppAlias("com.whatsapp") != null
                if (opened) HasseenaAccessibilityService.openWhatsAppChat(contact)
                return Result(true, if (opened) "WhatsApp کھول کر $contact کی chat تلاش کر رہی ہوں۔ پیغام تیار ہے؛ chat کھلنے کے بعد 'ہاں بھیجو' کہیں۔" else "WhatsApp installed نہیں ہے۔")
            }
        }

        val callMatch = Regex("(?:call|phone|call karo|فون کرو) (.+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)
        if (!callMatch.isNullOrBlank()) return openIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(callMatch.trim()))), "Dialer کھول دیا ہے۔ کال کی تصدیق وہاں کریں۔")

        val smsMatch = Regex("(?:text|sms|message) (.+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)
        if (!smsMatch.isNullOrBlank()) return openIntent(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(smsMatch.trim()))), "Messaging app کھول دی ہے۔ پیغام بھیجنے سے پہلے تصدیق کریں۔")

        // Global navigation requires user-enabled Accessibility.
        if (hasAny(lower, "go home", "home screen", "ghar screen", "home jao", "گھر جاؤ")) return global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME, "Home screen کھول دی ہے۔")
        if (hasAny(lower, "go back", "back", "wapas jao", "واپس جاؤ")) return global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK, "واپس چلی گئی۔")
        if (hasAny(lower, "recent apps", "recents", "recent kholo")) return global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS, "Recent apps کھول دی ہیں۔")
        if (hasAny(lower, "notifications kholo", "notification shade", "notifications dikhao", "نوٹیفکیشن دکھاؤ")) return global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Notifications کھول رہی ہوں۔")
        if (hasAny(lower, "quick settings", "quick settings kholo")) return global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "Quick Settings کھول رہی ہوں۔")

        // Accessibility UI primitives.
        val click = Regex("(?:click|tap|press|dabao|دباؤ|کلک کرو) (.+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)
        if (!click.isNullOrBlank()) return Result(true, if (HasseenaAccessibilityService.clickText(click.trim())) "جی، $click پر click کر دیا۔" else "مجھے $click کا button/text نظر نہیں آیا۔ Accessibility access چیک کریں۔")
        val type = Regex("(?:type|likho|لکھو|type karo) (.+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)
        if (!type.isNullOrBlank()) return Result(true, if (HasseenaAccessibilityService.typeIntoFocused(type.trim())) "جی، لکھ دیا ہے۔" else "کوئی active text field نہیں ملی۔")
        if (hasAny(lower, "scroll down", "neeche scroll", "نیچے اسکرول")) return Result(true, if (HasseenaAccessibilityService.scroll(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) "نیچے scroll کر دیا۔" else "Scrollable area نہیں ملی۔")
        if (hasAny(lower, "scroll up", "upar scroll", "اوپر اسکرول")) return Result(true, if (HasseenaAccessibilityService.scroll(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) "اوپر scroll کر دیا۔" else "Scrollable area نہیں ملی۔")

        // Universal launcher: "open <app>" / "<app> kholo". It resolves installed launcher apps.
        val appRequest = extractAppRequest(text, lower)
        if (appRequest != null) {
            findAndOpenApp(appRequest)?.let { return Result(true, "جی، $it کھول رہی ہوں۔") }
            return Result(true, "مجھے '$appRequest' نام کی installed app نہیں ملی۔")
        }

        // Explicit aliases.
        val aliases = listOf(
            Triple("whatsapp", "com.whatsapp", "WhatsApp"), Triple("واٹس ایپ", "com.whatsapp", "WhatsApp"),
            Triple("youtube", "com.google.android.youtube", "YouTube"), Triple("یوٹیوب", "com.google.android.youtube", "YouTube"),
            Triple("chrome", "com.android.chrome", "Chrome"), Triple("کروم", "com.android.chrome", "Chrome"),
            Triple("maps", "com.google.android.apps.maps", "Google Maps"), Triple("google maps", "com.google.android.apps.maps", "Google Maps")
        )
        aliases.firstOrNull { lower.contains(it.first) }?.let { a ->
            openAppAlias(a.second)?.let { return Result(true, "جی، ${a.third} کھول رہی ہوں۔") }
        }

        return Result(false, "")
    }

    private fun normalize(s: String) = s.lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ").trim()
    private fun hasAny(s: String, vararg words: String) = words.any { s.contains(it.lowercase(Locale.getDefault())) }
    private fun isStopCommand(s: String) = hasAny(s, "stop listening", "stop background listening", "sunna band karo", "sunna band kar do", "hasseena chup ho jao", "سننا بند کرو")

    private fun extractAppRequest(text: String, lower: String): String? {
        val patterns = listOf(Regex("^(?:open|launch|start)\\s+(.+)$", RegexOption.IGNORE_CASE), Regex("^(.+?)\\s+(?:open karo|open kro|khol do|kholo|on kro|on karo|چلاو|چلاؤ|کھولو|آن کرو)$", RegexOption.IGNORE_CASE))
        return patterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { x -> x.isNotBlank() } }
    }

    private fun changeVolume(direction: Int): Result { (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustVolume(direction, AudioManager.FLAG_SHOW_UI); return Result(true, "جی، کر دیا ہے۔") }

    private fun mediaKey(code: Int, message: String): Result = try {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
        Result(true, message)
    } catch (_: Exception) { Result(true, "میڈیا control Android نے allow نہیں کیا۔") }

    private fun setFlashlight(on: Boolean): Result {
        return try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Result(true, "Flashlight کے لیے Camera permission درکار ہے۔ پہلے Hasseena کو Camera permission دیں۔")
            } else {
                val camera = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val id = camera.cameraIdList.firstOrNull {
                    camera.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (id == null) {
                    Result(true, "اس فون میں flashlight Android کے لیے available نہیں ہے۔")
                } else {
                    camera.setTorchMode(id, on)
                    Result(true, if (on) "Flashlight آن کر دی ہے۔" else "Flashlight آف کر دی ہے۔")
                }
            }
        } catch (_: Exception) {
            Result(true, "Flashlight control نہیں ہو سکا۔")
        }
    }

    private fun global(action: Int, ok: String): Result = Result(true, if (HasseenaAccessibilityService.performGlobalActionSafe(action)) ok else "یہ action کرنے کے لیے Hasseena کی Accessibility Service enable کریں۔")

    private fun openIntent(intent: Intent, message: String): Result = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) { context.startActivity(intent); Result(true, message) }
        else Result(true, "یہ Android screen اس فون پر available نہیں ہے۔")
    } catch (_: Exception) { Result(true, "Android نے یہ action allow نہیں کیا۔") }

    private fun openAppAlias(alias: String): String? = try {
        val intent = context.packageManager.getLaunchIntentForPackage(alias) ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent)
        when(alias) { "com.whatsapp" -> "WhatsApp"; "com.google.android.youtube" -> "YouTube"; "com.android.chrome" -> "Chrome"; "com.google.android.apps.maps" -> "Google Maps"; else -> "app" }
    } catch (_: Exception) { null }

    private fun findAndOpenApp(name: String): String? {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
        val wanted = name.lowercase(Locale.getDefault()).trim()
        val aliases = mapOf("واٹس ایپ" to "whatsapp", "یوٹیوب" to "youtube", "کروم" to "chrome", "کیمرہ" to "camera", "گیلری" to "gallery")
        val query = aliases[wanted] ?: wanted
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase(Locale.getDefault()).contains(query) }
            ?: apps.firstOrNull { it.activityInfo.packageName.lowercase(Locale.getDefault()).contains(query) }
            ?: return null
        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(launch)
        return match.loadLabel(pm).toString()
    }
}
