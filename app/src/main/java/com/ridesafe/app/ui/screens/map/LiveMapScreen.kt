package com.ridesafe.app.ui.screens.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptor
import com.ridesafe.app.ui.theme.RideSafeTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.ridesafe.app.data.model.RiderStatus
import com.ridesafe.app.ui.theme.BikerBorder
import com.ridesafe.app.ui.theme.BikerCardBg
import com.ridesafe.app.ui.theme.BikerDarkBg
import com.ridesafe.app.ui.theme.BikerSurfaceElevated
import com.ridesafe.app.ui.theme.StatusRed
import com.ridesafe.app.ui.theme.TextMuted
import com.ridesafe.app.ui.theme.TextPrimary
import com.ridesafe.app.ui.theme.TextSecondary
import com.ridesafe.app.util.LocationUtils

/**
 * Creates a custom map pin with the rider's status color and status emoji.
 */
private fun createStatusMarkerBitmap(status: RiderStatus): BitmapDescriptor {
    val width = 120
    val height = 145
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1. Outer circle matching status color
    paint.color = status.color.toArgb()
    paint.style = Paint.Style.FILL
    canvas.drawCircle(width / 2f, 55f, 50f, paint)

    // 2. Inner white circular badge
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(width / 2f, 55f, 40f, paint)

    // 3. Status Emoji in center
    paint.textSize = 46f
    paint.textAlign = Paint.Align.CENTER
    val emojiY = 55f - ((paint.descent() + paint.ascent()) / 2f)
    canvas.drawText(status.emoji, width / 2f, emojiY, paint)

    // 4. Pin triangle pointer at the bottom
    val path = Path()
    path.moveTo(width / 2f - 16f, 96f)
    path.lineTo(width / 2f + 16f, 96f)
    path.lineTo(width / 2f, 136f)
    path.close()
    paint.color = status.color.toArgb()
    canvas.drawPath(path, paint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/**
 * LiveMapScreen is the main in-ride dashboard.
 * Shows all riders on the Google Map in real time, current stop statuses,
 * and quick-access controls for group communication.
 */
@Composable
fun LiveMapScreen(
    viewModel: MapViewModel,
    onLeaveRide: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    val markerIconCache = remember { mutableMapOf<RiderStatus, BitmapDescriptor>() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val defaultPosition = LatLng(37.7749, -122.4194) // Default fallback
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 14f)
    }

    var hasCenteredInitialLocation by remember { mutableStateOf(false) }

    // Center camera immediately on the device's real GPS position
    LaunchedEffect(Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && !hasCenteredInitialLocation) {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15.5f)
                    )
                    hasCenteredInitialLocation = true
                }
            }
        } catch (e: SecurityException) {
        }
    }

    // Also update camera when current rider GPS coordinates arrive from Firebase
    val currentRider = uiState.riders.find { it.isCurrentUser }
    LaunchedEffect(currentRider?.rider?.lat, currentRider?.rider?.lng) {
        val lat = currentRider?.rider?.lat ?: 0.0
        val lng = currentRider?.rider?.lng ?: 0.0
        if (!hasCenteredInitialLocation && lat != 0.0 && lng != 0.0) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15.5f)
            )
            hasCenteredInitialLocation = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BikerDarkBg)) {
        // 1. Interactive Google Map with live markers
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = true
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = true,
                myLocationButtonEnabled = false
            )
        ) {
            // Render a custom status pin for every rider in the group
            uiState.riders.forEach { riderItem ->
                val rider = riderItem.rider
                if (rider.lat != 0.0 && rider.lng != 0.0) {
                    val position = LatLng(rider.lat, rider.lng)
                    val status = rider.riderStatus

                    val titleText = if (riderItem.isCurrentUser) {
                        "${rider.name} (You) ${status.emoji}"
                    } else {
                        "${rider.name} ${status.emoji}"
                    }

                    val snippetText = if (riderItem.isCurrentUser) {
                        "Status: ${status.displayName}"
                    } else {
                        "Status: ${status.displayName} • ${riderItem.formattedDistance} • ${LocationUtils.formatTimeAgo(rider.lastUpdated)}"
                    }

                    val markerIcon = markerIconCache.getOrPut(status) {
                        createStatusMarkerBitmap(status)
                    }

                    Marker(
                        state = MarkerState(position = position),
                        title = titleText,
                        snippet = snippetText,
                        icon = markerIcon,
                        onClick = {
                            viewModel.selectRider(riderItem)
                            false // Returns false to show default info window
                        }
                    )
                }
            }
        }

        // 2. Top Header Bar: Ride Code + Copy Button + Leave Button
        TopRideBar(
            rideCode = uiState.rideCode,
            riderCount = uiState.riders.size,
            onCopyCode = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("BhaijiRide Code", uiState.rideCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Ride code copied to clipboard!", Toast.LENGTH_SHORT).show()
            },
            onLeaveClick = {
                viewModel.leaveRide(onLeaveComplete = onLeaveRide)
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(16.dp)
        )

        // 3. Bottom Control Dock: Stop Status Button + Rider List + Recenter
        BottomControlDock(
            myStatus = uiState.myStatus,
            riderCount = uiState.riders.size,
            onStatusClick = { viewModel.openStatusPicker() },
            onRiderListClick = { viewModel.openRiderList() },
            onRecenterClick = {
                val lat = currentRider?.rider?.lat ?: 0.0
                val lng = currentRider?.rider?.lng ?: 0.0
                if (lat != 0.0 && lng != 0.0) {
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16f)
                        )
                    }
                } else {
                    try {
                        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                            if (loc != null) {
                                coroutineScope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16f)
                                    )
                                }
                            } else {
                                Toast.makeText(context, "Acquiring GPS location...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: SecurityException) {
                        Toast.makeText(context, "Location permission needed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )

        // 5. Stop Status Picker Modal
        if (uiState.isStatusPickerOpen) {
            StopStatusDialog(
                currentStatus = uiState.myStatus,
                onStatusSelected = { newStatus ->
                    viewModel.setRiderStatus(newStatus)
                },
                onDismiss = { viewModel.closeStatusPicker() }
            )
        }

        // 6. Rider List Bottom Sheet
        if (uiState.isRiderListOpen) {
            RiderListBottomSheet(
                riders = uiState.riders,
                onRiderClick = { riderItem ->
                    viewModel.closeRiderList()
                    val lat = riderItem.rider.lat
                    val lng = riderItem.rider.lng
                    if (lat != 0.0 && lng != 0.0) {
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16f)
                            )
                        }
                    }
                },
                onDismiss = { viewModel.closeRiderList() }
            )
        }
    }
}

