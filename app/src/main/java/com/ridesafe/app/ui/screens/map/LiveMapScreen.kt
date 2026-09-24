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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
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
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Navigation
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
import androidx.compose.material.icons.automirrored.filled.AltRoute
import com.ridesafe.app.data.model.TripInfo
import com.ridesafe.app.ui.theme.StatusBlue
import com.ridesafe.app.ui.theme.StatusGreen
import com.ridesafe.app.util.PolylineUtils
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Polyline as OsmPolyline

/**
 * Creates a custom map pin bitmap with the rider's name and status emoji.
 * Displays e.g. "🏍️ Rahul (You)" or "⛽ Sahil" inside a sleek rounded pill
 * with an inverted pointer triangle pointing to the GPS coordinate.
 */
private fun createRiderMarkerBitmap(name: String, status: RiderStatus, isCurrentUser: Boolean): Bitmap {
    val displayName = if (isCurrentUser) {
        "${name.trim().ifEmpty { "You" }} (You)"
    } else {
        name.trim().ifEmpty { "Rider" }
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 38f
        textAlign = Paint.Align.LEFT
    }

    val textWidth = textPaint.measureText(displayName)
    val horizontalPadding = 26f
    val emojiWidth = 46f
    val spacing = 12f
    val contentWidth = emojiWidth + spacing + textWidth

    val pillWidth = (contentWidth + horizontalPadding * 2f).coerceAtLeast(130f)
    val pillHeight = 74f
    val pointerHeight = 22f
    val totalHeight = pillHeight + pointerHeight

    val bitmap = Bitmap.createBitmap(pillWidth.toInt(), totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#171A21")
        style = Paint.Style.FILL
    }

    val strokeColor = if (isCurrentUser) {
        android.graphics.Color.parseColor("#FFC107")
    } else {
        status.color.toArgb()
    }

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeWidth = if (isCurrentUser) 5f else 3.5f
    }

    val fillAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.FILL
    }

    // Draw rounded badge rectangle
    val rect = RectF(4f, 4f, pillWidth - 4f, pillHeight - 4f)
    canvas.drawRoundRect(rect, 36f, 36f, bgPaint)
    canvas.drawRoundRect(rect, 36f, 36f, strokePaint)

    // Draw pointer pin triangle at bottom center
    val centerX = pillWidth / 2f
    val pointerPath = Path().apply {
        moveTo(centerX - 14f, pillHeight - 4f)
        lineTo(centerX + 14f, pillHeight - 4f)
        lineTo(centerX, totalHeight - 2f)
        close()
    }
    canvas.drawPath(pointerPath, fillAccentPaint)

    // Draw status emoji
    val startX = (pillWidth - contentWidth) / 2f
    val emojiBaseline = pillHeight / 2f - ((emojiPaint.descent() + emojiPaint.ascent()) / 2f)
    canvas.drawText(status.emoji, startX, emojiBaseline, emojiPaint)

    // Draw name text
    val textStartX = startX + emojiWidth + spacing
    val textBaseline = pillHeight / 2f - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(displayName, textStartX, textBaseline, textPaint)

    return bitmap
}

/**
 * Creates a distinctive custom pin for Start and Destination markers on the map,
 * with bright border accents (green for Start, coral red for Destination)
 * and label text displaying the location name.
 */
