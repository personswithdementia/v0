package com.ongoma.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ongoma.R

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    showDebug: Boolean = false,
    onDebugToggle: (Boolean) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F12))
    ) {
        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Settings",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily(Font(R.font.fira_mono_medium)),
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Debug Performance Monitor Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(8.dp))
                    .clickable { onDebugToggle(!showDebug) }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Performance Monitor",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily(Font(R.font.fira_mono_medium)),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Show FPS, frame time, and stats",
                        fontSize = 12.sp,
                        fontFamily = FontFamily(Font(R.font.fira_mono_medium)),
                        color = Color(0xFF999999)
                    )
                }
                Switch(
                    checked = showDebug,
                    onCheckedChange = onDebugToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00FF88),
                        checkedTrackColor = Color(0xFF00FF88).copy(alpha = 0.5f),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )
            }
        }

        // Exit button (top-right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-12).dp, y = 12.dp)
                .size(65.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.3f),
                    spotColor = Color.Black.copy(alpha = 0.3f)
                )
                .clickable { onBack() }
        ) {
            Image(
                painter = painterResource(R.drawable.handle_exit),
                contentDescription = "Exit settings",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
