package com.ridesafe.app.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesafe.app.data.model.PlaceSuggestion
import com.ridesafe.app.data.model.RouteResult
import com.ridesafe.app.ui.theme.RideSafeTheme
import com.ridesafe.app.util.PolylineUtils

// -------------------------------------------------------------
// Tesla / Uber-Inspired Premium Dark Amber Theme Tokens
// -------------------------------------------------------------
private val PlannerDarkBg = Color(0xFF090B0E)             // Deep charcoal / near-black
private val PlannerCardBg = Color(0xFF13171F)             // Rich charcoal card background
private val PlannerSurfaceElevated = Color(0xFF1B212C)     // Interactive elevated surface
private val PlannerBorder = Color(0xFF26303E)              // Subtle structural border

// Electric Amber-Orange Brand Accents
private val ElectricAmber = Color(0xFFFF9800)          // Pure electric amber
private val ElectricAmberOrange = Color(0xFFFF6D00)    // High-energy electric orange

// Fresh Green Accent specifically for Start Indicator & Route Ready badge
private val StartGreen = Color(0xFF10B981)             // Fresh vivid emerald green

// Gradients & Glow Brushes
private val AmberButtonGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFFFA000), // Electric Amber
        Color(0xFFFF6D00)  // Electric Amber-Orange
    )
)

private val AmberGlowBorderBrush = Brush.linearGradient(
    colors = listOf(
        ElectricAmber.copy(alpha = 0.85f),
        Color(0xFF2B3545).copy(alpha = 0.6f),
        ElectricAmber.copy(alpha = 0.40f)
    )
)

// High-contrast clean typography colors
private val PlannerTextPrimary = Color(0xFFF8FAFC)        // Crisp white / platinum
private val PlannerTextSecondary = Color(0xFF94A3B8)      // Clean cool slate
private val PlannerTextMuted = Color(0xFF64748B)          // Muted slate
private val PlannerStatusRed = Color(0xFFEF4444)          // Crisp status red

/**
 * TripPlannerModal provides a premium route selection experience:
 * - Two search inputs: Start Location (auto-prefilled with current GPS) and Destination.
 * - Real-time type-ahead suggestions powered by Komoot's free Photon API (OSM data).
 * - Instant route calculation with distance & duration preview via OSRM demo routing engine.
 * - Creators can either start with a planned route or skip to ride untracked.
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
            .background(PlannerDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        color = PlannerDarkBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 1. Back arrow + title "Plan Convoy Route" with a small muted subtitle ("OpenStreetMap · Photon & OSRM — Free")
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PlannerSurfaceElevated)
                        .border(1.dp, PlannerBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PlannerTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Plan Convoy Route",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PlannerTextPrimary,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "OpenStreetMap · Photon & OSRM — Free",
                        style = MaterialTheme.typography.bodySmall,
                        color = PlannerTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. A card containing two stacked location fields connected by a thin vertical line
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PlannerCardBg),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, PlannerBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Top field: green dot indicator, start location text, "locate me" icon, clear (x) icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Fresh green dot indicator
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(StartGreen.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StartGreen)
                            )
                        }

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
                                    color = PlannerTextMuted,
                                    fontSize = 14.sp
                                )
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isDetectingStartLocation) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = StartGreen,
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
                                                tint = if (selectedStartPlace?.name == "Current Location") StartGreen else PlannerTextSecondary,
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
                                                tint = PlannerTextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = StartGreen,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = PlannerDarkBg.copy(alpha = 0.65f),
                                unfocusedContainerColor = PlannerDarkBg.copy(alpha = 0.45f),
                                focusedTextColor = PlannerTextPrimary,
                                unfocusedTextColor = PlannerTextPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Thin vertical connector line
                    Row(
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(18.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            StartGreen.copy(alpha = 0.7f),
                                            ElectricAmber.copy(alpha = 0.7f)
                                        )
                                    )
                                )
                        )
                    }

                    // Bottom field: amber dot indicator, destination text, clear (x) icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Amber dot indicator
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(ElectricAmber.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ElectricAmber)
                            )
                        }

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
                                    color = PlannerTextMuted,
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
                                            tint = PlannerTextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricAmber,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = PlannerDarkBg.copy(alpha = 0.65f),
                                unfocusedContainerColor = PlannerDarkBg.copy(alpha = 0.45f),
                                focusedTextColor = PlannerTextPrimary,
                                unfocusedTextColor = PlannerTextPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Suggestions List or Route Overview
            Box(modifier = Modifier.weight(1f)) {
                val activeSuggestions = if (isEditingStart) startSuggestions else destSuggestions
                val isLoadingActive = if (isEditingStart) isLoadingStartSuggestions else isLoadingDestSuggestions

                if (activeSuggestions.isNotEmpty() || isLoadingActive) {
                    // Autocomplete Suggestions Card
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        colors = CardDefaults.cardColors(containerColor = PlannerCardBg),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, PlannerBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
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
                                    color = PlannerTextMuted,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                )
                                if (isLoadingActive) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = if (isEditingStart) StartGreen else ElectricAmber,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = PlannerBorder.copy(alpha = 0.6f),
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
                                        color = PlannerBorder.copy(alpha = 0.35f),
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Route Details or Empty Guidance State
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (isCalculatingRoute) {
                            RouteStatusCard(
                                title = "Calculating Shortest Route",
                                subtitle = "Querying OSRM public routing server...",
                                icon = Icons.AutoMirrored.Filled.AltRoute,
                                iconTint = ElectricAmber,
                                isLoading = true
                            )
                        } else if (calculatedRoute != null) {
                            // 3. Highlighted card with amber-outlined border, route icon, "Optimal Route Found", green "READY" pill, and 2 stat boxes
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
                            EmptyPlanningStateCard()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Actions: 4. Large primary amber gradient button + 5. Subtle muted text link
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isRouteReady = calculatedRoute != null && selectedStartPlace != null && selectedDestPlace != null
                val isButtonEnabled = isRouteReady && !isCreatingRide && !isCalculatingRoute

                // 4. Large primary amber gradient button: "Create Convoy with Route" with forward arrow icon
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onCreateRideWithRoute()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isButtonEnabled) AmberButtonGradient
                            else Brush.horizontalGradient(
                                listOf(
                                    ElectricAmber.copy(alpha = 0.35f),
                                    ElectricAmber.copy(alpha = 0.25f)
                                )
                            )
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(),
                    enabled = isButtonEnabled
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isCreatingRide) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = PlannerDarkBg,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = "Create Convoy with Route",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = if (isButtonEnabled) PlannerDarkBg else PlannerTextMuted,
                                letterSpacing = 0.3.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = if (isButtonEnabled) PlannerDarkBg else PlannerTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 5. Subtle muted text link: "Skip Route & Start Directly"
                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        onSkipAndCreateRide()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    enabled = !isCreatingRide
                ) {
                    Text(
                        text = "Skip Route & Start Directly",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PlannerTextMuted
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
    val accentColor = if (isDestination) ElectricAmber else StartGreen

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f))
                .border(1.dp, accentColor.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = PlannerTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (suggestion.locality.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = suggestion.locality,
                    style = MaterialTheme.typography.bodySmall,
                    color = PlannerTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Layout item 3: Highlighted card with amber-outlined border containing:
 * - Route icon
 * - "Optimal Route Found" text
 * - Green "READY" pill badge on the right
 * - Two side-by-side stat boxes labeled "DISTANCE" and "EST. DURATION" with bold values
 */
