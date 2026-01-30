package com.ongoma

class AudioEngine {
    companion object {
        private var libraryLoaded = false
        private var loadError: String? = null

        init {
            try {
                System.loadLibrary("ongoma")
                libraryLoaded = true
                android.util.Log.i("AudioEngine", "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                loadError = e.message
                android.util.Log.e("AudioEngine", "Failed to load native library: ${e.message}")
                libraryLoaded = false
            }
        }

        fun isLibraryLoaded(): Boolean = libraryLoaded
        fun getLoadError(): String? = loadError
    }

    private var initialized = false
    private var initError: String? = null
    private var lastNotePlayed: Int? = null
    private var lastPlayError: String? = null
    private var notePlayCount: Int = 0

    external fun nativeInit()
    external fun nativeShutdown()
    external fun nativePlayNote(midiNote: Int)
    external fun nativeStopNote()
    external fun nativePlayNotePolyphonic(midiNote: Int)
    external fun nativeStopNotePolyphonic(midiNote: Int)
    external fun nativeStopAllNotes()
    external fun nativeGetCurrentTime(): Double

    fun initialize() {
        if (!libraryLoaded) {
            initError = "Native library not loaded"
            android.util.Log.w("AudioEngine", "Cannot initialize - native library not loaded")
            return
        }
        try {
            nativeInit()
            initialized = true
            initError = null
            android.util.Log.i("AudioEngine", "Audio engine initialized")
        } catch (e: Exception) {
            initError = e.message
            android.util.Log.e("AudioEngine", "Failed to initialize: ${e.message}")
        }
    }

    fun shutdown() {
        if (!libraryLoaded || !initialized) return
        try {
            nativeShutdown()
            initialized = false
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Failed to shutdown: ${e.message}")
        }
    }

    // Legacy monophonic methods
    fun playNote(midiNote: Int) {
        if (!libraryLoaded || !initialized) return
        try {
            nativePlayNote(midiNote)
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Failed to play note: ${e.message}")
        }
    }

    fun stopNote() {
        if (!libraryLoaded || !initialized) return
        try {
            nativeStopNote()
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Failed to stop note: ${e.message}")
        }
    }

    // Polyphonic methods
    fun playNotePolyphonic(midiNote: Int) {
        if (!libraryLoaded) {
            lastPlayError = "Library not loaded"
            android.util.Log.e("AudioEngine", "⚠️ CRITICAL: Native library not loaded - cannot play audio!")
            android.util.Log.e("AudioEngine", "Check logcat for 'System.loadLibrary' errors during app startup")
            return
        }
        if (!initialized) {
            lastPlayError = "Engine not initialized"
            android.util.Log.e("AudioEngine", "⚠️ CRITICAL: Audio engine not initialized - cannot play audio!")
            return
        }
        try {
            nativePlayNotePolyphonic(midiNote)
            lastNotePlayed = midiNote
            lastPlayError = null
            notePlayCount++
            android.util.Log.d("AudioEngine", "✓ Playing MIDI note: $midiNote (count: $notePlayCount)")
        } catch (e: Exception) {
            lastPlayError = e.message
            android.util.Log.e("AudioEngine", "Failed to play polyphonic note: ${e.message}", e)
        }
    }

    fun stopNotePolyphonic(midiNote: Int) {
        if (!libraryLoaded || !initialized) {
            android.util.Log.w("AudioEngine", "Cannot stop note - audio not initialized")
            return
        }
        try {
            nativeStopNotePolyphonic(midiNote)
            android.util.Log.d("AudioEngine", "✓ Stopped MIDI note: $midiNote")
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Failed to stop polyphonic note: ${e.message}", e)
        }
    }

    fun stopAllNotes() {
        if (!libraryLoaded || !initialized) return
        try {
            nativeStopAllNotes()
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Failed to stop all notes: ${e.message}")
        }
    }

    // Get current audio engine time (for arranger playback sync)
    fun getCurrentTime(): Double {
        if (!libraryLoaded || !initialized) return 0.0
        return try {
            nativeGetCurrentTime()
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Failed to get current time: ${e.message}")
            0.0
        }
    }

    // Status getters for debugging UI
    fun isInitialized(): Boolean = initialized
    fun getInitError(): String? = initError
    fun getLastNotePlayed(): Int? = lastNotePlayed
    fun getLastPlayError(): String? = lastPlayError
    fun getNotePlayCount(): Int = notePlayCount
}
