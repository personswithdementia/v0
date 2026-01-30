package com.ongoma.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Label mode for the keyboard keys
 */
enum class LabelMode {
    NOTE_NAMES,    // C, D#, E, etc.
    SOLFEGE,       // Do, Re, Mi, etc.
    NUMBERS,       // MIDI note numbers
    HIDDEN         // No labels
}

/**
 * Layout mode for the keyboard
 */
enum class LayoutMode {
    WICKI_HAYDEN,  // Hexagonal Wicki-Hayden layout
    TRADITIONAL    // Traditional grid layout
}

/**
 * Floating control panel that overlays the keyboard
 * Provides access to various settings and modes
 */
@Composable
fun ControlPanel(
    labelMode: LabelMode,
    onLabelModeChange: (LabelMode) -> Unit,
    layoutMode: LayoutMode,
    onLayoutModeChange: (LayoutMode) -> Unit,
    keyboardVisible: Boolean,
    onKeyboardVisibilityChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Keyboard visibility toggle
            ControlButton(
                icon = Icons.Default.Keyboard,
                contentDescription = "Toggle Keyboard",
                onClick = { onKeyboardVisibilityChange(!keyboardVisible) }
            )

            // Label mode cycle button
            ControlButton(
                text = getLabelModeIcon(labelMode),
                contentDescription = "Cycle Label Mode",
                onClick = {
                    val nextMode = when (labelMode) {
                        LabelMode.NOTE_NAMES -> LabelMode.SOLFEGE
                        LabelMode.SOLFEGE -> LabelMode.NUMBERS
                        LabelMode.NUMBERS -> LabelMode.HIDDEN
                        LabelMode.HIDDEN -> LabelMode.NOTE_NAMES
                    }
                    onLabelModeChange(nextMode)
                }
            )

            // Layout mode toggle
            ControlButton(
                icon = Icons.Default.ViewModule,
                contentDescription = "Toggle Layout Mode",
                onClick = {
                    val nextMode = when (layoutMode) {
                        LayoutMode.WICKI_HAYDEN -> LayoutMode.TRADITIONAL
                        LayoutMode.TRADITIONAL -> LayoutMode.WICKI_HAYDEN
                    }
                    onLayoutModeChange(nextMode)
                }
            )

            // Settings button
            ControlButton(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                onClick = onSettingsClick
            )

            // Navigate/Forward button
            ControlButton(
                icon = Icons.Default.ArrowForward,
                contentDescription = "Navigate",
                onClick = onNavigateNext
            )
        }
    }
}

/**
 * Individual circular control button
 */
@Composable
private fun ControlButton(
    icon: ImageVector? = null,
    text: String? = null,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "buttonScale"
    )

    Box(
        modifier = modifier
            .size(56.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color(0xFF1A1A1A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    isPressed = false
                    onClick()
                }
            )
            .padding(2.dp)
            .background(Color(0xFF2A2A2A)),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        } else if (text != null) {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

/**
 * Get icon/text representation for label mode
 */
private fun getLabelModeIcon(mode: LabelMode): String {
    return when (mode) {
        LabelMode.NOTE_NAMES -> "A"
        LabelMode.SOLFEGE -> "Do"
        LabelMode.NUMBERS -> "#"
        LabelMode.HIDDEN -> "○"
    }
}

/**
 * Convert label mode to user-friendly string
 */
fun LabelMode.toDisplayString(): String {
    return when (this) {
        LabelMode.NOTE_NAMES -> "Note Names"
        LabelMode.SOLFEGE -> "Solfège"
        LabelMode.NUMBERS -> "MIDI Numbers"
        LabelMode.HIDDEN -> "Hidden"
    }
}

/**
 * Convert layout mode to user-friendly string
 */
fun LayoutMode.toDisplayString(): String {
    return when (this) {
        LayoutMode.WICKI_HAYDEN -> "Wicki-Hayden"
        LayoutMode.TRADITIONAL -> "Traditional Grid"
    }
}
