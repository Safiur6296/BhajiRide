package com.ridesafe.app.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ridesafe.app.data.model.RiderStatus
import com.ridesafe.app.ui.theme.BikerBorder
import com.ridesafe.app.ui.theme.BikerCardBg
import com.ridesafe.app.ui.theme.BikerDarkBg
import com.ridesafe.app.ui.theme.BikerSurfaceElevated
import com.ridesafe.app.ui.theme.RideSafeTheme
import com.ridesafe.app.ui.theme.TextPrimary
import com.ridesafe.app.ui.theme.TextSecondary

/**
 * StopStatusDialog allows a rider to quickly broadcast why they stopped
 * or resume riding with a single tap.
 */
@Composable
fun StopStatusDialog(
    currentStatus: RiderStatus,
    onStatusSelected: (RiderStatus) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BikerCardBg),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BikerBorder, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Update Ride Status",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Notify your group of stops",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // List of selectable statuses
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RiderStatus.entries.forEach { status ->
                        val isSelected = status == currentStatus

                        StatusOptionItem(
                            status = status,
                            isSelected = isSelected,
                            onClick = {
                                onStatusSelected(status)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusOptionItem(
    status: RiderStatus,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        status.color.copy(alpha = 0.18f)
    } else {
        BikerSurfaceElevated
    }

    val borderColor = if (isSelected) {
        status.color
    } else {
        BikerBorder
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status Emoji with colored circular background
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(status.color.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = status.emoji,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (status == RiderStatus.RIDING) "Resume Riding" else status.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) status.color else TextPrimary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = when (status) {
                    RiderStatus.RIDING -> "Moving normally with the pack"
                    RiderStatus.REFUELING -> "Stopped at a gas station"
                    RiderStatus.PUNCTURE -> "Flat tire or mechanical repair"
                    RiderStatus.REST -> "Breather, snack, or coffee"
                    RiderStatus.EMERGENCY -> "Immediate assistance needed"
                    RiderStatus.OTHER -> "Temporary pause / waiting"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(status.color)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101216)
@Composable
fun StopStatusDialogPreview() {
    RideSafeTheme {
        StopStatusDialog(
            currentStatus = RiderStatus.REFUELING,
            onStatusSelected = {},
            onDismiss = {}
        )
    }
}

