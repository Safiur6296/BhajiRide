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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.location.LocationServices
import com.ridesafe.app.ui.theme.RideSafeTheme
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker as OsmMarker

/**
 * Creates a custom map pin bitmap with the rider's status color and status emoji.
 * Returns a raw Bitmap (wrapped in BitmapDrawable at the usage site for osmdroid).
 */
private fun createStatusMarkerBitmap(status: RiderStatus): Bitmap {
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

    return bitmap
}

/**
 * LiveMapScreen is the main in-ride dashboard.
 * Shows all riders on an OpenStreetMap (osmdroid) map in real time, current stop statuses,
 * and quick-access controls for group communication.
 *
 * The map uses osmdroid's MapView wrapped in Compose's AndroidView interop.
 * osmdroid loads free OpenStreetMap tiles — no API key or billing account needed.
 */
@Composable
fun LiveMapScreen(
    viewModel: MapViewModel,
    onLeaveRide: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    // Cache marker bitmaps by status so we don't recreate them every recomposition
    val markerBitmapCache = remember { mutableMapOf<RiderStatus, Bitmap>() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Hold a reference to the osmdroid MapView so we can control it from Compose callbacks
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var hasCenteredInitialLocation by remember { mutableStateOf(false) }

    // Configure osmdroid ONCE before the MapView is created.
    // This sets the User-Agent (required by OpenStreetMap tile servers) and
    // tile cache paths (using app-internal storage to avoid needing WRITE_EXTERNAL_STORAGE).
    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            // Store tiles in app-private directories — works on all Android versions
            // without needing WRITE_EXTERNAL_STORAGE permission
            osmdroidBasePath = context.getDir("osmdroid", Context.MODE_PRIVATE)
            osmdroidTileCache = context.getDir("osmdroid_tiles", Context.MODE_PRIVATE)
        }
    }

    // Center camera immediately on the device's real GPS position
    LaunchedEffect(Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && !hasCenteredInitialLocation) {
                    mapView?.controller?.let { controller ->
                        controller.setZoom(15.5)
                        controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                    }
                    hasCenteredInitialLocation = true
                }
            }
        } catch (e: SecurityException) {
            // Location permission not yet granted — camera will center when Firebase data arrives
        }
    }

    // Also update camera when current rider GPS coordinates arrive from Firebase
    val currentRider = uiState.riders.find { it.isCurrentUser }
    LaunchedEffect(currentRider?.rider?.lat, currentRider?.rider?.lng) {
        val lat = currentRider?.rider?.lat ?: 0.0
        val lng = currentRider?.rider?.lng ?: 0.0
        if (!hasCenteredInitialLocation && lat != 0.0 && lng != 0.0) {
            mapView?.controller?.let { controller ->
                controller.animateTo(GeoPoint(lat, lng), 15.5, 1000L)
            }
            hasCenteredInitialLocation = true
        }
    }

    // Clean up osmdroid MapView lifecycle when this composable leaves the composition
    DisposableEffect(Unit) {
        onDispose {
            mapView?.onPause()
            mapView?.onDetach()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BikerDarkBg)) {
        // Extract rider data so Compose tracks it as a dependency for recomposition.
        // Reading uiState.riders OUTSIDE the AndroidView lambda ensures Compose knows
        // to re-invoke update{} whenever the riders list changes (status, position, etc.)
        val riders = uiState.riders

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    // Use standard OpenStreetMap tiles (Mapnik style)
                    setTileSource(TileSourceFactory.MAPNIK)
                    // Enable pinch-to-zoom and two-finger rotate
                    setMultiTouchControls(true)
                    // Disable the default +/- zoom buttons (we have our own controls)
                    zoomController.setVisibility(
                        org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                    )
                    // Set initial zoom and center (San Francisco fallback, will be overridden by GPS)
                    controller.setZoom(14.0)
                    controller.setCenter(GeoPoint(37.7749, -122.4194))

                    // Start the map's tile loading
                    onResume()

                    // Store reference for use in Compose callbacks (recenter, etc.)
                    mapView = this
                }
            },
            update = { mv ->
                // Clear all existing marker overlays and re-add from current state.
                // This runs on every recomposition when riders list changes,
                // keeping markers perfectly in sync with Firebase data.
                mv.overlays.clear()

                riders.forEach { riderItem ->
                    val rider = riderItem.rider
                    if (rider.lat != 0.0 && rider.lng != 0.0) {
                        val position = GeoPoint(rider.lat, rider.lng)
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

                        // Get or create the custom pin bitmap for this status
                        val markerBitmap = markerBitmapCache.getOrPut(status) {
                            createStatusMarkerBitmap(status)
                        }

                        // Create an osmdroid Marker and configure it
                        val marker = OsmMarker(mv).apply {
                            this.position = position
                            this.title = titleText
                            this.snippet = snippetText
                            // Wrap the Bitmap in a BitmapDrawable (osmdroid's equivalent
                            // of Google Maps' BitmapDescriptor)
                            this.icon = BitmapDrawable(mv.context.resources, markerBitmap)
                            // Anchor at bottom-center of the pin image so the pointer
                            // tip sits exactly on the rider's GPS coordinates
                            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                            // Handle marker tap — select rider and show info window
                            setOnMarkerClickListener { clickedMarker, _ ->
                                viewModel.selectRider(riderItem)
                                clickedMarker.showInfoWindow()
                                true
                            }
                        }
                        mv.overlays.add(marker)
                    }
                }

                // Trigger a redraw so the new markers appear immediately
                mv.invalidate()
            }
        )

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
                    mapView?.controller?.animateTo(GeoPoint(lat, lng), 16.0, 1000L)
                } else {
                    try {
                        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                            if (loc != null) {
                                mapView?.controller?.animateTo(
                                    GeoPoint(loc.latitude, loc.longitude), 16.0, 1000L
                                )
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
                        mapView?.controller?.animateTo(GeoPoint(lat, lng), 16.0, 1000L)
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
