package com.hasseena.jarvis

class Memory(private val prefs: Prefs) {
    fun add(note: String) { val clean = note.trim(); if (clean.isBlank()) return; prefs.memory = (prefs.memory.lines().filter { it.isNotBlank() } + clean).takeLast(50).joinToString("\n") }
}
