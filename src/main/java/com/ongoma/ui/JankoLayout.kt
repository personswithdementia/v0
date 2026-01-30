package com.ongoma.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Chromatic Fourth (isomorphic) keyboard layout
 *
 * Layout characteristics:
 * - Rectangular grid of keys
 * - Horizontal: +1 semitone per column (chromatic)
 * - Vertical: +5 semitones (perfect fourth) per row
 * - All chord shapes identical in any key (isomorphic)
 *
 * MIDI formula: base + col + (row * 5)
 */
data class JankoKey(
    val row: Int,
    val col: Int,
    val midiNote: Int,
    val noteName: String,
    val center: Offset,
    val isBlackKey: Boolean
)

object JankoLayout {
    private const val SQRT_3 = 1.732050808f

    // Jankó spacing constants
    const val HORIZONTAL_SPACING_FACTOR = SQRT_3  // Same as hex for consistency
    private const val VERTICAL_SPACING_FACTOR = 1.5f

    // Jankó layout constants
    private const val JANKO_ROWS = 6  // Unit cell height
    private const val JANKO_COLS = 6  // Unit cell width (repeats every 4 octaves)

    // MIDI range
    private const val MIN_MIDI = 0
    private const val MAX_MIDI = 127

    // Default center note
    const val DEFAULT_CENTER_MIDI = 48  // C3

    private val NOTE_NAMES = arrayOf("c", "c#", "d", "d#", "e", "f", "f#", "g", "g#", "a", "a#", "b")

    /**
     * Calculate MIDI note for Chromatic Fourth layout
     * Formula: base + col + (row * 5)
     *
     * - Horizontal: +1 semitone (chromatic)
     * - Vertical: +5 semitones (perfect fourth)
     */
    fun midiForRowCol(row: Int, col: Int, baseNote: Int = DEFAULT_CENTER_MIDI): Int {
        val rawMidi = baseNote + col + (row * 5)

        // Wrap to full MIDI range 0-127 (128 notes)
        // @phedwin - can we do 0..1 << 7 
        val rangeSize = 128
        val normalized = rawMidi - MIN_MIDI
        val wrapped = ((normalized % rangeSize) + rangeSize) % rangeSize
        return wrapped + MIN_MIDI
    }

    /**
     * Get note name from MIDI number
     */
    fun noteName(midi: Int, showOctaveOnlyOnC: Boolean = true): String {
        val clampedMidi = midi.coerceIn(0, 127)
        val pc = clampedMidi % 12
        val octave = (clampedMidi / 12) - 1

        val baseName = NOTE_NAMES[pc]
        val isNatural = pc in setOf(0, 2, 4, 5, 7, 9, 11) // C, D, E, F, G, A, B

        return if (isNatural) {
            if (pc == 0) {
                // C keys: show note + octave
                "$baseName$octave"
            } else {
                // Other natural keys: just note name
                baseName
            }
        } else {
            // Sharp keys: just note name
            baseName
        }
    }

    /**
     * Determine if a MIDI note is a black key (sharp)
     */
    fun isBlackKey(midi: Int): Boolean {
        val pc = midi % 12
        return pc in setOf(1, 3, 6, 8, 10) // C# D# F# G# A#
    }

    /**
     * Calculate screen position for a key
     */
    fun centerForRowCol(
        row: Int,
        col: Int,
        keyWidth: Float,
        keyHeight: Float,
        scrollX: Float = 0f,
        scrollY: Float = 0f
    ): Offset {
        val x = col * keyWidth - scrollX
        val y = -row * keyHeight - scrollY  // Negative for Cartesian (up is positive row)

        return Offset(x, y)
    }

    /**
     * Generate visible keys for the screen
     *
     * @param keyWidth Width of each key in pixels
     * @param keyHeight Height of each key in pixels
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @param scrollX Horizontal scroll offset
     * @param scrollY Vertical scroll offset
     * @param baseNote Center MIDI note (default C3 = 48)
     */
    fun generateVisibleKeys(
        keyWidth: Float,
        keyHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
        scrollX: Float,
        scrollY: Float,
        baseNote: Int = DEFAULT_CENTER_MIDI
    ): List<JankoKey> {
        val keys = mutableListOf<JankoKey>()

        // Calculate visible range with buffer
        val minX = -keyWidth
        val maxX = screenWidth + keyWidth
        val minY = -keyHeight
        val maxY = screenHeight + keyHeight

        // Calculate column and row range
        val startCol = floor((minX + scrollX) / keyWidth).toInt() - 2
        val endCol = ceil((maxX + scrollX) / keyWidth).toInt() + 2

        // Note: y is inverted (up is positive row index)
        val startRow = floor(-(maxY + scrollY) / keyHeight).toInt() - 2
        val endRow = ceil(-(minY + scrollY) / keyHeight).toInt() + 2

        for (row in startRow..endRow) {
            for (col in startCol..endCol) {
                val center = centerForRowCol(row, col, keyWidth, keyHeight, scrollX, scrollY)
                val midi = midiForRowCol(row, col, baseNote)

                keys.add(
                    JankoKey(
                        row = row,
                        col = col,
                        midiNote = midi,
                        noteName = noteName(midi, showOctaveOnlyOnC = true),
                        center = center,
                        isBlackKey = isBlackKey(midi)
                    )
                )
            }
        }

        return keys
    }

    /**
     * Find key at touch position (rectangular hit detection)
     */
    fun findKeyAt(
        touch: Offset,
        keys: List<JankoKey>,
        keyWidth: Float,
        keyHeight: Float
    ): JankoKey? {
        return keys.firstOrNull { key ->
            val left = key.center.x - keyWidth / 2f
            val right = key.center.x + keyWidth / 2f
            val top = key.center.y - keyHeight / 2f
            val bottom = key.center.y + keyHeight / 2f

            touch.x >= left && touch.x <= right && touch.y >= top && touch.y <= bottom
        }
    }
}
