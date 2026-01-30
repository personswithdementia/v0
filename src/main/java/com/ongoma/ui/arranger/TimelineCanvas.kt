package com.ongoma.ui.arranger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * TimelineCanvas - Compact timeline ruler for the top of the arranger
 *
 * This is a lightweight version that shows only the section badges and
 * the top portion of grid/playhead. The full background grid extends
 * through the ArrangerBackground component below.
 *
 * Visual hierarchy (back to front):
 * 1. Background strip
 * 2. Highlighted range indicator
 * 3. Beat tick marks
 * 4. Playhead (top portion)
 * 5. Section hexagonal badges
 */
@Composable
fun TimelineCanvas(
    scrollX: Float,
    playheadPosition: Float,
    loopRegion: LoopRegion?,
    totalSections: Int = 32,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(ArrangerDimensions.TIMELINE_HEIGHT.dp)
    ) {
        val sectionWidth = ArrangerDimensions.BAR_WIDTH
        val visibleRange = calculateVisibleSections(scrollX, size.width, sectionWidth, totalSections)

        // === LAYER 1: Background strip ===
        drawRect(
            color = ArrangerColors.BACKGROUND_SURFACE,
            topLeft = Offset.Zero,
            size = size
        )

        // === LAYER 2: Highlighted range indicator (loop region bar) ===
        loopRegion?.let { region ->
            drawLoopIndicator(region, scrollX, sectionWidth)
        }

        // === LAYER 3: Section dividers and beat ticks ===
        drawTimelineGrid(visibleRange, scrollX, sectionWidth)

        // === LAYER 4: Playhead (top portion with head) ===
        drawTimelinePlayhead(playheadPosition, scrollX, sectionWidth)

        // === LAYER 5: Section hexagonal badges ===
        drawTimelineBadges(visibleRange, scrollX, sectionWidth, loopRegion, textMeasurer)
    }
}

/**
 * Calculate which sections are visible
 */
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
 * Layer 2: Draw loop region indicator bar
 */
private fun DrawScope.drawLoopIndicator(
    region: LoopRegion,
    scrollX: Float,
    sectionWidth: Float
) {
    val startX = region.startBar * sectionWidth - scrollX
    val endX = region.endBar * sectionWidth - scrollX
    val width = endX - startX

    // Skip if offscreen
    if (endX < 0 || startX > size.width) return

    // Horizontal bar at top
    val barHeight = 4f
    val barY = 6f

    drawRect(
        color = ArrangerColors.AMBER_HIGHLIGHT,
        topLeft = Offset(startX, barY),
        size = Size(width, barHeight)
    )

    // Beat subdivision markers within loop region
    val beatsPerSection = BackgroundConfig.BEATS_PER_SECTION
    val beatWidth = sectionWidth / beatsPerSection

    for (section in region.startBar until region.endBar) {
        val sectionStartX = section * sectionWidth - scrollX
        for (beat in 0 until beatsPerSection) {
            val beatX = sectionStartX + beat * beatWidth
            if (beatX >= startX && beatX <= endX && beatX > 0 && beatX < size.width) {
                drawCircle(
                    color = ArrangerColors.BACKGROUND_BASE,
                    radius = 2.5f,
                    center = Offset(beatX, barY + barHeight / 2f)
                )
            }
        }
    }
}

/**
 * Layer 3: Draw section dividers and beat ticks
 */
private fun DrawScope.drawTimelineGrid(
    visibleRange: IntRange,
    scrollX: Float,
    sectionWidth: Float
) {
    val beatsPerSection = BackgroundConfig.BEATS_PER_SECTION
    val beatWidth = sectionWidth / beatsPerSection
    val tickY = 48f
    val tickHeight = 8f

    visibleRange.forEach { section ->
        val sectionX = section * sectionWidth - scrollX

        // Skip if offscreen
        if (sectionX < -20f || sectionX > size.width + 20f) return@forEach

        // Section boundary line
        drawLine(
            color = ArrangerColors.GRID_MAJOR,
            start = Offset(sectionX, tickY),
            end = Offset(sectionX, size.height),
            strokeWidth = BackgroundConfig.MAJOR_GRID_WIDTH
        )

        // Beat tick marks
        for (beat in 1 until beatsPerSection) {
            val beatX = sectionX + beat * beatWidth
            if (beatX > 0 && beatX < size.width) {
                drawLine(
                    color = ArrangerColors.GRID_TICK,
                    start = Offset(beatX, tickY),
                    end = Offset(beatX, tickY + tickHeight),
                    strokeWidth = BackgroundConfig.TICK_WIDTH
                )
            }
        }
    }
}

/**
 * Layer 4: Draw playhead with head indicator
 */
private fun DrawScope.drawTimelinePlayhead(
    playheadPosition: Float,
    scrollX: Float,
    sectionWidth: Float
) {
    val x = playheadPosition * sectionWidth - scrollX

    // Skip if offscreen
    if (x < -10f || x > size.width + 10f) return

    // Vertical line
    drawLine(
        color = ArrangerColors.PLAYHEAD,
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = BackgroundConfig.PLAYHEAD_WIDTH
    )

    // Playhead head (circle at top)
    drawCircle(
        color = ArrangerColors.PLAYHEAD,
        radius = 5f,
        center = Offset(x, 10f)
    )
}

/**
 * Layer 5: Draw section number hexagonal badges
 */
private fun DrawScope.drawTimelineBadges(
    visibleRange: IntRange,
    scrollX: Float,
    sectionWidth: Float,
    loopRegion: LoopRegion?,
    textMeasurer: TextMeasurer
) {
    val hexSize = BackgroundConfig.HEXAGON_SIZE
    val hexCenterY = 30f

    visibleRange.forEach { section ->
        val x = section * sectionWidth - scrollX

        // Skip if offscreen
        if (x < -hexSize * 2 || x > size.width + hexSize * 2) return@forEach

        // Determine if section is within loop region (active state)
        val isActive = loopRegion?.contains(section) == true

        // Draw hexagon badge
        val fillColor = if (isActive) ArrangerColors.HEXAGON_ACTIVE_FILL else ArrangerColors.HEXAGON_DEFAULT_FILL
        val textColor = if (isActive) ArrangerColors.HEXAGON_ACTIVE_TEXT else ArrangerColors.HEXAGON_DEFAULT_TEXT

        val center = Offset(x, hexCenterY)
        val hexPath = createPreciseHexagonPath(center, hexSize)

        // Draw filled hexagon
        drawPath(
            path = hexPath,
            color = fillColor,
            style = Fill
        )

        // Draw section number
        val text = "$section"
        val textLayoutResult = textMeasurer.measure(
            text = text,
            style = TextStyle(
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )

        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(
                center.x - textLayoutResult.size.width / 2f,
                center.y - textLayoutResult.size.height / 2f
            )
        )
    }
}

/**
 * Create geometrically precise hexagon path (pointy-top)
 */
private fun createPreciseHexagonPath(center: Offset, size: Float): Path {
    val path = Path()

    // Pointy-top hexagon vertices
    val anglesDeg = floatArrayOf(270f, 330f, 30f, 90f, 150f, 210f)

    val vertices = anglesDeg.map { angle ->
        val rad = angle * PI.toFloat() / 180f
        Offset(
            center.x + size * cos(rad),
            center.y + size * sin(rad)
        )
    }

    path.moveTo(vertices[0].x, vertices[0].y)
    for (i in 1 until vertices.size) {
        path.lineTo(vertices[i].x, vertices[i].y)
    }
    path.close()

    return path
}