private fun createTripMarkerBitmap(name: String, isDestination: Boolean): Bitmap {
    val prefix = if (isDestination) "🏁" else "🚩"
    val label = if (isDestination) "DESTINATION" else "START"
    val placeName = name.trim().ifEmpty { if (isDestination) "Destination" else "Start" }
    val displayName = "$prefix $placeName"

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        color = if (isDestination) android.graphics.Color.parseColor("#FFC107") else android.graphics.Color.parseColor("#69F0AE")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val textWidth = textPaint.measureText(displayName)
    val labelWidth = labelPaint.measureText(label)
    val contentWidth = maxOf(textWidth, labelWidth)
    val horizontalPadding = 26f

    val pillWidth = (contentWidth + horizontalPadding * 2f).coerceAtLeast(140f)
    val pillHeight = 78f
    val pointerHeight = 20f
    val totalHeight = pillHeight + pointerHeight

    val bitmap = Bitmap.createBitmap(pillWidth.toInt(), totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgColor = android.graphics.Color.parseColor("#171A21")
    val strokeColor = if (isDestination) {
        android.graphics.Color.parseColor("#FF5252") // Coral Red
    } else {
        android.graphics.Color.parseColor("#00E676") // Emerald Green
    }

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    val fillAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.FILL
    }

    // Draw pill badge
    val rect = RectF(4f, 4f, pillWidth - 4f, pillHeight - 4f)
    canvas.drawRoundRect(rect, 36f, 36f, bgPaint)
    canvas.drawRoundRect(rect, 36f, 36f, strokePaint)

    // Draw bottom pointer pin
    val centerX = pillWidth / 2f
    val pointerPath = Path().apply {
        moveTo(centerX - 12f, pillHeight - 4f)
        lineTo(centerX + 12f, pillHeight - 4f)
        lineTo(centerX, totalHeight - 2f)
        close()
    }
    canvas.drawPath(pointerPath, fillAccentPaint)

    // Draw small uppercase label
    val labelX = (pillWidth - labelWidth) / 2f
    canvas.drawText(label, labelX, 28f, labelPaint)

    // Draw display name
    val textX = (pillWidth - textWidth) / 2f
    val textY = 62f
    canvas.drawText(displayName, textX, textY, textPaint)

    return bitmap
}

/**
 * Zooms and pans the camera to tightly enclose all active riders AND the planned route points.
 */
