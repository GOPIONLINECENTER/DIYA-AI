package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.architecture.LiveConnectionStatus
import com.example.core.architecture.PcAgentStatus
import com.example.ui.theme.DisconnectedSlate
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.IceHolo
import com.example.ui.theme.StandbyAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.holoGlassPanel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HudStatusBar(
    currentTimeText: String,
    currentDateText: String,
    mobileUiReady: Boolean,
    pcAgentStatus: PcAgentStatus,
    geminiLiveStatus: LiveConnectionStatus,
    isWideLayout: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .holoGlassPanel(shape = RoundedCornerShape(20.dp), elevated = true)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(IceHolo, ElectricCyan, ElectricBlue)
                            )
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.brand_title),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = ElectricCyan,
                            fontSize = if (isWideLayout) 22.sp else 19.sp,
                            letterSpacing = 3.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.brand_subtitle),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            letterSpacing = 1.4.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = currentTimeText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp
                    )
                )
                Text(
                    text = currentDateText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ElectricCyan.copy(alpha = 0.85f),
                        letterSpacing = 0.9.sp
                    )
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ConnectionStatusBadge(
                icon = Icons.Outlined.PhoneAndroid,
                label = if (mobileUiReady) "Mobile • Authenticated" else "Mobile • Offline",
                dotColor = ElectricCyan,
                accentColor = ElectricCyan
            )
            ConnectionStatusBadge(
                icon = Icons.Outlined.Computer,
                label = if (pcAgentStatus.isConnected) "PC • Online" else "PC • Offline",
                dotColor = if (pcAgentStatus.isConnected) ElectricCyan else DisconnectedSlate,
                accentColor = if (pcAgentStatus.isConnected) ElectricCyan else DisconnectedSlate
            )
            ConnectionStatusBadge(
                icon = Icons.Outlined.CloudQueue,
                label = if (geminiLiveStatus.isConnected) "Gemini • Server Active" else "Gemini • Server Standby",
                dotColor = StandbyAmber,
                accentColor = StandbyAmber
            )
        }
    }
}

@Composable
private fun ConnectionStatusBadge(
    icon: ImageVector,
    label: String,
    dotColor: Color,
    accentColor: Color
) {
    val animatedDot by animateColorAsState(targetValue = dotColor, label = "statusDot")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(GlassSurface.copy(alpha = 0.65f))
            .border(
                width = 0.8.dp,
                color = accentColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(animatedDot)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor.copy(alpha = 0.9f),
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextPrimary.copy(alpha = 0.92f),
                letterSpacing = 0.5.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
