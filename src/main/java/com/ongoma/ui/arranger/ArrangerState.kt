package com.ongoma.ui.arranger

import androidx.compose.ui.graphics.Color

// === DATA MODELS ===

data class ArrangerTrack(
    val id: Int,
    val name: String,
    val color: Color,
    val iconRes: Int? = null,
    val volume: Float = 0.8f,
    val muted: Boolean = false,
    val solo: Boolean = false
)

data class ArrangerClip(
    val id: String,
    val trackId: Int,
    val startBar: Float,
    val durationBars: Float,
    val name: String? = null,
    val notes: List<ClipNote> = emptyList(),
    val waveformData: FloatArray = FloatArray(16) { 0.3f + (it % 3) * 0.1f } // Synthetic waveform
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArrangerClip

        if (id != other.id) return false
        if (trackId != other.trackId) return false
        if (startBar != other.startBar) return false
        if (durationBars != other.durationBars) return false
        if (name != other.name) return false
        if (notes != other.notes) return false
        if (!waveformData.contentEquals(other.waveformData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + trackId
        result = 31 * result + startBar.hashCode()
        result = 31 * result + durationBars.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + notes.hashCode()
        result = 31 * result + waveformData.contentHashCode()
        return result
    }
}

data class ClipNote(
    val id: String,
    val midiNote: Int,
    val offsetBars: Float, // Relative to clip start
    val durationBars: Float
)

data class LoopRegion(
    val startBar: Int,
    val endBar: Int
) {
    fun contains(bar: Int): Boolean = bar in startBar until endBar
    fun contains(bar: Float): Boolean = bar >= startBar && bar < endBar
}

// === COLOR CONSTANTS (Professional DAW Palette) ===

object ArrangerColors {
    // Background layer - very dark neutral, not pure black
    val BACKGROUND_BASE = Color(0xFF0D0D0F)      // Deep charcoal, almost black
    val BACKGROUND_SURFACE = Color(0xFF141418)   // Slightly lighter for depth

    // Grid system - subtle, never demanding attention
    val GRID_MAJOR = Color(0xFF2A2A2E)           // Major grid lines (section boundaries)
    val GRID_MINOR = Color(0xFF1A1A1D)           // Minor grid lines (subdivisions)
    val GRID_TICK = Color(0xFF232326)            // Beat tick marks ("sticks")

    // Accent colors - warm amber / muted orange
    val AMBER_HIGHLIGHT = Color(0xFFC65A1A)      // Primary highlight (RGB 198,90,26)
    val AMBER_MUTED = Color(0xFF8B4513)          // Muted amber for subtle accents
    val AMBER_OVERLAY = Color(0x40C65A1A)        // Semi-transparent overlay (~25% alpha)

    // Hexagon badge states
    val HEXAGON_DEFAULT_FILL = Color(0xFF1E1E22) // Dark badge background
    val HEXAGON_DEFAULT_TEXT = Color(0xFFB0B0B5) // Light gray text
    val HEXAGON_ACTIVE_FILL = Color(0xFFC65A1A)  // Amber when emphasized
    val HEXAGON_ACTIVE_TEXT = Color(0xFFFFFFFF)  // White text on active

    // Playhead
    val PLAYHEAD = Color(0xFFE8E8EC)             // Bright but not harsh white
    val PLAYHEAD_GLOW = Color(0x30E8E8EC)        // Subtle glow around playhead

    // Secondary accents
    val TEAL_ACCENT = Color(0xFF1A8A8A)          // Teal / cyan accent
    val GREEN_MUTED = Color(0xFF2D5A3D)          // Muted green

    // Track colors (from existing codebase patterns)
    val TRACK_PINK = Color(0xFFD81B60)
    val TRACK_ORANGE = Color(0xFFFF8F00)
    val TRACK_TEAL = Color(0xFF00ACC1)
    val TRACK_GREEN = Color(0xFF43A047)
}

// === BACKGROUND CONFIGURATION ===

object BackgroundConfig {
    // Subdivisions per section (beats in 4/4 time)
    const val BEATS_PER_SECTION = 4

    // Minor grid subdivisions per beat (for rhythmic texture)
    const val SUBDIVISIONS_PER_BEAT = 4

    // Visual weights (stroke widths in dp)
    const val MAJOR_GRID_WIDTH = 1.5f
    const val MINOR_GRID_WIDTH = 0.75f
    const val TICK_WIDTH = 0.5f
    const val PLAYHEAD_WIDTH = 2f

    // Hexagon geometry
    const val HEXAGON_SIZE = 16f          // Radius of hexagon badge
    const val HEXAGON_MARGIN_TOP = 12f    // Distance from top edge
    const val HEXAGON_SPACING = 0f        // Hexagons aligned with section start

    // Tick mark geometry
    const val TICK_HEIGHT = 8f            // Height of beat tick marks
    const val TICK_MARGIN_TOP = 48f       // Y position of tick marks
}

// === DIMENSION CONSTANTS ===

object ArrangerDimensions {
    const val TRACK_HEIGHT = 80f           // Height of each track lane in dp
    const val HEADER_WIDTH = 80f           // Width of track header column in dp
    const val BAR_WIDTH = 120f             // Width of one bar (4/4 time) in dp
    const val TIMELINE_HEIGHT = 60f        // Height of timeline ruler in dp
    const val PLAYHEAD_DRAG_THRESHOLD = 20f // Distance in px to detect playhead drag
    const val SCROLL_DRAG_THRESHOLD = 15f  // Distance in px to switch to scroll mode
}

// === TIME UTILITIES ===

object TimeUtils {
    /**
     * Convert BPM to seconds per bar (4/4 time signature)
     * @param bpm Beats per minute
     * @param beatsPerBar Number of beats in a bar (default 4 for 4/4 time)
     * @return Seconds per bar
     */
    fun bpmToSecondsPerBar(bpm: Int, beatsPerBar: Int = 4): Float {
        val beatsPerSecond = bpm / 60f
        return beatsPerBar / beatsPerSecond
    }

    /**
     * Convert bar position to seconds
     */
    fun barToSeconds(bar: Float, bpm: Int): Float {
        return bar * bpmToSecondsPerBar(bpm)
    }

    /**
     * Convert seconds to bar position
     */
    fun secondsToBar(seconds: Float, bpm: Int): Float {
        return seconds / bpmToSecondsPerBar(bpm)
    }

    /**
     * Convert screen X coordinate to bar position
     */
    fun screenXToBar(screenX: Float, scrollX: Float, barWidth: Float): Float {
        return (screenX + scrollX) / barWidth
    }

    /**
     * Convert bar position to screen X coordinate
     */
    fun barToScreenX(bar: Float, scrollX: Float, barWidth: Float): Float {
        return bar * barWidth - scrollX
    }

    /**
     * Calculate visible bar range for current scroll position
     * @return IntRange of visible bar numbers (inclusive)
     */
    fun calculateVisibleBars(scrollX: Float, screenWidth: Float, barWidth: Float): IntRange {
        val firstBar = maxOf(1, (scrollX / barWidth).toInt())
        val lastBar = ((scrollX + screenWidth) / barWidth).toInt() + 2 // +2 for padding
        return firstBar..lastBar
    }

    /**
     * Calculate visible clips for current scroll position
     */
    fun calculateVisibleClips(
        clips: List<ArrangerClip>,
        visibleBars: IntRange
    ): List<ArrangerClip> {
        val startBar = visibleBars.first.toFloat()
        val endBar = visibleBars.last.toFloat()
        return clips.filter { clip ->
            val clipEnd = clip.startBar + clip.durationBars
            // Clip is visible if it overlaps with visible range
            !(clipEnd < startBar || clip.startBar > endBar)
        }
    }
}

// === GESTURE STATE ===

sealed class GestureMode {
    object Idle : GestureMode()
    data class Scrolling(val pointerId: Long, val startX: Float) : GestureMode()
    data class DraggingPlayhead(val pointerId: Long) : GestureMode()
    data class SelectingRegion(val pointerId: Long, val startBar: Float) : GestureMode()
}