@Composable
private fun TopRideBar(
    rideCode: String,
    riderCount: Int,
    onCopyCode: () -> Unit,
    onLeaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ride Code Card
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(BikerCardBg.copy(alpha = 0.92f))
                .border(1.dp, BikerBorder, RoundedCornerShape(16.dp))
                .clickable(onClick = onCopyCode)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RIDE CODE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Text(
                    text = rideCode,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy code",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }

        // Right actions: Rider count badge & Leave button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Active riders badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(BikerCardBg.copy(alpha = 0.92f))
                    .border(1.dp, BikerBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$riderCount",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
            }

            // Leave Ride button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BikerCardBg.copy(alpha = 0.92f))
                    .border(1.dp, StatusRed.copy(alpha = 0.5f), CircleShape)
                    .clickable(onClick = onLeaveClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Leave Ride",
                    tint = StatusRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun BottomControlDock(
    myStatus: RiderStatus,
    riderCount: Int,
    onStatusClick: () -> Unit,
    onRiderListClick: () -> Unit,
    onRecenterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Status Button (tap to change status)
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(BikerCardBg.copy(alpha = 0.95f))
                .border(
                    width = 2.dp,
                    color = myStatus.color.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(18.dp)
                )
                .clickable(onClick = onStatusClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(myStatus.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = myStatus.emoji, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "MY STATUS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Text(
                    text = myStatus.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = myStatus.color
                )
            }
        }

        // Rider List toggle button
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(BikerCardBg.copy(alpha = 0.95f))
                .border(1.dp, BikerBorder, RoundedCornerShape(18.dp))
                .clickable(onClick = onRiderListClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = "Riders List",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Recenter button
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(BikerCardBg.copy(alpha = 0.95f))
                .border(1.dp, BikerBorder, RoundedCornerShape(18.dp))
                .clickable(onClick = onRecenterClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Recenter",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101216)
@Composable
fun TopRideBarPreview() {
    RideSafeTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            TopRideBar(
                rideCode = "MOTO84",
                riderCount = 5,
                onCopyCode = {},
                onLeaveClick = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101216)
@Composable
fun BottomControlDockPreview() {
    RideSafeTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            BottomControlDock(
                myStatus = RiderStatus.RIDING,
                riderCount = 4,
                onStatusClick = {},
                onRiderListClick = {},
                onRecenterClick = {}
            )
        }
    }
}
