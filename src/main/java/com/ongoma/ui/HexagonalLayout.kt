package com.ongoma.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.PI

data class HexKey(
    val row: Int,
    val col: Int,
    val midiNote: Int,
    val noteName: String,        // e.g. "c3" only on C naturals, "c#" on black keys
    val center: Offset,
    val isBlackKey: Boolean
)

object HexagonalLayout {
    private const val SQRT_3 = 1.7320508f
//    private const val SQRT_3 = 1.732050808f

    // Geometry (HORIZONTAL_SPACING_FACTOR must be public for consistent hit detection)
    private const val VERTICAL_SPACING_FACTOR = 1.5f   // row height = hexSize * 1.5
    const val HORIZONTAL_SPACING_FACTOR = SQRT_3  // Public for MainActivity hit detection consistency

    // Full MIDI range (0–127) 0 .. 1 << 7
    private const val MIN_MIDI = 0
    private const val MAX_MIDI = 127

    // Default center — C3 = MIDI 48 (as confirmed in your meeting)
    const val DEFAULT_CENTER_MIDI = 48

    private val NOTE_NAMES = arrayOf("c", "c#", "d", "d#", "e", "f", "f#", "g", "g#", "a", "a#", "b")

    fun createHexagonPath(center: Offset, size: Float): Path {
        val path = Path()
        // this values was after hours of trials, its rounded edges.
        val cornerRadius = size * 0.12f
        // this is basic trig class & how we achieve the hexagon
        val anglesDeg = floatArrayOf(-30f, 30f, 90f, 150f, 210f, 270f)

        /*
        *
        * class DegreeRange(val start: Float, val end: Float, val step: Float) {
     * This is the "magic" that allows the for-loop
    operator fun iterator(): Iterator<Float> = object : Iterator<Float> {
        var current = start

        override fun hasNext(): Boolean = current < end

        override fun next(): Float {
            val result = current
            current += step
            return result
        }
    }
}
        *
        *
        *
        * */

        // Calculate all corner points
        val corners = anglesDeg.map { angle ->
            val rad = angle * PI.toFloat() / 180f
            Offset(center.x + size * cos(rad), center.y + size * sin(rad))
        }

        // Start from first corner
        val firstCorner = corners[0]
        val lastCorner = corners[5]

        // Calculate starting point (before first corner)
        val dx0 = firstCorner.x - lastCorner.x
        val dy0 = firstCorner.y - lastCorner.y
        val dist0 = sqrt(dx0 * dx0 + dy0 * dy0)
        val startX = firstCorner.x - (dx0 / dist0) * cornerRadius
        val startY = firstCorner.y - (dy0 / dist0) * cornerRadius
        path.moveTo(startX, startY)

        // Draw each edge with rounded corners
        for (i in 0..5) {
            val currentCorner = corners[i]
            val nextCorner = corners[(i + 1) % 6]

            // Vector to next corner
            val dx = nextCorner.x - currentCorner.x
            val dy = nextCorner.y - currentCorner.y
            val dist = sqrt(dx * dx + dy * dy)

            // Point before next corner (end of straight edge)
            val endX = nextCorner.x - (dx / dist) * cornerRadius
            val endY = nextCorner.y - (dy / dist) * cornerRadius
            path.lineTo(endX, endY)

            // Calculate point after corner (start of next edge)
            val nextNextCorner = corners[(i + 2) % 6]
            val dx2 = nextNextCorner.x - nextCorner.x
            val dy2 = nextNextCorner.y - nextCorner.y
            val dist2 = sqrt(dx2 * dx2 + dy2 * dy2)
            val afterX = nextCorner.x + (dx2 / dist2) * cornerRadius
            val afterY = nextCorner.y + (dy2 / dist2) * cornerRadius

            // Round the corner with quadratic bezier
            path.quadraticBezierTo(nextCorner.x, nextCorner.y, afterX, afterY)
        }

        path.close()
        return path
    }

    /**
     * TRUE WICKI-HAYDEN (wide layout)
     * Horizontal right:   +2 semitones
     * Up-right diagonal:  +7 semitones (perfect fifth)
     * Up-left diagonal:   +5 semitones (perfect fourth)
     * Straight up (2 rows): +12 semitones (octave)
     *
     * MIDI range: C0 (12) to C8 (108) with infinite wrapping
     */
    /**
     * MIDI Mapping Logic
     *
     * WICKI-HAYDEN (Hexagonal):
     * Horizontal: +2 semitones
     * Vertical: +7 semitones (Fifth)
     *
     * CHROMATIC FOURTH (Piano Grid):
     * Horizontal: +1 semitone
     * Vertical: +5 semitones (Fourth)
     */
    fun midiForRowCol(row: Int, col: Int, isPiano: Boolean = false): Int {
        if (isPiano) {
            // Chromatic Fourth Layout
            // Base(48) + Row*5 (fourths) + Col*1 (semitones)
            val rawMidi = 48 + (row * 5) + col

            // Wrap to full MIDI range 0-127 (128 notes)
            val rangeSize = 128
            val minMidi = 0

            val normalized = rawMidi - minMidi
            val wrapped = ((normalized % rangeSize) + rangeSize) % rangeSize
            return wrapped + minMidi
        } else {
            // Wicki-Hayden Layout
            val rawMidi = 48 + (row * 7) - ((row / 2) * 2) + (col * 2)

            // Wrap to full MIDI range 0-127 (128 notes)
            val rangeSize = 128
            val minMidi = 0

            val normalized = rawMidi - minMidi
            val wrapped = ((normalized % rangeSize) + rangeSize) % rangeSize
            return wrapped + minMidi
        }
    }

