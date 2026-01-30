package com.ongoma.ui.arranger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Transport controls for playback (play/pause/stop)
 * Displayed as floating buttons in the arranger screen
 */
@Composable
fun TransportControls(
    isPlaying: Boolean,
    tempo: Int,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(ArrangerColors.BACKGROUND_SURFACE.copy(alpha = 0.95f))
            .border(
                width = 1.dp,
                color = ArrangerColors.GRID_MAJOR,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause button
        TransportButton(
            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = onPlayPause,
            isActive = isPlaying
        )

        // Stop button
        TransportButton(
            icon = Icons.Default.Stop,
            contentDescription = "Stop",
            onClick = onStop,
            isActive = false
        )

        // Tempo display
        Column(
            modifier = Modifier.padding(start = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$tempo",
                color = ArrangerColors.PLAYHEAD,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "BPM",
                color = ArrangerColors.HEXAGON_DEFAULT_TEXT,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Individual transport button
 */
@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isActive: Boolean
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (isActive) ArrangerColors.AMBER_HIGHLIGHT
                else ArrangerColors.HEXAGON_DEFAULT_FILL
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = ArrangerColors.PLAYHEAD,
            modifier = Modifier.size(24.dp)
        )
    }
}
