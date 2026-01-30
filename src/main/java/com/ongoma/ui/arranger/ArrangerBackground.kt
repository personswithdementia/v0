package com.ongoma.ui.arranger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArrangerBackground(
        scrollX: Float,
        playheadPosition: Float,
        totalSections: Int,
        highlightedRanges: List<HighlightedRange> = emptyList(),
        beatsPerSection: Int = BackgroundConfig.BEATS_PER_SECTION,
        subdivisionsPerBeat: Int = BackgroundConfig.SUBDIVISIONS_PER_BEAT,
        modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val sectionWidth = ArrangerDimensions.BAR_WIDTH
        val visibleRange =
                calculateVisibleSections(scrollX, size.width, sectionWidth, totalSections)

        // === LAYER 1: Base background ===
        drawRect(color = ArrangerColors.BACKGROUND_BASE, topLeft = Offset.Zero, size = size)

        // === LAYER 2: Highlighted time ranges (behind grid) ===
        highlightedRanges.forEach { range -> drawHighlightedRange(range, scrollX, sectionWidth) }

        // === LAYER 3: Minor grid lines (subdivisions) ===
        drawMinorGridLines(
                visibleRange,
                scrollX,
                sectionWidth,
                beatsPerSection,
                subdivisionsPerBeat
        )

        // === LAYER 4: Major grid lines (section boundaries) ===
        drawMajorGridLines(visibleRange, scrollX, sectionWidth)

        // === LAYER 5: Beat tick marks ("sticks") ===
        drawBeatTicks(visibleRange, scrollX, sectionWidth, beatsPerSection)

        // === LAYER 6: Playhead ===
        drawPlayhead(playheadPosition, scrollX, sectionWidth)

        // === LAYER 7: Section number hexagonal badges ===
        drawSectionBadges(visibleRange, scrollX, sectionWidth, highlightedRanges, textMeasurer)
    }
}

/** Data class for highlighted time ranges */
data class HighlightedRange(
        val startSection: Float, // Can start mid-section (e.g., 2.5)
        val endSection: Float, // Can end mid-section
        val color: Color = ArrangerColors.AMBER_OVERLAY
)

/** Calculate which sections are visible in the current viewport */
private fun calculateVisibleSections(
        scrollX: Float,
        screenWidth: Float,
        sectionWidth: Float,
        totalSections: Int
): IntRange {
    val firstVisible = maxOf(1, (scrollX / sectionWidth).toInt())
    val lastVisible = minOf(totalSections, ((scrollX + screenWidth) / sectionWidth).toInt() + 2)
    return firstVisible..lastVisible
}

/**
 * Layer 2: Draw semi-transparent highlighted time ranges These sit behind grid lines but above the
 * base background
 */
private fun DrawScope.drawHighlightedRange(
        range: HighlightedRange,
        scrollX: Float,
        sectionWidth: Float
) {
    val startX = range.startSection * sectionWidth - scrollX
    val endX = range.endSection * sectionWidth - scrollX
    val width = endX - startX

    // Skip if completely offscreen
    if (endX < 0 || startX > size.width) return

    // Draw semi-transparent overlay
    drawRect(color = range.color, topLeft = Offset(startX, 0f), size = Size(width, size.height))
}

/**
 * Layer 3: Draw minor grid lines (subtle subdivisions within sections) Provides rhythmic visual
 * texture without clutter
 */
private fun DrawScope.drawMinorGridLines(
        visibleRange: IntRange,
        scrollX: Float,
        sectionWidth: Float,
        beatsPerSection: Int,
        subdivisionsPerBeat: Int
) {
    val totalSubdivisions = beatsPerSection * subdivisionsPerBeat
    val subdivisionWidth = sectionWidth / totalSubdivisions

    visibleRange.forEach { section ->
        val sectionStartX = section * sectionWidth - scrollX

        // Draw minor lines for each subdivision (skip section boundaries)
        for (sub in 1 until totalSubdivisions) {
            // Skip if this is a beat boundary (those get tick marks instead)
            if (sub % subdivisionsPerBeat == 0) continue

            val x = sectionStartX + sub * subdivisionWidth

            // Skip if offscreen
            if (x < -1f || x > size.width + 1f) continue

            drawLine(
                    color = ArrangerColors.GRID_MINOR,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = BackgroundConfig.MINOR_GRID_WIDTH
            )
        }
    }
}

/**
 * Layer 4: Draw major grid lines at section boundaries Slightly brighter, anchoring the numbered
 * sections
 */
private fun DrawScope.drawMajorGridLines(
        visibleRange: IntRange,
        scrollX: Float,
        sectionWidth: Float
) {
    visibleRange.forEach { section ->
        val x = section * sectionWidth - scrollX

        // Skip if offscreen
        if (x < -10f || x > size.width + 10f) return@forEach

        drawLine(
                color = ArrangerColors.GRID_MAJOR,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = BackgroundConfig.MAJOR_GRID_WIDTH
        )
    }
}

/**
 * Layer 5: Draw beat tick marks ("sticks") between section numbers Short vertical tick marks
 * representing beats/sub-beats Visually lighter than major grid lines, feels musical not mechanical
 */
