package com.ongoma.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ongoma.R
import kotlinx.coroutines.launch

@Composable
fun HexagonalKeyboard(
    keys: List<HexKey>,
    pressedKeys: Set<Int>,
    hexSize: Float,
    modifier: Modifier = Modifier,
    handleBounds: List<androidx.compose.ui.geometry.Rect> = emptyList()
) {
    val textMeasurer = rememberTextMeasurer()
    val context = LocalContext.current

    // SVG Aspect Ratio: 225w / 249h = 0.9036
    // We want the width to match the grid width (hexSize * sqrt(3)) plus some overlap
    // The grid width is hexSize * 1.732
    // Let's make the bitmap width slightly larger than the grid cell width to ensure coverage
    val bitmapWidth = (hexSize * 1.7f).toInt()
    val bitmapHeight = (bitmapWidth * (249f / 225f)).toInt()

    // Load SVG bitmaps asynchronously to avoid blocking UI
    var tealNormal by remember { mutableStateOf<ImageBitmap?>(null) }
    var tealGlowing by remember { mutableStateOf<ImageBitmap?>(null) }
    var tealOutlined by remember { mutableStateOf<ImageBitmap?>(null) }
    var blackNormal by remember { mutableStateOf<ImageBitmap?>(null) }
    var blackGlowing by remember { mutableStateOf<ImageBitmap?>(null) }
    var blackOutlined by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(bitmapWidth) {
        // Load in background
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            tealNormal = HexagonBitmapCache.getBitmapFromVector(context, R.drawable.hex_key_teal, bitmapWidth, bitmapHeight)
            tealGlowing = HexagonBitmapCache.getBitmapFromVector(context, R.drawable.hex_key_teal_glowing, bitmapWidth, bitmapHeight)
            tealOutlined = HexagonBitmapCache.getBitmapFromVector(context, R.drawable.hex_key_teal_outlined, bitmapWidth, bitmapHeight)
            blackNormal = HexagonBitmapCache.getBitmapFromVector(context, R.drawable.hex_key_black, bitmapWidth, bitmapHeight)
            blackGlowing = HexagonBitmapCache.getBitmapFromVector(context, R.drawable.hex_key_black_glowing, bitmapWidth, bitmapHeight)
            blackOutlined = HexagonBitmapCache.getBitmapFromVector(context, R.drawable.hex_key_black_outlined, bitmapWidth, bitmapHeight)
        }
    }

    // FIXED: Instant glow with proper fade-out
    val glowFadeMap = remember { mutableStateMapOf<Int, Animatable<Float, AnimationVector1D>>() }
    val previousPressedKeys = remember { mutableStateOf(setOf<Int>()) }
    val scope = rememberCoroutineScope()
    val fadeJobs = remember { mutableMapOf<Int, kotlinx.coroutines.Job>() }

    // INSTANT glow on press, 4s fade on release
    LaunchedEffect(pressedKeys) {
        val justPressed = pressedKeys - previousPressedKeys.value
        val justReleased = previousPressedKeys.value - pressedKeys

        // When pressed: Cancel any existing fade (key will glow via pressedKeys check)
        justPressed.forEach { midiNote ->
            fadeJobs[midiNote]?.cancel()
            glowFadeMap.remove(midiNote)
        }

        // 4-second fade-out when released
        justReleased.forEach { midiNote ->
            // Cancel any previous job just in case
            fadeJobs[midiNote]?.cancel()
            
            // Start fade from full brightness
            val anim = Animatable(1f)
            glowFadeMap[midiNote] = anim
            
            // Launch in the composable's scope so it survives LaunchedEffect restarts
            fadeJobs[midiNote] = scope.launch {
                try {
                    anim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 4000, easing = LinearOutSlowInEasing)
                    )
                } finally {
                    // Only remove if this specific animation finished (wasn't cancelled/replaced)
                    // But since we cancel on press, simple removal is fine
                    glowFadeMap.remove(midiNote)
                    fadeJobs.remove(midiNote)
                }
            }
        }

        previousPressedKeys.value = pressedKeys
    }

    // Helper to check if a key intersects with any handle
    fun isKeyUnderHandle(key: HexKey): Boolean {
        if (handleBounds.isEmpty()) return false

        // Approximate hexagon bounds with a circle for simpler collision
        val keyRadius = hexSize
        return handleBounds.any { handleRect ->
            val handleCenter = Offset(
                (handleRect.left + handleRect.right) / 2f,
                (handleRect.top + handleRect.bottom) / 2f
            )
            val distance = (key.center - handleCenter).getDistance()
            val handleRadius = (handleRect.width + handleRect.height) / 4f
            distance < (keyRadius + handleRadius)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // OPTIMIZATION: Batch draw operations - draw all normal keys first, then glows
        val normalKeys = mutableListOf<Pair<HexKey, Offset>>()
        val glowingKeys = mutableListOf<Triple<HexKey, Offset, Float>>()

        // First pass: categorize keys
        keys.forEach { key ->
            val topLeft = Offset(
                key.center.x - bitmapWidth / 2f,
                key.center.y - bitmapHeight / 2f
            )

            // Skip if completely offscreen (optimization)
            if (topLeft.x > size.width + bitmapWidth || topLeft.x < -bitmapWidth ||
                topLeft.y > size.height + bitmapHeight || topLeft.y < -bitmapHeight) {
                return@forEach
            }

            normalKeys.add(key to topLeft)

            // Check if key is under a handle - if so, no glow
            val isInactive = isKeyUnderHandle(key)

            // Calculate glow: 1.0 if pressed, fade value if fading, 0.0 otherwise
            val glowFade = if (isInactive) {
                0f  // No glow for keys under handles
            } else {
                when {
                    pressedKeys.contains(key.midiNote) -> 1f  // INSTANT for currently pressed keys
                    previousPressedKeys.value.contains(key.midiNote) -> 1f // Bridge the gap between release and animation start
                    else -> glowFadeMap[key.midiNote]?.value ?: 0f  // Fade value for releasing keys
                }
            }
            if (glowFade > 0.001f) {  // Skip negligible glow (performance)
                glowingKeys.add(Triple(key, topLeft, glowFade))
            }
        }

        // Second pass: draw all normal keys (batched)
        normalKeys.forEach { (key, topLeft) ->
            val normalBitmap = if (key.isBlackKey) blackNormal else tealNormal
            normalBitmap?.let { bmp ->
                drawImage(image = bmp, topLeft = topLeft, alpha = 1.0f)
            }
        }

        // Third pass: draw all glow effects (batched)
        glowingKeys.forEach { (key, topLeft, glowFade) ->
            // Glowing bitmap
            val glowingBitmap = if (key.isBlackKey) blackGlowing else tealGlowing
            glowingBitmap?.let { bmp ->
                drawImage(image = bmp, topLeft = topLeft, alpha = glowFade)
            }

            // Outlined bitmap
            val outlinedBitmap = if (key.isBlackKey) blackOutlined else tealOutlined
            outlinedBitmap?.let { bmp ->
                drawImage(image = bmp, topLeft = topLeft, alpha = glowFade)
            }
        }

        // Fourth pass: draw all text labels
        normalKeys.forEach { (key, topLeft) ->
            // Draw text on top
            val (mainText, subText) = if (key.isBlackKey) {
                key.noteName to null  // Use normal # symbol, not ♯
            } else {
                val notePart = key.noteName.replace(Regex("\\d"), "")
                val octavePart = key.noteName.replace(Regex("[^\\d]"), "")
                if (notePart.lowercase() == "c") {
                    notePart to octavePart
                } else {
                    notePart to null
                }
            }

            val mainStyle = TextStyle(
                color = Color.White,
                fontSize = (hexSize * 0.22f).sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily(Font(R.font.fira_mono_medium)),
                shadow = Shadow(Color.Black.copy(0.6f), offset = Offset(0f, 1f), blurRadius = 4f)
            )

            val subStyle = TextStyle(
                color = Color(0xFFCCCCCC),
                fontSize = (hexSize * 0.08f).sp,
                fontFamily = FontFamily(Font(R.font.fira_mono_medium))
            )

            val combinedText = buildAnnotatedString {
                withStyle(SpanStyle(color = mainStyle.color, fontSize = mainStyle.fontSize, fontWeight = mainStyle.fontWeight, fontFamily = mainStyle.fontFamily)) {
                    append(mainText)
                }
                subText?.let {
                    append("\n")
                    withStyle(SpanStyle(color = subStyle.color, fontSize = subStyle.fontSize, fontFamily = subStyle.fontFamily)) {
                        append(it)
                    }
                }
            }

            val measured = textMeasurer.measure(combinedText, style = TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center, shadow = mainStyle.shadow))
            val textPos = Offset(
                key.center.x - measured.size.width / 2f,
                key.center.y - measured.size.height / 2f
            )
            drawText(measured, topLeft = textPos)
        }
    }
    // No fade animation - instant state switching for crisp feedback like the handles!
}
