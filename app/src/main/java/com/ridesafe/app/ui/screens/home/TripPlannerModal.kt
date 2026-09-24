package com.ridesafe.app.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesafe.app.data.model.PlaceSuggestion
import com.ridesafe.app.data.model.RouteResult
import com.ridesafe.app.ui.theme.BikerAmber
import com.ridesafe.app.ui.theme.BikerBorder
import com.ridesafe.app.ui.theme.BikerCardBg
import com.ridesafe.app.ui.theme.BikerDarkBg
import com.ridesafe.app.ui.theme.BikerSurfaceElevated
import com.ridesafe.app.ui.theme.StatusBlue
import com.ridesafe.app.ui.theme.StatusGreen
import com.ridesafe.app.ui.theme.StatusRed
import com.ridesafe.app.ui.theme.TextMuted
import com.ridesafe.app.ui.theme.TextPrimary
import com.ridesafe.app.ui.theme.TextSecondary
import com.ridesafe.app.util.PolylineUtils

/**
 * TripPlannerModal provides an Uber-style route selection experience:
 * - Two search inputs: Start Location (auto-prefilled with current GPS) and Destination.
 * - Real-time type-ahead suggestions powered by Komoot's free Photon API (OSM data).
 * - Instant route calculation with distance & duration preview via OSRM demo routing engine.
 * - Optional flow: Creators can either start with a planned route or skip to ride untracked.
 */
