package com.ongoma.ui.arranger

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import com.ongoma.AudioEngine
import com.ongoma.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Main arranger screen for Ongoma v0.2
 *
 * Features:
 * - Interactive timeline with dynamic bar markers and hexagonal loop markers
 * - Track lanes with clips and waveform visualization
 * - Horizontal scroll with infinite timeline
 * - Playhead control (tap/drag)
 * - Audio playback with engine time sync
 * - Loop region support
 */
@Composable
fun ArrangerScreen(
    audioEngine: AudioEngine,
    onNavigateToKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    // === STATE MANAGEMENT ===
    var scrollX by remember { mutableFloatStateOf(0f) }
    var playheadPosition by remember { mutableFloatStateOf(1f) }
    var isPlaying by remember { mutableStateOf(false) }
    var loopRegion by remember { mutableStateOf<LoopRegion?>(LoopRegion(2, 4)) }
    val tempo = remember { 120 }

    // Sample tracks (would eventually load from persistence)
    val tracks = remember {
        listOf(
            ArrangerTrack(1, "Piano", ArrangerColors.TRACK_PINK, R.drawable.handle_piano),
            ArrangerTrack(2, "Chords", ArrangerColors.TRACK_ORANGE),
            ArrangerTrack(3, "Melody", ArrangerColors.TRACK_TEAL),
            ArrangerTrack(4, "Vocals", ArrangerColors.TRACK_GREEN)
        )
    }

    // Sample clips with notes
    val clips = remember {
        listOf(
            ArrangerClip(
                id = "clip1",
                trackId = 2,
                startBar = 2.5f,
                durationBars = 2f,
                name = "Chord",
                notes = listOf(
                    ClipNote("n1", 60, 0f, 0.5f), // C4 at start
                    ClipNote("n2", 64, 0.5f, 0.5f), // E4 at beat 2
                    ClipNote("n3", 67, 1f, 0.5f)  // G4 at beat 3
                )
            ),
            ArrangerClip(
                id = "clip2",
                trackId = 3,
                startBar = 3f,
                durationBars = 1.5f,
                name = "Riff"
            )
        )
    }

    // Scheduled notes tracking
    val scheduledNotes = remember { mutableStateMapOf<String, ScheduledNote>() }
    val coroutineScope = rememberCoroutineScope()

    // === PLAYBACK COROUTINE ===
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val startTime = audioEngine.getCurrentTime()
            val startBar = playheadPosition
            val secondsPerBar = TimeUtils.bpmToSecondsPerBar(tempo)

            while (isPlaying) {
                val currentTime = audioEngine.getCurrentTime()
                val elapsedSeconds = currentTime - startTime
                val elapsedBars = (elapsedSeconds / secondsPerBar).toFloat()

                playheadPosition = startBar + elapsedBars

                // Schedule upcoming notes (0.5 bar look-ahead)
                scheduleUpcomingNotes(
                    playheadPosition,
                    currentTime,
                    clips,
                    audioEngine,
                    scheduledNotes,
                    secondsPerBar
                )

                // Update at ~60fps
                delay(16)

                // Handle loop region
                loopRegion?.let { region ->
                    if (playheadPosition >= region.endBar) {
                        playheadPosition = region.startBar.toFloat()
                        // Clear scheduled notes and restart
                        scheduledNotes.keys.toList().forEach { noteId ->
                            scheduledNotes[noteId]?.let { note ->
                                audioEngine.stopNotePolyphonic(note.midiNote)
                            }
                        }
                        scheduledNotes.clear()
                    }
                }
            }
        } else {
            // Stop all notes when stopping playback
            scheduledNotes.keys.toList().forEach { noteId ->
                scheduledNotes[noteId]?.let { note ->
                    audioEngine.stopNotePolyphonic(note.midiNote)
                }
            }
            scheduledNotes.clear()
        }
    }

    // Compute highlighted ranges for background layer
    val highlightedRanges = remember(loopRegion) {
        loopRegion?.let {
            listOf(
                HighlightedRange(
                    startSection = it.startBar.toFloat(),
                    endSection = it.endBar.toFloat(),
                    color = ArrangerColors.AMBER_OVERLAY
                )
            )
        } ?: emptyList()
    }

    val totalSections = 32 // Dynamic - can extend based on project length

    // === UI LAYOUT ===
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ArrangerColors.BACKGROUND_BASE)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Track headers column (fixed width)
            TrackHeaderColumn(
                tracks = tracks,
                onTrackClick = { track ->
                    // Navigate to keyboard for this track
                    onNavigateToKeyboard()
                }
            )

            // Timeline + Track lanes content area
            Column(modifier = Modifier.weight(1f)) {
                // Timeline ruler at top
                TimelineCanvas(
                    scrollX = scrollX,
                    playheadPosition = playheadPosition,
                    loopRegion = loopRegion,
                    totalSections = totalSections,
                    modifier = Modifier.fillMaxWidth()
                )

                // Track lanes area with background layer and gesture handling
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                var gestureMode: GestureMode = GestureMode.Idle
                                val pointerStartPos = down.position
                                var dragDistance = 0f

                                // Check if starting on playhead
                                val barWidth = ArrangerDimensions.BAR_WIDTH
                                val playheadX = playheadPosition * barWidth - scrollX
                                val distanceToPlayhead = abs(down.position.x - playheadX)

                                if (distanceToPlayhead < ArrangerDimensions.PLAYHEAD_DRAG_THRESHOLD) {
                                    gestureMode = GestureMode.DraggingPlayhead(down.id.value)
                                }

                                // Process pointer events
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break

                                    when (gestureMode) {
                                        is GestureMode.Idle -> {
                                            // Determine gesture type based on movement
                                            dragDistance += abs(change.positionChange().x)

                                            if (dragDistance > ArrangerDimensions.SCROLL_DRAG_THRESHOLD) {
                                                gestureMode = GestureMode.Scrolling(
                                                    down.id.value,
                                                    pointerStartPos.x
                                                )
                                            }
                                        }

                                        is GestureMode.Scrolling -> {
                                            // Apply horizontal scroll
                                            scrollX -= change.positionChange().x
                                            scrollX = scrollX.coerceAtLeast(0f)
                                            change.consume()
                                        }

                                        is GestureMode.DraggingPlayhead -> {
                                            // Update playhead position
                                            playheadPosition = TimeUtils.screenXToBar(
                                                change.position.x,
                                                scrollX,
                                                barWidth
                                            ).coerceAtLeast(1f)
                                            change.consume()
                                        }

                                        is GestureMode.SelectingRegion -> {
                                            // TODO: Implement region selection
                                        }
                                    }
                                } while (event.changes.any { it.pressed })

                                // Gesture finished
                                when (gestureMode) {
                                    is GestureMode.Idle -> {
                                        // Tap: Move playhead to tap position
                                        playheadPosition = TimeUtils.screenXToBar(
                                            down.position.x,
                                            scrollX,
                                            barWidth
                                        ).coerceAtLeast(1f)
                                    }
                                    else -> {}
                                }
                            }
                        }
                ) {
                    // LAYER 1: Pure background with grid, playhead, highlights
                    ArrangerBackground(
                        scrollX = scrollX,
                        playheadPosition = playheadPosition,
                        totalSections = totalSections,
                        highlightedRanges = highlightedRanges,
                        modifier = Modifier.fillMaxSize()
                    )

                    // LAYER 2: Track content (clips, waveforms) - transparent, on top
                    TrackLanesCanvas(
                        tracks = tracks,
                        clips = clips,
                        scrollX = scrollX,
                        totalSections = totalSections,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Transport controls (floating at bottom center)
        TransportControls(
            isPlaying = isPlaying,
            tempo = tempo,
            onPlayPause = {
                isPlaying = !isPlaying
            },
            onStop = {
                isPlaying = false
                playheadPosition = 1f
                // Stop all notes
                audioEngine.stopAllNotes()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

// === HELPER CLASSES ===

data class ScheduledNote(
    val midiNote: Int,
    val startTime: Double,
    val duration: Double
)

// === NOTE SCHEDULING ===

private fun scheduleUpcomingNotes(
    currentBar: Float,
    currentTime: Double,
    clips: List<ArrangerClip>,
    audioEngine: AudioEngine,
    scheduledNotes: MutableMap<String, ScheduledNote>,
    secondsPerBar: Float
) {
    val lookAheadBars = 0.5f // Schedule 0.5 bars ahead

    clips.forEach { clip ->
        clip.notes.forEach { note ->
            val noteBar = clip.startBar + note.offsetBars

            // Check if note is in look-ahead window
            if (noteBar >= currentBar && noteBar < currentBar + lookAheadBars) {
                val noteId = "${clip.id}-${note.id}"

                // Only schedule if not already scheduled
                if (!scheduledNotes.containsKey(noteId)) {
                    audioEngine.playNotePolyphonic(note.midiNote)

                    val noteDuration = note.durationBars * secondsPerBar.toDouble()
                    scheduledNotes[noteId] = ScheduledNote(
                        note.midiNote,
                        currentTime,
                        noteDuration
                    )
                }
            }
        }
    }

    // Stop notes that finished
    scheduledNotes.entries.removeAll { (noteId, note) ->
        if (currentTime > note.startTime + note.duration) {
            audioEngine.stopNotePolyphonic(note.midiNote)
            true
        } else {
            false
        }
    }
}