private fun zoomToFitContent(
    mapView: MapView?,
    riders: List<RiderWithDistance>,
    routePoints: List<GeoPoint>,
    tripInfo: TripInfo?
) {
    val mv = mapView ?: return
    val additionalPoints = mutableListOf<GeoPoint>()
    riders.forEach { r ->
        if (r.rider.lat != 0.0 && r.rider.lng != 0.0) {
            additionalPoints.add(GeoPoint(r.rider.lat, r.rider.lng))
        }
    }
    if (tripInfo != null && tripInfo.isTripPlanned) {
        if (tripInfo.startLat != 0.0 && tripInfo.startLng != 0.0) {
            additionalPoints.add(GeoPoint(tripInfo.startLat, tripInfo.startLng))
        }
        if (tripInfo.destLat != 0.0 && tripInfo.destLng != 0.0) {
            additionalPoints.add(GeoPoint(tripInfo.destLat, tripInfo.destLng))
        }
    }

    val boundingBox = PolylineUtils.calculateRouteBoundingBox(routePoints, additionalPoints)
    if (boundingBox != null) {
        mv.zoomToBoundingBox(boundingBox, true, 130)
    }
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

    // Cache marker bitmaps by unique rider identity so we don't recreate them every recomposition
    val markerBitmapCache = remember { mutableMapOf<String, Bitmap>() }
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

    // Center camera immediately on the device's real GPS position or route
    LaunchedEffect(Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                android.util.Log.d("RideSafeDebug", "[MapRender] LiveMapScreen lastLocation: loc=$loc, hasCentered=$hasCenteredInitialLocation")
                if (loc != null && !hasCenteredInitialLocation) {
                    if (uiState.routePoints.isNotEmpty()) {
                        zoomToFitContent(mapView, uiState.riders, uiState.routePoints, uiState.tripInfo)
                        hasCenteredInitialLocation = true
                    } else {
                        mapView?.controller?.let { controller ->
                            controller.setZoom(15.5)
                            controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                        }
                        hasCenteredInitialLocation = true
                    }
                    android.util.Log.d("RideSafeDebug", "[MapRender] Camera centered on real GPS position: ${loc.latitude}, ${loc.longitude}")
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.e("RideSafeDebug", "[MapRender] SecurityException on lastLocation: ${e.message}", e)
        }
    }

    // Automatically zoom to fit route when route points load
    LaunchedEffect(uiState.routePoints.size) {
        if (uiState.routePoints.isNotEmpty()) {
            zoomToFitContent(mapView, uiState.riders, uiState.routePoints, uiState.tripInfo)
            hasCenteredInitialLocation = true
        }
    }

    // Also update camera when current rider GPS coordinates arrive from Firebase or local sensor
    val currentRider = uiState.riders.find { it.isCurrentUser }
    LaunchedEffect(currentRider?.rider?.lat, currentRider?.rider?.lng) {
        val lat = currentRider?.rider?.lat ?: 0.0
        val lng = currentRider?.rider?.lng ?: 0.0
        android.util.Log.d("RideSafeDebug", "[MapRender] LaunchedEffect currentRider coords: lat=$lat, lng=$lng, hasCentered=$hasCenteredInitialLocation")
        if (!hasCenteredInitialLocation && lat != 0.0 && lng != 0.0) {
            if (uiState.routePoints.isNotEmpty()) {
                zoomToFitContent(mapView, uiState.riders, uiState.routePoints, uiState.tripInfo)
            } else {
                mapView?.controller?.let { controller ->
                    controller.setZoom(16.0)
                    controller.animateTo(GeoPoint(lat, lng), 16.0, 800L)
                }
            }
            hasCenteredInitialLocation = true
            android.util.Log.d("RideSafeDebug", "[MapRender] Camera animated to current rider position: ($lat, $lng)")
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
        // Extract rider & trip data so Compose tracks them as dependencies for recomposition.
        val riders = uiState.riders
        val tripInfo = uiState.tripInfo
        val routePoints = uiState.routePoints

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
                    // Set initial zoom and center (will be overridden by GPS / route)
                    controller.setZoom(14.0)
                    controller.setCenter(GeoPoint(28.6139, 77.2090))

                    // Start the map's tile loading
                    onResume()

                    // Store reference for use in Compose callbacks
                    mapView = this
                }
            },
            update = { mv ->
                // Clear all existing overlays and re-add from current state.
                mv.overlays.clear()
                android.util.Log.d("RideSafeDebug", "[MapRender] Updating map overlays for ${riders.size} riders, routePoints=${routePoints.size}:")

                // 1. Draw planned route polyline if present (drawn UNDER markers)
                if (routePoints.isNotEmpty()) {
                    val routePolyline = OsmPolyline(mv).apply {
                        setPoints(routePoints)
                        outlinePaint.apply {
                            color = android.graphics.Color.parseColor("#00B0FF") // Electric route blue
                            strokeWidth = 16f // 6-8dp width
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                        }
                        title = if (tripInfo != null && tripInfo.isTripPlanned) {
                            "Route: ${tripInfo.formattedDistance} (${tripInfo.formattedDuration})"
                        } else {
                            "Planned Route"
                        }
                    }
                    mv.overlays.add(routePolyline)
                }

                // 2. Add Start point marker if trip planned
                if (tripInfo != null && tripInfo.isTripPlanned && tripInfo.startLat != 0.0 && tripInfo.startLng != 0.0) {
                    val startPos = GeoPoint(tripInfo.startLat, tripInfo.startLng)
                    val startKey = "start_${tripInfo.startName}"
                    val startBitmap = markerBitmapCache.getOrPut(startKey) {
                        createTripMarkerBitmap(tripInfo.startName, isDestination = false)
                    }
                    val startMarker = OsmMarker(mv).apply {
                        position = startPos
                        title = "Start: ${tripInfo.startName}"
                        snippet = "Convoy Departure Point"
                        icon = BitmapDrawable(mv.context.resources, startBitmap)
                        setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { marker, _ ->
                            marker.showInfoWindow()
                            true
                        }
                    }
                    mv.overlays.add(startMarker)
                }

                // 3. Add Destination point marker if trip planned
                if (tripInfo != null && tripInfo.isTripPlanned && tripInfo.destLat != 0.0 && tripInfo.destLng != 0.0) {
                    val destPos = GeoPoint(tripInfo.destLat, tripInfo.destLng)
                    val destKey = "dest_${tripInfo.destName}"
                    val destBitmap = markerBitmapCache.getOrPut(destKey) {
                        createTripMarkerBitmap(tripInfo.destName, isDestination = true)
                    }
                    val destMarker = OsmMarker(mv).apply {
                        position = destPos
                        title = "Destination: ${tripInfo.destName}"
                        snippet = if (tripInfo.formattedDistance.isNotEmpty()) {
                            "Total Distance: ${tripInfo.formattedDistance} (${tripInfo.formattedDuration})"
                        } else {
                            "Convoy Destination"
                        }
                        icon = BitmapDrawable(mv.context.resources, destBitmap)
                        setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { marker, _ ->
                            marker.showInfoWindow()
                            true
                        }
                    }
                    mv.overlays.add(destMarker)
                }

                // 4. Add Rider markers (on top)
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

                        val cacheKey = "${rider.id}_${rider.name}_${status.name}_${riderItem.isCurrentUser}"
                        val markerBitmap = markerBitmapCache.getOrPut(cacheKey) {
                            createRiderMarkerBitmap(rider.name, status, riderItem.isCurrentUser)
                        }

                        val marker = OsmMarker(mv).apply {
                            this.position = position
                            this.title = titleText
                            this.snippet = snippetText
                            this.icon = BitmapDrawable(mv.context.resources, markerBitmap)
                            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                            setOnMarkerClickListener { clickedMarker, _ ->
                                viewModel.selectRider(riderItem)
                                clickedMarker.showInfoWindow()
                                true
                            }
                        }
                        mv.overlays.add(marker)
                    }
                }

                // Trigger a redraw so all new overlays appear immediately
                mv.invalidate()
            }
        )

        // 2. Top Header Panels: Ride Code + Trip Overview Banner + Emergency Alert
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                }
            )

            // 2.2 Planned Route Overview Pill (appears when trip route is present)
            if (tripInfo != null && tripInfo.isTripPlanned) {
                TripOverviewBanner(
                    tripInfo = tripInfo,
                    onFitRouteClick = {
                        zoomToFitContent(mapView, uiState.riders, uiState.routePoints, tripInfo)
                    }
                )
            }

            // 2.5 Emergency Alert Banner: Displays when any other convoy rider sets status to EMERGENCY
            val emergencyRiders = uiState.riders.filter { !it.isCurrentUser && it.rider.riderStatus == RiderStatus.EMERGENCY }
            if (emergencyRiders.isNotEmpty()) {
                EmergencyAlertBanner(
                    emergencyRiders = emergencyRiders,
                    onLocateRider = { emergencyRider ->
                        val lat = emergencyRider.rider.lat
                        val lng = emergencyRider.rider.lng
                        if (lat != 0.0 && lng != 0.0) {
                            mapView?.controller?.animateTo(GeoPoint(lat, lng), 17.0, 1000L)
                        }
                    }
                )
            }
        }

        // 3. Proximity Radar Box: Shows relative distance and ahead/behind status for all riders
        RiderProximityBox(
            riders = uiState.riders,
            onRiderClick = { riderItem ->
                val lat = riderItem.rider.lat
                val lng = riderItem.rider.lng
                if (lat != 0.0 && lng != 0.0) {
                    mapView?.controller?.animateTo(GeoPoint(lat, lng), 16.0, 1000L)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 86.dp)
        )

        // 4. Bottom Control Dock: Stop Status Button + Rider List + Fit Route + Recenter
        BottomControlDock(
            myStatus = uiState.myStatus,
            riderCount = uiState.riders.size,
            hasPlannedRoute = tripInfo != null && tripInfo.isTripPlanned,
            onStatusClick = { viewModel.openStatusPicker() },
            onRiderListClick = { viewModel.openRiderList() },
            onFitRouteClick = {
                zoomToFitContent(mapView, uiState.riders, uiState.routePoints, tripInfo)
            },
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
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Leave Ride",
                    tint = StatusRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Emergency Alert Banner showing which rider needs immediate help and their distance,
 * with a quick LOCATE button to animate the map camera directly to them.
 */
@Composable
private fun EmergencyAlertBanner(
    emergencyRiders: List<RiderWithDistance>,
    onLocateRider: (RiderWithDistance) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryEmergency = emergencyRiders.firstOrNull() ?: return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StatusRed.copy(alpha = 0.22f))
            .border(2.dp, StatusRed, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(StatusRed.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🚨", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "EMERGENCY: ${primaryEmergency.rider.name.ifEmpty { "Rider" }}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = StatusRed
                    )
                    Text(
                        text = "Needs help! ${primaryEmergency.formattedDistance} away",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }

            Button(
                onClick = { onLocateRider(primaryEmergency) },
                colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "LOCATE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun BottomControlDock(
    myStatus: RiderStatus,
    riderCount: Int,
    hasPlannedRoute: Boolean = false,
    onStatusClick: () -> Unit,
    onRiderListClick: () -> Unit,
    onFitRouteClick: () -> Unit = {},
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

        // Fit Route & Convoy button (visible when a route is planned)
        if (hasPlannedRoute) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(BikerCardBg.copy(alpha = 0.95f))
                    .border(1.dp, StatusBlue.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                    .clickable(onClick = onFitRouteClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.AltRoute,
                    contentDescription = "Fit Route & Convoy",
                    tint = StatusBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
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

/**
 * Floating banner displaying planned route summary (start -> destination, distance & duration).
 * Tapping it animates camera to fit both the entire route and convoy riders.
 */
@Composable
private fun TripOverviewBanner(
    tripInfo: TripInfo,
    onFitRouteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BikerCardBg.copy(alpha = 0.94f))
            .border(1.dp, StatusBlue.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .clickable(onClick = onFitRouteClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(StatusBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.AltRoute,
                        contentDescription = null,
                        tint = StatusBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${tripInfo.startName.ifEmpty { "Start" }} ➔ ${tripInfo.destName.ifEmpty { "Destination" }}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${tripInfo.formattedDistance} · ${tripInfo.formattedDuration} · Tap to fit view",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusBlue,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(BikerSurfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "FIT ROUTE",
                    color = StatusBlue,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * RiderProximityBox displays a floating dashboard showing the relative position and distance
 * of all fellow group riders (e.g. "Rahul is 10 KM ahead of You", "Sahil is 5 KM behind you").
 */
@Composable
fun RiderProximityBox(
    riders: List<RiderWithDistance>,
    onRiderClick: (RiderWithDistance) -> Unit,
    modifier: Modifier = Modifier
) {
    val fellowRiders = riders.filter { !it.isCurrentUser }
    var isExpanded by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BikerCardBg.copy(alpha = 0.94f))
            .border(1.dp, BikerBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar with Expand / Collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RIDER RADAR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${fellowRiders.size} ${if (fellowRiders.size == 1) "RIDER" else "RIDERS"}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (fellowRiders.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🏍️",
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Waiting for other riders to join...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 160.dp)
                        ) {
                            fellowRiders.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BikerSurfaceElevated)
                                        .clickable { onRiderClick(item) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Direction Badge / Arrow
                                    when (item.isAhead) {
                                        true -> {
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(androidx.compose.ui.graphics.Color(0xFF1B5E20)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowUpward,
                                                    contentDescription = "Ahead",
                                                    tint = androidx.compose.ui.graphics.Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                        false -> {
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(androidx.compose.ui.graphics.Color(0xFFE65100)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDownward,
                                                    contentDescription = "Behind",
                                                    tint = androidx.compose.ui.graphics.Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                        null -> {
                                            Text(
                                                text = item.rider.riderStatus.emoji,
                                                fontSize = 18.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    // Display the exact requested string e.g. "Rahul is 10 KM ahead of You"
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.relativePositionText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            fontSize = 13.sp
                                        )
                                        if (item.rider.speed > 0f) {
                                            Text(
                                                text = "Speed: ${LocationUtils.formatSpeed(item.rider.speed)} • ${item.rider.riderStatus.displayName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = item.rider.riderStatus.color,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    // Status Emoji
                                    Text(
                                        text = item.rider.riderStatus.emoji,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