@Composable
private fun CalculatedRouteCard(
    route: RouteResult,
    startName: String,
    destName: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PlannerCardBg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.2.dp, AmberGlowBorderBrush)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Route icon + "Optimal Route Found" + green "READY" pill badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ElectricAmber.copy(alpha = 0.15f))
                            .border(1.dp, ElectricAmber.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.AltRoute,
                            contentDescription = null,
                            tint = ElectricAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Optimal Route Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PlannerTextPrimary
                    )
                }

                // Green READY pill badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(StartGreen.copy(alpha = 0.15f))
                        .border(1.dp, StartGreen.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "READY",
                        color = StartGreen,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.6.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Two side-by-side stat boxes labeled "DISTANCE" and "EST. DURATION" with bold values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PlannerDarkBg)
                        .border(1.dp, PlannerBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            text = "DISTANCE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = PlannerTextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = PolylineUtils.formatDistance(route.distanceMeters),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = PlannerTextPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PlannerDarkBg)
                        .border(1.dp, PlannerBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            text = "EST. DURATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = PlannerTextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = PolylineUtils.formatDuration(route.durationSeconds),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = ElectricAmber
                        )
                    }
                }
            }

            if (route.summary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Via: ${route.summary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlannerTextSecondary,
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
        colors = CardDefaults.cardColors(containerColor = PlannerCardBg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, PlannerBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f))
                        .border(1.dp, iconTint.copy(alpha = 0.30f), CircleShape),
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
                    color = PlannerTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PlannerTextSecondary,
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
        colors = CardDefaults.cardColors(containerColor = PlannerCardBg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, PlannerStatusRed.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "⚠️", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Routing Note",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PlannerStatusRed
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = PlannerTextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ElectricAmber.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricAmber)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Retry Route", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyPlanningStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PlannerCardBg.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, PlannerBorder.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(PlannerSurfaceElevated)
                    .border(1.dp, PlannerBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NearMe,
                    contentDescription = null,
                    tint = ElectricAmber,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Plan Your Convoy Destination",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PlannerTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Search a destination above to calculate the shortest path, estimated ride time, and draw the convoy route line on your pack's live map.",
                style = MaterialTheme.typography.bodySmall,
                color = PlannerTextSecondary,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090B0E)
@Composable
fun TripPlannerModalPreview() {
    RideSafeTheme {
        TripPlannerModal(
            startQuery = "Connaught Place, New Delhi",
            destQuery = "India Gate, New Delhi",
            selectedStartPlace = PlaceSuggestion(
                name = "Connaught Place",
                locality = "New Delhi, Delhi",
                lat = 28.6304,
                lng = 77.2177
            ),
            selectedDestPlace = PlaceSuggestion(
                name = "India Gate",
                locality = "Rajpath, New Delhi",
                lat = 28.6129,
                lng = 77.2295
            ),
            startSuggestions = emptyList(),
            destSuggestions = emptyList(),
            isLoadingStartSuggestions = false,
            isLoadingDestSuggestions = false,
            isDetectingStartLocation = false,
            isCalculatingRoute = false,
            calculatedRoute = RouteResult(
                routePoints = emptyList(),
                encodedPolyline = "",
                distanceMeters = 4200.0,
                durationSeconds = 620.0,
                summary = "Kasturba Gandhi Marg"
            ),
            routeError = null,
            isCreatingRide = false,
            onStartQueryChange = {},
            onDestQueryChange = {},
            onSelectStartPlace = {},
            onSelectDestPlace = {},
            onUseCurrentLocationForStart = {},
            onClearStartPlace = {},
            onClearDestPlace = {},
            onRetryRouteCalculation = {},
            onCreateRideWithRoute = {},
            onSkipAndCreateRide = {},
            onDismiss = {}
        )
    }
}
