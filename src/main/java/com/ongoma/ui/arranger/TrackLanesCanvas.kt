package com.ongoma.ui.arranger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/**
 * TrackLanesCanvas - Renders track lanes with clips and waveforms
 *
 * This component layers ON TOP of ArrangerBackground, rendering only
 * the track-specific content (clips, waveforms, labels).
 *
 * The background grid is handled by ArrangerBackground - this canvas
 * is transparent except for clip content.
 *
 * Visual hierarchy (back to front):
 * 1. Transparent base (ArrangerBackground shows through)
 * 2. Clip backgrounds with waveforms
 * 3. Clip borders
 * 4. Clip labels
 */
@Composable
fun TrackLanesCanvas(
    tracks: List<ArrangerTrack>,
    clips: List<ArrangerClip>,
    scrollX: Float,
    totalSections: Int = 32,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val sectionWidth = ArrangerDimensions.BAR_WIDTH
        val trackHeight = ArrangerDimensions.TRACK_HEIGHT
        val visibleRange = calculateVisibleSections(scrollX, size.width, sectionWidth, totalSections)
        val visibleClips = calculateVisibleClips(clips, visibleRange)

        // NOTE: No background drawn here - ArrangerBackground handles that
        // This canvas is transparent, layered on top

        // === LAYER 1: Clip backgrounds with waveforms ===
        visibleClips.forEach { clip ->
            val track = tracks.find { it.id == clip.trackId }
            if (track != null) {
                val trackIndex = tracks.indexOf(track)
                drawClipBackground(clip, track, trackIndex, scrollX, sectionWidth, trackHeight)
            }
        }

        // === LAYER 2: Clip borders ===
        visibleClips.forEach { clip ->
            val track = tracks.find { it.id == clip.trackId }
            if (track != null) {
                val trackIndex = tracks.indexOf(track)
                drawClipBorder(clip, track, trackIndex, scrollX, sectionWidth, trackHeight)
            }
        }

        // === LAYER 3: Clip labels ===
        visibleClips.forEach { clip ->
            val track = tracks.find { it.id == clip.trackId }
            if (track != null) {
                val trackIndex = tracks.indexOf(track)
                drawClipLabel(clip, trackIndex, scrollX, sectionWidth, trackHeight, textMeasurer)
            }
        }
    }
}

/**
 * Calculate visible sections
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
 * Calculate visible clips based on scroll position
 */
private fun calculateVisibleClips(clips: List<ArrangerClip>, visibleRange: IntRange): List<ArrangerClip> {
    val startSection = visibleRange.first.toFloat()
    val endSection = visibleRange.last.toFloat()
    return clips.filter { clip ->
        val clipEnd = clip.startBar + clip.durationBars
        !(clipEnd < startSection || clip.startBar > endSection)
    }
}

/**
 * Layer 1: Draw clip background with waveform visualization
 */
private fun DrawScope.drawClipBackground(
    clip: ArrangerClip,
    track: ArrangerTrack,
    trackIndex: Int,
    scrollX: Float,
    sectionWidth: Float,
    trackHeight: Float
) {
    val startX = clip.startBar * sectionWidth - scrollX
    val width = clip.durationBars * sectionWidth
    val trackY = trackIndex * trackHeight

    // Offscreen culling
    if (startX > size.width + 100f || startX + width < -100f) return

    val clipTop = trackY + 8f
    val clipHeight = trackHeight - 16f

    // Clip background with rounded corners
    drawRoundRect(
        color = track.color.copy(alpha = 0.85f),
        topLeft = Offset(startX, clipTop),
        size = Size(width, clipHeight),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // Waveform visualization
    drawWaveform(
        waveformData = clip.waveformData,
        startX = startX,
        trackY = clipTop,
        width = width,
        height = clipHeight
    )
}

/**
 * Layer 2: Draw clip border
 */
private fun DrawScope.drawClipBorder(
    clip: ArrangerClip,
    track: ArrangerTrack,
    trackIndex: Int,
    scrollX: Float,
    sectionWidth: Float,
    trackHeight: Float
) {
    val startX = clip.startBar * sectionWidth - scrollX
    val width = clip.durationBars * sectionWidth
    val trackY = trackIndex * trackHeight

    // Offscreen culling
    if (startX > size.width + 100f || startX + width < -100f) return

    val clipTop = trackY + 8f
    val clipHeight = trackHeight - 16f

    // Border
    drawRoundRect(
        color = track.color.copy(alpha = 0.4f),
        topLeft = Offset(startX, clipTop),
        size = Size(width, clipHeight),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = 1.5f)
    )
}

/**
 * Draw waveform bars inside clip
 */
private fun DrawScope.drawWaveform(
    waveformData: FloatArray,
    startX: Float,
    trackY: Float,
    width: Float,
    height: Float
) {
    val barCount = waveformData.size
    val barWidth = width / barCount
    val centerY = trackY + height / 2f

    waveformData.forEachIndexed { index, amplitude ->
        val x = startX + index * barWidth + barWidth / 2f

        // Skip if offscreen
        if (x < 0 || x > size.width) return@forEachIndexed

        val barHeight = amplitude * height * 0.55f

        // Waveform bar (darker overlay)
        drawRect(
            color = ArrangerColors.BACKGROUND_BASE.copy(alpha = 0.35f),
            topLeft = Offset(x - 1f, centerY - barHeight / 2f),
            size = Size(2f, barHeight)
        )
    }
}

/**
 * Layer 3: Draw clip name label
 */
private fun DrawScope.drawClipLabel(
    clip: ArrangerClip,
    trackIndex: Int,
    scrollX: Float,
    sectionWidth: Float,
    trackHeight: Float,
    textMeasurer: TextMeasurer
) {
    val startX = clip.startBar * sectionWidth - scrollX
    val trackY = trackIndex * trackHeight

    // Offscreen culling
    if (startX > size.width + 100f) return

    clip.name?.let { name ->
        val textLayoutResult = textMeasurer.measure(
            text = name,
            style = TextStyle(
                color = ArrangerColors.PLAYHEAD.copy(alpha = 0.9f),
                fontSize = 11.sp
            )
        )

        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(
                startX + 8f,
                trackY + 16f
            )
        )
    }
}
