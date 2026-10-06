package com.hasseena.jarvis

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import android.os.Handler
import android.os.Looper
import java.util.Locale

/**
 * User-enabled accessibility bridge. It only acts when Hasseena explicitly requests an
 * action and Android Accessibility access has been enabled by the user.
 */
class HasseenaAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var instance: HasseenaAccessibilityService? = null

        fun isEnabled(): Boolean = instance != null
        fun performGlobalActionSafe(action: Int): Boolean = try { instance?.performGlobalAction(action) == true } catch (_: Exception) { false }

        fun clickText(text: String, exact: Boolean = false): Boolean = instance?.clickTextInternal(text, exact) == true
        fun typeIntoFocused(text: String): Boolean = instance?.typeIntoFocusedInternal(text) == true
        fun scroll(direction: Int): Boolean = instance?.scrollInternal(direction) == true
        fun pressBack(): Boolean = performGlobalActionSafe(AccessibilityService.GLOBAL_ACTION_BACK)
        fun clickFirstButton(): Boolean = instance?.clickFirstButtonInternal() == true
        fun dumpVisibleText(): String = instance?.dumpVisibleTextInternal().orEmpty()
        fun openWhatsAppChat(contact: String): Boolean = instance?.openWhatsAppChatInternal(contact) == true
        fun isWhatsAppActive(): Boolean = instance?.isWhatsAppActiveInternal() == true
    }

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
    override fun onInterrupt() {}
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    private fun root(): AccessibilityNodeInfo? = rootInActiveWindow

    private fun allNodes(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        out.add(node)
        for (i in 0 until node.childCount) allNodes(node.getChild(i), out)
    }

    private fun clickTextInternal(target: String, exact: Boolean): Boolean {
        val wanted = target.trim().lowercase(Locale.getDefault())
        if (wanted.isBlank()) return false
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        allNodes(root(), nodes)
        val node = nodes.firstOrNull {
            val t = it.text?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
            val d = it.contentDescription?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
            (if (exact) t == wanted || d == wanted else t.contains(wanted) || d.contains(wanted)) &&
                (it.isClickable || it.parent != null)
        } ?: return false
        return clickNode(node)
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        repeat(6) {
            if (n?.isClickable == true) return n!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n?.parent
        }
        return false
    }

    private fun typeIntoFocusedInternal(text: String): Boolean {
        val focused = root()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun scrollInternal(direction: Int): Boolean {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        allNodes(root(), nodes)
        val scrollable = nodes.firstOrNull { it.isScrollable } ?: return false
        return scrollable.performAction(direction)
    }

    private fun clickFirstButtonInternal(): Boolean {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        allNodes(root(), nodes)
        val button = nodes.firstOrNull { it.className?.toString()?.contains("Button", true) == true && it.isVisibleToUser }
        return button?.let { clickNode(it) } == true
    }

    private fun isWhatsAppActiveInternal(): Boolean {
        return root()?.packageName?.toString() == "com.whatsapp"
    }

    private fun openWhatsAppChatInternal(contact: String): Boolean {
        if (!isEnabled()) return false
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (!isWhatsAppActiveInternal()) return@postDelayed
            if (!clickTextInternal("search", false) && !clickTextInternal("Search", false)) return@postDelayed
            handler.postDelayed({
                if (!isWhatsAppActiveInternal()) return@postDelayed
                if (!typeIntoFocusedInternal(contact)) return@postDelayed
                handler.postDelayed({
                    if (!isWhatsAppActiveInternal()) return@postDelayed
                    clickTextInternal(contact, false)
                }, 900)
            }, 500)
        }, 1200)
        return true
    }

    private fun dumpVisibleTextInternal(): String {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        allNodes(root(), nodes)
        return nodes.mapNotNull { it.text?.toString()?.trim() }
            .filter { it.isNotBlank() }.distinct().take(80).joinToString("\n")
    }
}