    fun noteName(midi: Int, showOctaveOnlyOnC: Boolean = true): String {
        // Support full MIDI range 0-127
        val clampedMidi = midi.coerceIn(0, 127)
        val pc = clampedMidi % 12
        val octave = (clampedMidi / 12) - 1  // Will be -1 to 9 for full range

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

    fun isBlackKey(midi: Int): Boolean {
        val pc = midi % 12
        return pc in setOf(1, 3, 6, 8, 10) // C# D# F# G# A#
    }

    fun centerForRowCol(
        row: Int,
        col: Int,
        hexSize: Float,
        scrollX: Float = 0f,
        scrollY: Float = 0f,
        verticalSpacingFactor: Float = VERTICAL_SPACING_FACTOR,
        isPiano: Boolean = false
    ): Offset {
        val horiz = hexSize * HORIZONTAL_SPACING_FACTOR
        val vert = hexSize * verticalSpacingFactor

        // Piano mode: Simple grid (no stagger)
        // Hex mode: Staggered rows (even rows shifted right)
        val xOffset = if (!isPiano && row % 2 == 0) horiz / 2f else 0f

        val x = col * horiz + xOffset - scrollX
        val y = -row * vert - scrollY // Negative row to go UP visually (standard Cartesian)

        return Offset(x, y)
    }

    /**
     * Infinite scrollable grid
     */
    fun generateVisibleKeys(
        hexSize: Float,
        screenWidth: Float,
        screenHeight: Float,
        scrollX: Float,
        scrollY: Float,
        approxVisibleCols: Int = 18,
        approxVisibleRows: Int = 30,
        verticalSpacingFactor: Float = VERTICAL_SPACING_FACTOR,
        isPiano: Boolean = false
    ): List<HexKey> {
        val keys = mutableListOf<HexKey>()

        val horiz = hexSize * HORIZONTAL_SPACING_FACTOR
        val vert = hexSize * verticalSpacingFactor

        val minX = -hexSize
        val maxX = screenWidth + hexSize
        val minY = -hexSize
        val maxY = screenHeight + hexSize

        val startCol = floor((minX + scrollX) / horiz).toInt() - 2
        val endCol = ceil((maxX + scrollX) / horiz).toInt() + 2

        // Note: y is inverted (up is positive row index)
        val startRow = floor(-(maxY + scrollY) / vert).toInt() - 2
        val endRow = ceil(-(minY + scrollY) / vert).toInt() + 2

        for (row in startRow..endRow) {
            for (col in startCol..endCol) {
                val center = centerForRowCol(
                    row, col, hexSize, scrollX, scrollY,
                    verticalSpacingFactor, isPiano
                )

                val midi = midiForRowCol(row, col, isPiano)
                keys.add(
                    HexKey(
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

    fun findKeyAt(
        touch: Offset,
        keys: List<HexKey>,
        hexSize: Float,
        isPiano: Boolean = false,
        verticalSpacingFactor: Float = VERTICAL_SPACING_FACTOR
    ): HexKey? {
        if (isPiano) {
            // Rectangular hit detection for piano
            val keyWidth = hexSize * HORIZONTAL_SPACING_FACTOR
            val keyHeight = hexSize * verticalSpacingFactor

            return keys.firstOrNull { key ->
                val left = key.center.x - keyWidth / 2f
                val right = key.center.x + keyWidth / 2f
                val top = key.center.y - keyHeight / 2f
                val bottom = key.center.y + keyHeight / 2f

                touch.x >= left && touch.x <= right && touch.y >= top && touch.y <= bottom
            }
        } else {
            // Circular/Hexagonal hit detection
            val nearestKey = keys.minByOrNull { key ->
                val dx = touch.x - key.center.x
                val dy = touch.y - key.center.y
                dx * dx + dy * dy
            }

            return nearestKey?.let { key ->
                val dx = touch.x - key.center.x
                val dy = touch.y - key.center.y
                val dist = sqrt(dx * dx + dy * dy)
                // Match the actual bitmap size: 1.85x scale factor / 2.0 (radius) = 0.925
                if (dist < hexSize * 0.85f) key else null
            }
        }
    }
}
