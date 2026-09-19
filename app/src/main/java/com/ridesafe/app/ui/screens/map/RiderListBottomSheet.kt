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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesafe.app.data.model.Rider
import com.ridesafe.app.data.model.RiderStatus
import com.ridesafe.app.ui.theme.BikerBorder
import com.ridesafe.app.ui.theme.BikerCardBg
import com.ridesafe.app.ui.theme.BikerDarkBg
import com.ridesafe.app.ui.theme.BikerSurfaceElevated
import com.ridesafe.app.ui.theme.RideSafeTheme
import com.ridesafe.app.ui.theme.TextMuted
import com.ridesafe.app.ui.theme.TextPrimary
import com.ridesafe.app.ui.theme.TextSecondary
import com.ridesafe.app.util.LocationUtils

/**
 * RiderListBottomSheet displays the full roster of riders in the current session.
 *
 * For Java Developers:
 * In traditional Android (XML), this required:
 * - A custom BottomSheetDialogFragment XML layout
 * - A RecyclerView layout
 * - An item layout XML
 * - A Java RecyclerView.Adapter class
 * - A Java RecyclerView.ViewHolder class
 * - Manual notifyDataSetChanged() calls
 *
 * In Jetpack Compose, the entire sheet is represented in this single declarative function
 * using LazyColumn!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderListBottomSheet(
    riders: List<RiderWithDistance>,
    onRiderClick: (RiderWithDistance) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BikerCardBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BikerBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Riders in Group",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(BikerSurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${riders.size} Active",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (riders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Waiting for riders to join...",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    items(riders, key = { it.rider.id }) { riderItem ->
                        RiderListItem(
                            riderItem = riderItem,
                            onClick = { onRiderClick(riderItem) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiderListItem(
    riderItem: RiderWithDistance,
    onClick: () -> Unit
) {
    val rider = riderItem.rider
    val status = rider.riderStatus

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BikerSurfaceElevated)
            .border(1.dp, BikerBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status avatar
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(status.color.copy(alpha = 0.2f))
                .border(1.5.dp, status.color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = status.emoji,
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name and status
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rider.name.ifEmpty { "Rider" },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                if (riderItem.isCurrentUser) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "YOU",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Status label & updated time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = status.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = status.color,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Text(
                    text = " • ${LocationUtils.formatTimeAgo(rider.lastUpdated)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        // Distance / Speed badge
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = riderItem.formattedDistance,
                style = MaterialTheme.typography.titleMedium,
                color = if (riderItem.isCurrentUser) TextMuted else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            if (rider.speed > 1f) {
                Text(
                    text = LocationUtils.formatSpeed(rider.speed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101216)
@Composable
fun RiderListBottomSheetPreview() {
    val sampleRiders = listOf(
        RiderWithDistance(
            rider = Rider(id = "1", name = "Alex", status = RiderStatus.RIDING.name, speed = 18f, lastUpdated = System.currentTimeMillis()),
            distanceMeters = 0f,
            formattedDistance = "You",
            isCurrentUser = true
        ),
        RiderWithDistance(
            rider = Rider(id = "2", name = "Jordan", status = RiderStatus.REFUELING.name, speed = 0f, lastUpdated = System.currentTimeMillis() - 30_000),
            distanceMeters = 850f,
            formattedDistance = "850 m",
            isCurrentUser = false
        ),
        RiderWithDistance(
            rider = Rider(id = "3", name = "Sam", status = RiderStatus.PUNCTURE.name, speed = 0f, lastUpdated = System.currentTimeMillis() - 120_000),
            distanceMeters = 2400f,
            formattedDistance = "2.4 km",
            isCurrentUser = false
        )
    )

    RideSafeTheme {
        RiderListBottomSheet(
            riders = sampleRiders,
            onRiderClick = {},
            onDismiss = {}
        )
    }
}

