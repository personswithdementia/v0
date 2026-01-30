package com.ongoma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ongoma.AudioEngine
import com.ongoma.R
import kotlinx.coroutines.delay

/**
 * Real-time performance debug overlay
 * Shows FPS, frame time, audio status, and key rendering stats
 */
@Composable
fun DebugOverlay(
    audioEngine: AudioEngine,
    visibleKeyCount: Int = 0,
    pressedKeys: Map<Pair<Int, Int>, Int> = emptyMap(),  // (row,col) -> MIDI
    modifier: Modifier = Modifier
) {
    // FPS tracking
    var fps by remember { mutableStateOf(0f) }
    var frameTime by remember { mutableStateOf(0f) }
    var lastFrameTime by remember { mutableStateOf(System.nanoTime()) }
    var frameCount by remember { mutableStateOf(0) }
    var fpsUpdateTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Update FPS counter
    LaunchedEffect(Unit) {
        while (true) {
            val currentTime = System.nanoTime()
            val currentMillis = System.currentTimeMillis()

            frameCount++
            frameTime = (currentTime - lastFrameTime) / 1_000_000f // Convert to ms
            lastFrameTime = currentTime

            // Update FPS every 500ms
            if (currentMillis - fpsUpdateTime >= 500) {
                fps = frameCount * 1000f / (currentMillis - fpsUpdateTime)
                frameCount = 0
                fpsUpdateTime = currentMillis
            }

            delay(16) // ~60fps refresh
        }
    }

    // Audio status
    val libraryLoaded = AudioEngine.isLibraryLoaded()
    val engineInit = audioEngine.isInitialized()
    val noteCount = audioEngine.getNotePlayCount()
    val lastNote = audioEngine.getLastNotePlayed()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header
            Text(
                text = "PERFORMANCE DEBUG",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily(Font(R.font.fira_mono_medium))
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // FPS (color coded)
            val fpsColor = when {
                fps >= 55f -> Color(0xFF00FF00)  // Green
                fps >= 40f -> Color(0xFFFFAA00)  // Orange
                else -> Color(0xFFFF0000)        // Red
            }
            Text(
                text = "FPS: %.1f".format(fps),
                style = TextStyle(
                    color = fpsColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily(Font(R.font.fira_mono_medium))
                )
            )

            // Frame time (color coded)
            val frameColor = when {
                frameTime <= 16.7f -> Color(0xFF00FF00)  // Green (60fps)
                frameTime <= 33.3f -> Color(0xFFFFAA00)  // Orange (30fps)
                else -> Color(0xFFFF0000)                // Red
            }
            Text(
                text = "Frame: %.1fms".format(frameTime),
                style = TextStyle(
                    color = frameColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily(Font(R.font.fira_mono_medium))
                )
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Keys rendered
            Text(
                text = "Keys: $visibleKeyCount",
                style = TextStyle(
                    color = Color.Cyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily(Font(R.font.fira_mono_medium))
                )
            )

            // Audio status
            Text(
                text = "Audio: ${if (libraryLoaded && engineInit) "✓" else "✗"}",
                style = TextStyle(
                    color = if (libraryLoaded && engineInit) Color.Green else Color.Red,
                    fontSize = 10.sp,
                    fontFamily = FontFamily(Font(R.font.fira_mono_medium))
                )
            )

            if (noteCount > 0) {
                Text(
                    text = "Notes: $noteCount",
                    style = TextStyle(
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily(Font(R.font.fira_mono_medium))
                    )
                )
            }

            lastNote?.let {
                Text(
                    text = "Last: MIDI $it",
                    style = TextStyle(
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily(Font(R.font.fira_mono_medium))
                    )
                )
            }

            // Currently pressed keys
            if (pressedKeys.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "PRESSED (${pressedKeys.size}):",
                    style = TextStyle(
                        color = Color.Yellow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily(Font(R.font.fira_mono_medium))
                    )
                )
                pressedKeys.values.take(5).forEach { midiNote ->
                    Text(
                        text = "  MIDI $midiNote",
                        style = TextStyle(
                            color = Color(0xFF00FF88),
                            fontSize = 9.sp,
                            fontFamily = FontFamily(Font(R.font.fira_mono_medium))
                        )
                    )
                }
                if (pressedKeys.size > 5) {
                    Text(
                        text = "  +${pressedKeys.size - 5} more...",
                        style = TextStyle(
                            color = Color.Gray,
                            fontSize = 8.sp,
                            fontFamily = FontFamily(Font(R.font.fira_mono_medium))
                        )
                    )
                }
            }
        }
    }
}
