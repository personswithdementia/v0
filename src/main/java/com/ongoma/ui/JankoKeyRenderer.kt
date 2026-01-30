package com.ongoma.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ongoma.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Jankó keyboard renderer with purple glow
 */
@Composable
fun JankoKeyboard(
    keys: List<JankoKey>,
    pressedKeys: Set<Pair<Int, Int>>,
    keyWidth: Float,
    keyHeight: Float,
    modifier: Modifier = Modifier,
    handleBounds: List<androidx.compose.ui.geometry.Rect> = emptyList()
) {
    // Track glow fade for recently released keys (4-second fade)
    val glowFadeMap = remember { mutableStateMapOf<Pair<Int, Int>, Animatable<Float, AnimationVector1D>>() }
    val previousPressedKeys = remember { mutableStateOf(setOf<Pair<Int, Int>>()) }

    // Helper to check if a key intersects with any handle
    fun isKeyUnderHandle(key: JankoKey): Boolean {
        if (handleBounds.isEmpty()) return false

        val keyRect = androidx.compose.ui.geometry.Rect(
            left = key.center.x - keyWidth / 2f,
            top = key.center.y - keyHeight / 2f,
            right = key.center.x + keyWidth / 2f,
            bottom = key.center.y + keyHeight / 2f
        )

        return handleBounds.any { handleRect ->
            keyRect.overlaps(handleRect)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        keys.forEach { key ->
            val keyId = key.row to key.col
            val isInactive = isKeyUnderHandle(key)
            val isPressed = !isInactive && (keyId in pressedKeys)
            val glowFade = if (isInactive) 0f else (glowFadeMap[keyId]?.value ?: 0f)

            JankoKey(
                key = key,
                keyWidth = keyWidth,
                keyHeight = keyHeight,
                isPressed = isPressed,
                glowFadeAlpha = glowFade
            )
        }
    }

    // Start 4-second glow fade when key is released
    LaunchedEffect(pressedKeys) {
        val justReleased = previousPressedKeys.value - pressedKeys

        justReleased.forEach { keyId ->
            glowFadeMap.remove(keyId) // Cancel any existing fade
            val anim = Animatable(1f)
            glowFadeMap[keyId] = anim
            launch {
                anim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 4000, easing = LinearOutSlowInEasing)
                )
                glowFadeMap.remove(keyId)
            }
        }

        previousPressedKeys.value = pressedKeys
    }
}

@Composable
fun JankoKey(
    key: JankoKey,
    keyWidth: Float,
    keyHeight: Float,
    isPressed: Boolean,
    glowFadeAlpha: Float
) {
    val center = key.center
    val scale = if (isPressed) 1.05f else 1.0f

    // Calculate position for the key (centered on the center point)
    val x = center.x - (keyWidth / 2f)
    val y = center.y - (keyHeight / 2f)

    val density = LocalDensity.current
    val widthDp = with(density) { keyWidth.toDp() }
    val heightDp = with(density) { keyHeight.toDp() }

    Box(
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(widthDp, heightDp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        // Layer 1: Base key (always visible)
        Image(
            painter = painterResource(
                if (key.isBlackKey) R.drawable.piano_key_black
                else R.drawable.piano_key_white
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // Layer 2: Purple glowing state (visible when pressed or fading)
        val showGlow = isPressed || glowFadeAlpha > 0f
        if (showGlow) {
            val glowOpacity = if (isPressed) 1.0f else glowFadeAlpha
            Image(
                painter = painterResource(
                    if (key.isBlackKey) R.drawable.piano_key_black_glowing
                    else R.drawable.piano_key_white_glowing
                ),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = glowOpacity },
                contentScale = ContentScale.FillBounds
            )
        }

        // Layer 3: Outlined state (visible when pressed or fading)
        val showOutline = isPressed || glowFadeAlpha > 0f
        if (showOutline) {
            val outlineOpacity = if (isPressed) 1.0f else glowFadeAlpha
            Image(
                painter = painterResource(
                    if (key.isBlackKey) R.drawable.piano_key_black_outlined
                    else R.drawable.piano_key_white_outlined
                ),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = outlineOpacity },
                contentScale = ContentScale.FillBounds
            )
        }

        // Layer 4: Text label
        JankoKeyLabel(key = key, keyWidth = keyWidth)
    }
}

@Composable
fun JankoKeyLabel(key: JankoKey, keyWidth: Float) {
    val (mainText, subText) = if (key.isBlackKey) {
        // Sharp keys: just show sharp symbol (e.g., "c#")
        key.noteName to null
    } else {
        // Natural keys: extract note and octave
        val notePart = key.noteName.replace(Regex("\\d"), "")
        val octavePart = key.noteName.replace(Regex("[^\\d]"), "")

        if (notePart.lowercase() == "c") {
            // C keys: show both note and octave
            notePart to octavePart
        } else {
            // Other natural keys: just note name
            notePart to null
        }
    }

    val textColor = if (key.isBlackKey) Color.White else Color.Black
    val shadowColor = if (key.isBlackKey) Color.Black else Color.White

    // Font sizes
    val mainStyle = TextStyle(
        color = textColor,
        fontSize = (keyWidth * 0.09f).sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily(Font(R.font.fira_mono_medium)),
        shadow = Shadow(shadowColor.copy(0.3f), offset = Offset(0f, 1f), blurRadius = 2f)
    )

    val subStyle = TextStyle(
        color = textColor.copy(alpha = 0.7f),
        fontSize = (keyWidth * 0.05f).sp,
        fontFamily = FontFamily(Font(R.font.fira_mono_medium))
    )

    // Center main text (note name) at key center, place octave number below
    Box(modifier = Modifier.fillMaxSize()) {
        // Center main text
        Text(
            text = mainText,
            style = mainStyle,
            modifier = Modifier.align(Alignment.Center)
        )
        // Place octave number below
        subText?.let {
            Text(
                text = it,
                style = subStyle,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (keyWidth * 0.055f).dp)
            )
        }
    }
}
