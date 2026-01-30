package com.ongoma

import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.ongoma.ui.*
import com.ongoma.ui.arranger.ArrangerScreen

class MainActivity : ComponentActivity() {

    private val audioEngine = AudioEngine()

    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    Log.i("Ongoma", "Microphone permission granted")
                    initializeAudio()
                } else {
                    Log.e("Ongoma", "Microphone permission denied — audio will not work")
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full immersive mode
        window.decorView.systemUiVisibility =
                (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        actionBar?.hide()

        // Initialize audio immediately (no permission needed for playback)
        initializeAudio()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0A0F12))) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0F12)) {
                    // Navigation State
                    var currentScreen by remember { mutableStateOf(Screen.ARRANGER) }

                    when (currentScreen) {
                        Screen.ARRANGER -> {
                            ArrangerScreen(
                                    audioEngine = audioEngine,
                                    onNavigateToKeyboard = {
                                        audioEngine.stopAllNotes()
                                        currentScreen = Screen.KEYBOARD
                                    }
                            )
                        }
                        Screen.KEYBOARD -> {
                            HexagonalKeyboardScreen(
                                    audioEngine = audioEngine,
                                    onExit = {
                                        // Stop notes when exiting to arranger
                                        audioEngine.stopAllNotes()
                                        currentScreen = Screen.ARRANGER
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun initializeAudio() {
        try {
            audioEngine.initialize()
            if (AudioEngine.isLibraryLoaded()) {
                Log.i("Ongoma", "AudioEngine initialized successfully")
            } else {
                Log.e("Ongoma", "Native library failed to load - no audio available")
                runOnUiThread {
                    android.widget.Toast.makeText(
                                    this,
                                    "Audio initialization failed",
                                    android.widget.Toast.LENGTH_LONG
                            )
                            .show()
                }
            }
        } catch (e: Exception) {
            Log.e("Ongoma", "Failed to initialize AudioEngine", e)
            runOnUiThread {
                android.widget.Toast.makeText(
                                this,
                                "Audio error: ${e.message}",
                                android.widget.Toast.LENGTH_LONG
                        )
                        .show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // ✅ Stop all notes when app goes to background
        audioEngine.stopAllNotes()
        android.util.Log.d("LIFECYCLE", "onPause - stopped all notes")
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("LIFECYCLE", "onResume")
    }

    override fun onDestroy() {
        audioEngine.shutdown()
        super.onDestroy()
    }
}

enum class Screen {
    ARRANGER,
    KEYBOARD
}

@Composable
fun HexagonalKeyboardScreen(audioEngine: AudioEngine, onExit: () -> Unit) {
    // Keyboard skin state
    var currentSkin by remember { mutableStateOf(KeyboardSkin.HEXAGON) }

    // Control states
    var isScrollLocked by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) } // Performance monitor toggle

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val view = LocalView.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // ~10 visible columns → bigger hexagons, 5-6 rows visible
    val hexSize = screenWidthPx / (10f * 1.73205f) // √3 ≈ 1.73205

    // Dynamic layout parameters based on skin
    val isJanko = currentSkin == KeyboardSkin.JANKO
    val verticalSpacingFactor = 1.5f // Same for all modes

    // Jankó key sizing: 4 rows × 14 cols visible
    val jankoKeyWidth = screenWidthPx / 14f
    val jankoKeyHeight = screenHeightPx / 4f

    // Separate scroll state for each keyboard mode (maintains position when switching)
    // Hexagon scroll state
    var hexagonScrollX by
            remember(hexSize, screenWidthPx) {
                mutableStateOf(screenWidthPx / 2f - hexSize * 1.73205f)
            }
    var hexagonScrollY by
            remember(hexSize, screenHeightPx) {
                val centerRow = 2 // ✅ Center on octave 4-5 (C4 = MIDI 60, ~2 rows up from C3)
                val hexVert = hexSize * 1.5f // Hexagon vertical spacing
                mutableStateOf(-centerRow * hexVert - screenHeightPx / 2f)
            }

    // Jankó scroll state
    var jankoScrollX by
            remember(jankoKeyWidth, screenWidthPx) {
                mutableStateOf(screenWidthPx / 2f - jankoKeyWidth * 7f) // Center around C
            }
    var jankoScrollY by
            remember(jankoKeyHeight, screenHeightPx) {
                val centerRow = 2 // ✅ Center on octave 4-5 (C4 = MIDI 60, ~2 rows up from C3)
                mutableStateOf(-centerRow * jankoKeyHeight - screenHeightPx / 2f)
            }

    // Active scroll state based on current keyboard mode
    var scrollX by
            remember(currentSkin) {
                mutableStateOf(
                        when (currentSkin) {
                            KeyboardSkin.HEXAGON -> hexagonScrollX
                            KeyboardSkin.JANKO -> jankoScrollX
                        }
                )
            }
    var scrollY by
            remember(currentSkin) {
                mutableStateOf(
                        when (currentSkin) {
                            KeyboardSkin.HEXAGON -> hexagonScrollY
                            KeyboardSkin.JANKO -> jankoScrollY
                        }
                )
            }

    // Sync scroll changes back to mode-specific state
    LaunchedEffect(scrollX, scrollY, currentSkin) {
        when (currentSkin) {
            KeyboardSkin.HEXAGON -> {
                hexagonScrollX = scrollX
                hexagonScrollY = scrollY
            }
            KeyboardSkin.JANKO -> {
                jankoScrollX = scrollX
                jankoScrollY = scrollY
            }
        }
    }

    // Currently pressed keys (Row, Col) -> MidiNote
    // We store the pair to identify the specific key visually, and the MIDI for audio
    var pressedKeys by remember { mutableStateOf(mapOf<Pair<Int, Int>, Int>()) }

    // Stable reference for Janko keyboard pressed keys (fixes glow animation)
    var jankoPressed by remember { mutableStateOf(emptySet<Pair<Int, Int>>()) }

    // Handle bounds for key inactivation
    var handleBounds by remember { mutableStateOf(emptyList<androidx.compose.ui.geometry.Rect>()) }

    // Generate visible keys for hexagon mode - use derivedStateOf for efficient recomputation
    val hexagonVisibleKeys by remember {
        derivedStateOf {
            HexagonalLayout.generateVisibleKeys(
                    hexSize = hexSize,
                    screenWidth = screenWidthPx,
                    screenHeight = screenHeightPx,
                    scrollX = scrollX,
                    scrollY = scrollY,
                    approxVisibleCols = 18,
                    approxVisibleRows = 30,
                    verticalSpacingFactor = verticalSpacingFactor,
                    isPiano = false
            )
        }
    }

    // Generate Jankó visible keys
    val jankoVisibleKeys by remember {
        derivedStateOf {
            JankoLayout.generateVisibleKeys(
                    keyWidth = jankoKeyWidth,
                    keyHeight = jankoKeyHeight,
                    screenWidth = screenWidthPx,
                    screenHeight = screenHeightPx,
                    scrollX = scrollX,
                    scrollY = scrollY,
                    baseNote = JankoLayout.DEFAULT_CENTER_MIDI
            )
        }
    }

    Box(
            modifier =
                    Modifier.fillMaxSize().background(Color(0xFF444444)).pointerInput(Unit) {
                        // === MULTI-TOUCH POLYPHONIC HANDLING: Track ALL fingers independently ===
                        // Map: PointerId -> Map<KeyId, MidiNote>
                        val activePointers = mutableMapOf<Long, MutableMap<Pair<Int, Int>, Int>>()
                        val pointerStartPositions = mutableMapOf<Long, Offset>()
                        val pointerDragDistances = mutableMapOf<Long, Float>()
                        var primaryScrollPointerId: Long? = null
                        val dragThreshold = 15f

                        // Helper to check if a position is under any handle
                        fun isPositionUnderHandle(position: Offset): Boolean {
                            return handleBounds.any { handleRect ->
                                position.x >= handleRect.left &&
                                        position.x <= handleRect.right &&
                                        position.y >= handleRect.top &&
                                        position.y <= handleRect.bottom
                            }
                        }

                        // Helper to check if position is in handle zone
                        fun isInHandleZone(position: Offset): Boolean {
                            val handleZoneWidth = 250f
                            val handleZoneHeight = 200f
                            return position.x > (size.width - handleZoneWidth) &&
                                    position.y < handleZoneHeight
                        }

                        awaitPointerEventScope {
                            // Removed try-finally to prevent accidental note stopping during
                            // recomposition
                            while (true) {
                                val event = awaitPointerEvent()

                                event.changes.forEach { change ->
                                    val pointerId = change.id.value

                                    when {
                                        // ===== POINTER DOWN: New finger placed =====
                                        change.pressed && change.previousPressed == false -> {
                                            // If the event was already consumed (e.g. by
                                            // ControlHandles), ignore it
                                            if (change.isConsumed) {
                                                android.util.Log.d(
                                                        "MULTITOUCH",
                                                        "Pointer $pointerId consumed by child (Handles), ignoring"
                                                )
                                                return@forEach
                                            }

                                            val position = change.position
                                            pointerStartPositions[pointerId] = position
                                            pointerDragDistances[pointerId] = 0f

                                            // Initialize pointer's key map
                                            activePointers[pointerId] = mutableMapOf()

                                            // Find and play key
                                            if (isJanko) {
                                                val jankoKey =
                                                        JankoLayout.findKeyAt(
                                                                position,
                                                                jankoVisibleKeys,
                                                                jankoKeyWidth,
                                                                jankoKeyHeight
                                                        )
                                                jankoKey?.let { key ->
                                                    val keyId = key.row to key.col
                                                    // Removed isPositionUnderHandle check as we
                                                    // rely on consumption now

                                                    android.util.Log.d(
                                                            "MULTITOUCH",
                                                            "Pointer $pointerId DOWN: Jankó key ($keyId) midi=${key.midiNote}"
                                                    )

                                                    if (!pressedKeys.containsKey(keyId)) {
                                                        // Track this key for this pointer
                                                        activePointers[pointerId]!![keyId] =
                                                                key.midiNote
                                                        pressedKeys =
                                                                pressedKeys +
                                                                        (keyId to key.midiNote)
                                                        jankoPressed = pressedKeys.keys

                                                        // Play note
                                                        audioEngine.playNotePolyphonic(key.midiNote)
                                                        view.performHapticFeedback(
                                                                HapticFeedbackConstants.VIRTUAL_KEY
                                                        )
                                                        android.util.Log.d(
                                                                "MULTITOUCH",
                                                                "Playing note ${key.midiNote} for pointer $pointerId"
                                                        )
                                                    }
                                                }
                                            } else {
                                                val hexKey =
                                                        HexagonalLayout.findKeyAt(
                                                                position,
                                                                hexagonVisibleKeys,
                                                                hexSize,
                                                                isPiano = false,
                                                                verticalSpacingFactor =
                                                                        verticalSpacingFactor
                                                        )
                                                hexKey?.let { key ->
                                                    val keyId = key.row to key.col
                                                    // Removed isPositionUnderHandle check as we
                                                    // rely on consumption now

                                                    android.util.Log.d(
                                                            "MULTITOUCH",
                                                            "Pointer $pointerId DOWN: Hex key ($keyId) midi=${key.midiNote}"
                                                    )

                                                    if (!pressedKeys.containsKey(keyId)) {
                                                        // Track this key for this pointer
                                                        activePointers[pointerId]!![keyId] =
                                                                key.midiNote
                                                        pressedKeys =
                                                                pressedKeys +
                                                                        (keyId to key.midiNote)
                                                        jankoPressed = pressedKeys.keys

                                                        // Play note
                                                        audioEngine.playNotePolyphonic(key.midiNote)
                                                        view.performHapticFeedback(
                                                                HapticFeedbackConstants.VIRTUAL_KEY
                                                        )
                                                        android.util.Log.d(
                                                                "MULTITOUCH",
                                                                "Playing note ${key.midiNote} for pointer $pointerId"
                                                        )
                                                    }
                                                }
                                            }

                                            change.consume()
                                        }

                                        // ===== POINTER MOVE: Finger dragging =====
                                        change.pressed && change.previousPressed -> {
                                            val startPos =
                                                    pointerStartPositions[pointerId]
                                                            ?: change.position
                                            val currentDist =
                                                    (change.position - startPos).getDistance()
                                            pointerDragDistances[pointerId] = currentDist

                                            // If this pointer crossed drag threshold, use it for
                                            // scrolling
                                            // ✅ Increased threshold to 40f to prevent accidental
                                            // scrolling/note stops
                                            if (currentDist > 40f && primaryScrollPointerId == null
                                            ) {
                                                primaryScrollPointerId = pointerId
                                                android.util.Log.d(
                                                        "MULTITOUCH",
                                                        "Pointer $pointerId became scroll pointer"
                                                )

                                                // Stop notes from this pointer when it starts
                                                // scrolling
                                                activePointers[pointerId]?.forEach { (keyId, midi)
                                                    ->
                                                    audioEngine.stopNotePolyphonic(midi)
                                                    pressedKeys = pressedKeys - keyId
                                                }
                                                activePointers[pointerId]?.clear()
                                                jankoPressed = pressedKeys.keys
                                            }

                                            // Handle scrolling if this is the scroll pointer
                                            if (pointerId == primaryScrollPointerId &&
                                                            !isScrollLocked
                                            ) {
                                                val drag = change.position - change.previousPosition
                                                scrollX -= drag.x * 1.5f
                                                scrollY -= drag.y * 1.5f
                                                change.consume()
                                            }
                                        }

                                        // ===== POINTER UP: Finger lifted =====
                                        !change.pressed && change.previousPressed -> {
                                            android.util.Log.d(
                                                    "MULTITOUCH",
                                                    "Pointer $pointerId UP"
                                            )

                                            // Stop all notes from this pointer
                                            activePointers[pointerId]?.forEach { (keyId, midi) ->
                                                audioEngine.stopNotePolyphonic(midi)
                                                pressedKeys = pressedKeys - keyId
                                                android.util.Log.d(
                                                        "MULTITOUCH",
                                                        "Stopping note $midi from pointer $pointerId"
                                                )
                                            }

                                            // Clean up pointer tracking
                                            activePointers.remove(pointerId)
                                            pointerStartPositions.remove(pointerId)
                                            pointerDragDistances.remove(pointerId)

                                            // If this was the scroll pointer, reset scroll mode
                                            if (pointerId == primaryScrollPointerId) {
                                                primaryScrollPointerId = null
                                                android.util.Log.d(
                                                        "MULTITOUCH",
                                                        "Released scroll pointer"
                                                )
                                            }

                                            jankoPressed = pressedKeys.keys
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    }
    ) {
        // Render keyboard based on current skin
        when (currentSkin) {
            KeyboardSkin.HEXAGON -> {
                HexagonalKeyboard(
                        keys = hexagonVisibleKeys,
                        pressedKeys = pressedKeys.values.toSet(),
                        hexSize = hexSize,
                        modifier = Modifier.fillMaxSize(),
                        handleBounds = handleBounds
                )
            }
            KeyboardSkin.JANKO -> {
                // Chromatic Fourth isomorphic keyboard
                // Horizontal: +1 semitone (chromatic)
                // Vertical: +5 semitones (perfect fourth)
                // 4 rows × 14 cols visible
                JankoKeyboard(
                        keys = jankoVisibleKeys,
                        pressedKeys = jankoPressed, // ✅ Stable reference - fixes glow!
                        keyWidth = jankoKeyWidth,
                        keyHeight = jankoKeyHeight,
                        modifier = Modifier.fillMaxSize(),
                        handleBounds = handleBounds
                )
            }
        }

        // Control handles menu (3 handles: Piano, Settings, Lock)
        if (!showSettings) {
            ControlHandles(
                    isPianoMode = (currentSkin == KeyboardSkin.JANKO),
                    onPianoToggle = {
                        // ✅ Stop all notes before switching keyboard
                        audioEngine.stopAllNotes()
                        currentSkin =
                                when (currentSkin) {
                                    KeyboardSkin.HEXAGON -> KeyboardSkin.JANKO
                                    KeyboardSkin.JANKO -> KeyboardSkin.HEXAGON
                                }
                        android.util.Log.d(
                                "KEYBOARD",
                                "Switched to ${if (currentSkin == KeyboardSkin.JANKO) "Jankó" else "Hexagon"}, stopped all notes"
                        )
                    },
                    onLockToggle = { locked -> isScrollLocked = locked },
                    onSettingsClick = { showSettings = true },
                    onExitClick = onExit,
                    onHandleBoundsChanged = { bounds -> handleBounds = bounds }
            )
        }

        // Settings screen overlay
        if (showSettings) {
            SettingsScreen(
                    onBack = { showSettings = false },
                    showDebug = showDebug,
                    onDebugToggle = { showDebug = it }
            )
        }

        // Performance Debug Overlay (toggleable from settings)
        if (showDebug) {
            DebugOverlay(
                    audioEngine = audioEngine,
                    visibleKeyCount =
                            if (isJanko) jankoVisibleKeys.size else hexagonVisibleKeys.size,
                    pressedKeys = pressedKeys
            )
        }
    }
}