@Composable
fun TripPlannerModal(
    startQuery: String,
    destQuery: String,
    selectedStartPlace: PlaceSuggestion?,
    selectedDestPlace: PlaceSuggestion?,
    startSuggestions: List<PlaceSuggestion>,
    destSuggestions: List<PlaceSuggestion>,
    isLoadingStartSuggestions: Boolean,
    isLoadingDestSuggestions: Boolean,
    isDetectingStartLocation: Boolean,
    isCalculatingRoute: Boolean,
    calculatedRoute: RouteResult?,
    routeError: String?,
    isCreatingRide: Boolean,
    onStartQueryChange: (String) -> Unit,
    onDestQueryChange: (String) -> Unit,
    onSelectStartPlace: (PlaceSuggestion) -> Unit,
    onSelectDestPlace: (PlaceSuggestion) -> Unit,
    onUseCurrentLocationForStart: () -> Unit,
    onClearStartPlace: () -> Unit,
    onClearDestPlace: () -> Unit,
    onRetryRouteCalculation: () -> Unit,
    onCreateRideWithRoute: () -> Unit,
    onSkipAndCreateRide: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var isEditingStart by remember { mutableStateOf(false) }
    var isEditingDest by remember { mutableStateOf(false) }

    // Hardware/gesture back press intercepts to dismiss full-screen trip planner
    BackHandler(enabled = true) {
        onDismiss()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(BikerDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        color = BikerDarkBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        onDismiss()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(BikerSurfaceElevated)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Plan Convoy Route",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "OpenStreetMap • Photon & OSRM (100% Free)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

                    // Location Input Card (Uber Style)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = BikerCardBg),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BikerBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Start Location Field
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Green Dot Indicator
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(StatusGreen)
                                )
                                Spacer(modifier = Modifier.width(12.dp))

                                OutlinedTextField(
                                    value = startQuery,
                                    onValueChange = {
                                        isEditingStart = true
                                        isEditingDest = false
                                        onStartQueryChange(it)
                                    },
                                    placeholder = {
                                        Text(
                                            text = if (isDetectingStartLocation) "Detecting GPS location..." else "Search start location",
                                            color = TextMuted,
                                            fontSize = 14.sp
                                        )
                                    },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isDetectingStartLocation) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    color = StatusGreen,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            } else {
                                                IconButton(
                                                    onClick = {
                                                        focusManager.clearFocus()
                                                        onUseCurrentLocationForStart()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MyLocation,
                                                        contentDescription = "Current GPS",
                                                        tint = if (selectedStartPlace?.name == "Current Location") StatusGreen else TextSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            if (startQuery.isNotEmpty()) {
                                                IconButton(
                                                    onClick = onClearStartPlace,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Clear",
                                                        tint = TextMuted,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = StatusGreen,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = BikerDarkBg.copy(alpha = 0.6f),
                                        unfocusedContainerColor = BikerDarkBg.copy(alpha = 0.4f),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Vertical Connector Line
                            Row(modifier = Modifier.padding(start = 5.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(20.dp)
                                        .background(BikerBorder)
                                )
                            }

                            // Destination Location Field
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Amber Square Indicator
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(BikerAmber)
                                )
                                Spacer(modifier = Modifier.width(12.dp))

                                OutlinedTextField(
                                    value = destQuery,
                                    onValueChange = {
                                        isEditingDest = true
                                        isEditingStart = false
                                        onDestQueryChange(it)
                                    },
                                    placeholder = {
                                        Text(
                                            text = "Where is the convoy heading?",
                                            color = TextMuted,
                                            fontSize = 14.sp
                                        )
                                    },
                                    trailingIcon = {
                                        if (destQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = onClearDestPlace,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear",
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BikerAmber,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = BikerDarkBg.copy(alpha = 0.6f),
                                        unfocusedContainerColor = BikerDarkBg.copy(alpha = 0.4f),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Suggestion Lists or Route Overview
                    Box(modifier = Modifier.weight(1f)) {
                        val activeSuggestions = if (isEditingStart) startSuggestions else destSuggestions
                        val isLoadingActive = if (isEditingStart) isLoadingStartSuggestions else isLoadingDestSuggestions

                        if (activeSuggestions.isNotEmpty() || isLoadingActive) {
                            // Autocomplete Suggestions List
                            Card(
                                modifier = Modifier.fillMaxSize(),
                                colors = CardDefaults.cardColors(containerColor = BikerCardBg),
                                shape = RoundedCornerShape(18.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BikerBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isEditingStart) "START SUGGESTIONS" else "DESTINATION SUGGESTIONS",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                        if (isLoadingActive) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                color = BikerAmber,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }

                                    HorizontalDivider(
                                        color = BikerBorder.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(activeSuggestions) { suggestion ->
                                            SuggestionRowItem(
                                                suggestion = suggestion,
                                                isDestination = !isEditingStart,
                                                onClick = {
                                                    focusManager.clearFocus()
                                                    if (isEditingStart) {
                                                        isEditingStart = false
                                                        onSelectStartPlace(suggestion)
                                                    } else {
                                                        isEditingDest = false
                                                        onSelectDestPlace(suggestion)
                                                    }
                                                }
                                            )
                                            HorizontalDivider(
                                                color = BikerBorder.copy(alpha = 0.3f),
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Route Details or Welcome Helper
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Route Calculation Status Card
                                if (isCalculatingRoute) {
                                    RouteStatusCard(
                                        title = "Calculating Shortest Route",
                                        subtitle = "Querying OSRM public routing server...",
                                        icon = Icons.Default.Directions,
                                        iconTint = StatusBlue,
                                        isLoading = true
                                    )
                                } else if (calculatedRoute != null) {
                                    CalculatedRouteCard(
                                        route = calculatedRoute,
                                        startName = selectedStartPlace?.name ?: "Start",
                                        destName = selectedDestPlace?.name ?: "Destination"
                                    )
                                } else if (routeError != null) {
                                    RouteErrorCard(
                                        error = routeError,
                                        onRetry = onRetryRouteCalculation
                                    )
                                } else {
                                    // Empty state guidance
                                    EmptyPlanningStateCard()
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Bottom Action Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isRouteReady = calculatedRoute != null && selectedStartPlace != null && selectedDestPlace != null

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onCreateRideWithRoute()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BikerAmber,
                                disabledContainerColor = BikerBorder.copy(alpha = 0.5f)
                            ),
                            enabled = isRouteReady && !isCreatingRide && !isCalculatingRoute
                        ) {
                            if (isCreatingRide) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = BikerDarkBg,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Directions,
                                    contentDescription = null,
                                    tint = if (isRouteReady) BikerDarkBg else TextMuted
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isRouteReady) "Create Convoy with Route" else "Select Start & Destination",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRouteReady) BikerDarkBg else TextMuted
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = if (isRouteReady) BikerDarkBg else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Optional skip button (requirement #5: Handle "no trip planned" case)
                        TextButton(
                            onClick = {
                                focusManager.clearFocus()
                                onSkipAndCreateRide()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            enabled = !isCreatingRide
                        ) {
                            Text(
                                text = "Skip Route & Start Directly",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
}

@Composable
private fun SuggestionRowItem(
    suggestion: PlaceSuggestion,
    isDestination: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isDestination) BikerAmber.copy(alpha = 0.15f) else StatusGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                tint = if (isDestination) BikerAmber else StatusGreen,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (suggestion.locality.isNotEmpty()) {
                Text(
                    text = suggestion.locality,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CalculatedRouteCard(
    route: RouteResult,
    startName: String,
    destName: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BikerCardBg),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, StatusBlue.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(StatusBlue.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = null,
                            tint = StatusBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Optimal Route Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = StatusBlue
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusGreen.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "READY",
                        color = StatusGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Distance & Duration Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BikerDarkBg)
                        .border(1.dp, BikerBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "DISTANCE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = PolylineUtils.formatDistance(route.distanceMeters),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BikerDarkBg)
                        .border(1.dp, BikerBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "EST. DURATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = PolylineUtils.formatDuration(route.durationSeconds),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = BikerAmber
                        )
                    }
                }
            }

            if (route.summary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Via: ${route.summary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun RouteStatusCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    isLoading: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BikerCardBg),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BikerBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = iconTint,
                    strokeWidth = 2.5.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun RouteErrorCard(
    error: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BikerCardBg),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StatusRed.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "⚠️", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Routing Note",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StatusRed
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BikerAmber)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Retry Route")
            }
        }
    }
}

@Composable
private fun EmptyPlanningStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BikerCardBg.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BikerBorder.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(BikerSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NearMe,
                    contentDescription = null,
                    tint = BikerAmber,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Plan Your Convoy Destination",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Search a destination above to calculate the shortest path, estimated ride time, and draw the blue route line on your pack's live map.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 12.sp
            )
        }
    }
}