private fun DrawScope.drawBeatTicks(
        visibleRange: IntRange,
        scrollX: Float,
        sectionWidth: Float,
        beatsPerSection: Int
) {
    val beatWidth = sectionWidth / beatsPerSection
    val tickY = BackgroundConfig.TICK_MARGIN_TOP
    val tickHeight = BackgroundConfig.TICK_HEIGHT

    visibleRange.forEach { section ->
        val sectionStartX = section * sectionWidth - scrollX

        // Draw tick for each beat (skip first beat - that's the section boundary)
        for (beat in 1 until beatsPerSection) {
            val x = sectionStartX + beat * beatWidth

            // Skip if offscreen
            if (x < -1f || x > size.width + 1f) continue

            drawLine(
                    color = ArrangerColors.GRID_TICK,
                    start = Offset(x, tickY),
                    end = Offset(x, tickY + tickHeight),
                    strokeWidth = BackgroundConfig.TICK_WIDTH
            )
        }
    }
}

/**
 * Layer 6: Draw playhead - single bright vertical line Extends full height, moves smoothly with
 * playback
 */
private fun DrawScope.drawPlayhead(playheadPosition: Float, scrollX: Float, sectionWidth: Float) {
    val x = playheadPosition * sectionWidth - scrollX

    // Only render if visible
    if (x < -10f || x > size.width + 10f) return

    // Subtle glow effect
    drawLine(
            color = ArrangerColors.PLAYHEAD_GLOW,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = BackgroundConfig.PLAYHEAD_WIDTH * 3f
    )

    // Main playhead line
    drawLine(
            color = ArrangerColors.PLAYHEAD,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = BackgroundConfig.PLAYHEAD_WIDTH
    )
}

/**
 * Layer 7: Draw section number hexagonal badges Geometrically precise, positioned at section starts
 * Default: dark background with light text Active (in highlighted range): amber fill with white
 * text
 */
private fun DrawScope.drawSectionBadges(
        visibleRange: IntRange,
        scrollX: Float,
        sectionWidth: Float,
        highlightedRanges: List<HighlightedRange>,
        textMeasurer: TextMeasurer
) {
    val hexSize = BackgroundConfig.HEXAGON_SIZE
    val marginTop = BackgroundConfig.HEXAGON_MARGIN_TOP

    visibleRange.forEach { section ->
        val x = section * sectionWidth - scrollX

        // Skip if offscreen
        if (x < -hexSize * 2 || x > size.width + hexSize * 2) return@forEach

        // Determine if this section is active (within any highlighted range)
        val isActive =
                highlightedRanges.any { range ->
                    section.toFloat() >= range.startSection && section.toFloat() < range.endSection
                }

        // Draw hexagon badge
        drawHexagonBadge(
                center = Offset(x, marginTop + hexSize),
                size = hexSize,
                isActive = isActive,
                sectionNumber = section,
                textMeasurer = textMeasurer
        )
    }
}

/**
 * Draw a single hexagonal badge with section number Geometrically precise flat hexagon (pointy-top
 * orientation)
 */
private fun DrawScope.drawHexagonBadge(
        center: Offset,
        size: Float,
        isActive: Boolean,
        sectionNumber: Int,
        textMeasurer: TextMeasurer
) {
    val fillColor =
            if (isActive) ArrangerColors.HEXAGON_ACTIVE_FILL
            else ArrangerColors.HEXAGON_DEFAULT_FILL
    val textColor =
            if (isActive) ArrangerColors.HEXAGON_ACTIVE_TEXT
            else ArrangerColors.HEXAGON_DEFAULT_TEXT

    // Create precise hexagon path (pointy-top, flat sides)
    val hexPath = createPreciseHexagonPath(center, size)

    // Draw filled hexagon
    drawPath(path = hexPath, color = fillColor, style = Fill)

    // Draw section number centered in hexagon
    val text = "$sectionNumber"
    val textLayoutResult =
            textMeasurer.measure(
                    text = text,
                    style =
                            TextStyle(
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                            )
            )

    drawText(
            textLayoutResult = textLayoutResult,
            topLeft =
                    Offset(
                            center.x - textLayoutResult.size.width / 2f,
                            center.y - textLayoutResult.size.height / 2f
                    )
    )
}

/**
 * Create geometrically precise hexagon path Pointy-top orientation with flat left and right sides
 * No rounded corners - sharp, precise geometry
 */
private fun createPreciseHexagonPath(center: Offset, size: Float): Path {
    val path = Path()

    // Pointy-top hexagon: vertices at 30, 90, 150, 210, 270, 330 degrees
    val anglesDeg = floatArrayOf(270f, 330f, 30f, 90f, 150f, 210f)

    val vertices =
            anglesDeg.map { angle ->
                val rad = angle * PI.toFloat() / 180f
                Offset(center.x + size * cos(rad), center.y + size * sin(rad))
            }

    // Move to first vertex
    path.moveTo(vertices[0].x, vertices[0].y)

    // Draw lines to remaining vertices
    for (i in 1 until vertices.size) {
        path.lineTo(vertices[i].x, vertices[i].y)
    }

    path.close()
    return path
}
