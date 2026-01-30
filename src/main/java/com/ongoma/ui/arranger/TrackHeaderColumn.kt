package com.ongoma.ui.arranger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Track header column displays fixed-width headers for each track
 * Uses LazyColumn for efficient rendering of many tracks
 */
@Composable
fun TrackHeaderColumn(
    tracks: List<ArrangerTrack>,
    onTrackClick: (ArrangerTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .width(ArrangerDimensions.HEADER_WIDTH.dp)
            .fillMaxHeight()
            .background(ArrangerColors.BACKGROUND_BASE)
    ) {
        items(tracks, key = { it.id }) { track ->
            TrackHeaderItem(
                track = track,
                onClick = { onTrackClick(track) }
            )
        }
    }
}

/**
 * Individual track header item with colored background and icon
 */
@Composable
private fun TrackHeaderItem(
    track: ArrangerTrack,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ArrangerDimensions.TRACK_HEIGHT.dp)
            .padding(4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(track.color.copy(alpha = 0.8f))
            .border(
                width = 1.dp,
                color = track.color.copy(alpha = 0.4f),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Track icon (if available)
            track.iconRes?.let { iconRes ->
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = track.name,
                    tint = ArrangerColors.PLAYHEAD,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Track name
            Text(
                text = track.name,
                color = ArrangerColors.PLAYHEAD,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
