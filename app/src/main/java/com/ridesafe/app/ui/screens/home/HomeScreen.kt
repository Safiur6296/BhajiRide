package com.ridesafe.app.ui.screens.home

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ridesafe.app.BuildConfig
import com.ridesafe.app.R
import com.ridesafe.app.data.model.LocalRideSession
import com.ridesafe.app.data.model.LocalRideSessionUi
import com.ridesafe.app.data.model.PlaceSuggestion
import com.ridesafe.app.ui.theme.RideSafeTheme
import com.ridesafe.app.util.PermissionHelper

// -------------------------------------------------------------
// Tesla / Uber-Inspired Premium Dark Theme Tokens for HomeScreen
// -------------------------------------------------------------
private val HomeDarkBg = Color(0xFF090B0E)             // Deep charcoal / near-black
private val HomeCardBg = Color(0xFF13171F)             // Rich charcoal card background
private val HomeSurfaceElevated = Color(0xFF1B212C)     // Interactive elevated surface
private val HomeBorder = Color(0xFF26303E)              // Subtle structural border

// Electric Amber-Orange Brand Accents
private val ElectricAmber = Color(0xFFFF9800)          // Pure electric amber
private val ElectricAmberBright = Color(0xFFFFA726)    // Glowing amber highlight
private val ElectricAmberOrange = Color(0xFFFF6D00)    // High-energy electric orange

// Gradients & Glow Brushes
private val AmberButtonGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFFFA000), // Electric Amber
        Color(0xFFFF6D00)  // Electric Amber-Orange
    )
)

private val AmberGlowBorderBrush = Brush.linearGradient(
    colors = listOf(
        ElectricAmber.copy(alpha = 0.55f),
        Color(0xFF2B3545).copy(alpha = 0.5f),
        ElectricAmber.copy(alpha = 0.20f)
    )
)

private val ActiveRideBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF10B981).copy(alpha = 0.6f),
        Color(0xFF26303E),
        Color(0xFF10B981).copy(alpha = 0.25f)
    )
)

// High-contrast clean typography colors
private val HomeTextPrimary = Color(0xFFF8FAFC)        // Crisp white / platinum
private val HomeTextSecondary = Color(0xFF94A3B8)      // Clean cool slate
private val HomeTextMuted = Color(0xFF64748B)          // Muted slate
private val HomeStatusGreen = Color(0xFF10B981)        // Crisp status green
private val HomeStatusRed = Color(0xFFEF4444)          // Crisp status red

/**
 * HomeScreen handles rider onboarding: entering a rider name, creating a new ride,
 * entering a ride code to join an existing group, and viewing recent ride sessions.
 */
@Composable
fun HomeScreen(
    onRequestPermissions: () -> Unit,
    onRideJoined: (rideCode: String, riderId: String, riderName: String) -> Unit,
    onCheckForUpdates: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var hasPermissions by remember {
        mutableStateOf(PermissionHelper.hasRequiredRidePermissions(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermissions = PermissionHelper.hasRequiredRidePermissions(context)
                viewModel.refreshSessions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    HomeScreenContent(
        uiState = uiState,
        hasPermissions = hasPermissions,
        onRequestPermissions = onRequestPermissions,
        onCheckForUpdates = onCheckForUpdates,
        onRiderNameChange = viewModel::onRiderNameChange,
        onJoinCodeChange = viewModel::onJoinCodeChange,
        onCreateRide = {
            viewModel.createRide(context, onRideJoined)
        },
        onJoinRide = {
            viewModel.joinRide(context, onRideJoined)
        },
        onRejoinRide = { session ->
            viewModel.rejoinRide(context, session, onRideJoined)
        },
        onDeleteSession = viewModel::deleteSession,
        onRefreshSessions = viewModel::refreshSessions,
        onOpenTripPlanner = viewModel::openTripPlanner,
        onCloseTripPlanner = viewModel::closeTripPlanner,
        onStartQueryChange = viewModel::onStartQueryChange,
        onDestQueryChange = viewModel::onDestQueryChange,
        onSelectStartPlace = viewModel::selectStartPlace,
        onSelectDestPlace = viewModel::selectDestPlace,
        onUseCurrentLocationForStart = viewModel::detectAndSetCurrentLocationAsStart,
        onClearStartPlace = viewModel::clearStartPlace,
        onClearDestPlace = viewModel::clearDestPlace,
        onRetryRouteCalculation = viewModel::calculateRoute,
        onCreateRideWithRoute = {
            viewModel.createRideWithPlannedTrip(context, onRideJoined)
        }
    )
}

/**
 * Stateless content composable for HomeScreen, separating UI from ViewModel.
 */
@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onCheckForUpdates: () -> Unit = {},
    onRiderNameChange: (String) -> Unit,
    onJoinCodeChange: (String) -> Unit,
    onCreateRide: () -> Unit,
    onJoinRide: () -> Unit,
    onRejoinRide: (LocalRideSession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onRefreshSessions: () -> Unit,
    onOpenTripPlanner: () -> Unit = {},
    onCloseTripPlanner: () -> Unit = {},
    onStartQueryChange: (String) -> Unit = {},
    onDestQueryChange: (String) -> Unit = {},
    onSelectStartPlace: (PlaceSuggestion) -> Unit = {},
    onSelectDestPlace: (PlaceSuggestion) -> Unit = {},
    onUseCurrentLocationForStart: () -> Unit = {},
    onClearStartPlace: () -> Unit = {},
    onClearDestPlace: () -> Unit = {},
    onRetryRouteCalculation: () -> Unit = {},
    onCreateRideWithRoute: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var sessionToDelete by remember { mutableStateOf<LocalRideSessionUi?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Delete Confirmation Dialog
    if (sessionToDelete != null) {
        val session = sessionToDelete!!
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Text(
                    text = "Forget Ride Session?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = HomeTextPrimary
                )
            },
            text = {
                Text(
                    text = "Remove ride \"${session.rideCode}\" from this device? This will only remove it from your local history — active riders in the group won't be affected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HomeTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSession(session.rideCode)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HomeStatusRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { sessionToDelete = null }
                ) {
                    Text("Cancel", color = HomeTextSecondary)
                }
            },
            containerColor = HomeCardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeDarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(HomeDarkBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // -------------------------------------------------------------
            // 1. Centered circular app icon inside a glowing amber ring
            // -------------------------------------------------------------
            Box(
                modifier = Modifier.size(136.dp),
                contentAlignment = Alignment.Center
            ) {
                // Ambient electric amber halo glow backlight
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ElectricAmber.copy(alpha = 0.32f),
                                    ElectricAmber.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Glowing amber ring framing the circular motorcycle logo
                Box(
                    modifier = Modifier
                        .size(106.dp)
                        .clip(CircleShape)
                        .background(HomeCardBg)
                        .border(
                            width = 2.5.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    ElectricAmber,
                                    ElectricAmberBright,
                                    ElectricAmberOrange,
                                    ElectricAmber
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "PackSync App Logo",
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // -------------------------------------------------------------
            // 2. Bold large app name "PACKSYNC"
            // -------------------------------------------------------------
            Text(
                text = "PACKSYNC",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 30.sp,
                    letterSpacing = 3.5.sp
                ),
                color = HomeTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // -------------------------------------------------------------
            // 3. Pill-shaped tagline badge with small green status dot
            // -------------------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(HomeSurfaceElevated.copy(alpha = 0.8f))
                    .border(1.dp, HomeBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(HomeStatusGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "NEVER LOSE YOUR PACK AGAIN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = HomeTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // -------------------------------------------------------------
            // 4. Small muted version / in-app update text row
            // -------------------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onCheckForUpdates() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Check for Updates",
                    tint = HomeTextMuted,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "v${BuildConfig.VERSION_NAME} • Check for Updates",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = HomeTextMuted,
                    letterSpacing = 0.3.sp
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Permission Banner (if needed)
            AnimatedVisibility(
                visible = !hasPermissions,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(ElectricAmber.copy(alpha = 0.12f))
                            .border(1.dp, ElectricAmber.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(ElectricAmber.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = ElectricAmber,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Location Permission Needed",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricAmber
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "PackSync requires GPS to share live positions and emergency alerts.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HomeTextPrimary,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onRequestPermissions,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricAmber),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("Grant", color = HomeDarkBg, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                }
            }

            // -------------------------------------------------------------
            // 5. "Rider Callsign" card: outlined amber border, display name
            //    text field, green "READY" badge in the corner
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(HomeCardBg)
                    .border(
                        width = 1.dp,
                        brush = AmberGlowBorderBrush,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TwoWheeler,
                                contentDescription = null,
                                tint = ElectricAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "RIDER CALLSIGN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricAmber,
                                letterSpacing = 1.5.sp
                            )
                        }

                        // Green "READY" badge in the corner
                        if (uiState.riderName.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(HomeStatusGreen.copy(alpha = 0.15f))
                                    .border(1.dp, HomeStatusGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(HomeStatusGreen)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "READY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = HomeStatusGreen,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = uiState.riderName,
                        onValueChange = onRiderNameChange,
                        label = { Text("Display Name (e.g. Alex)") },
                        placeholder = { Text("Enter your callsign") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (uiState.riderName.isNotBlank()) ElectricAmber else HomeTextMuted
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricAmber,
                            unfocusedBorderColor = HomeBorder,
                            focusedLabelColor = ElectricAmber,
                            unfocusedLabelColor = HomeTextSecondary,
                            focusedTextColor = HomeTextPrimary,
                            unfocusedTextColor = HomeTextPrimary,
                            focusedContainerColor = HomeSurfaceElevated.copy(alpha = 0.5f),
                            unfocusedContainerColor = HomeSurfaceElevated.copy(alpha = 0.25f),
                            cursorColor = ElectricAmber
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // -------------------------------------------------------------
            // 6. "Start a New Convoy" card:
            //    - Primary amber gradient fill: "Plan Route & Create Ride"
            //    - Secondary outlined button: "Quick Start (No Route)"
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(HomeCardBg)
                    .border(
                        width = 1.dp,
                        brush = AmberGlowBorderBrush,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(20.dp)
            ) {
                Column {
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
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = ElectricAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "START A NEW CONVOY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricAmber,
                                letterSpacing = 1.3.sp
                            )
                            Text(
                                text = "Host a new group ride session",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = HomeTextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Generates a unique 6-letter code to share with your pack for real-time GPS tracking and emergency safety.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HomeTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Primary Button: Amber Gradient Fill
                    val isPlanButtonEnabled = !uiState.isCreatingRide && !uiState.isJoiningRide && uiState.rejoiningCode == null
                    Button(
                        onClick = {
                            if (!hasPermissions) {
                                onRequestPermissions()
                                return@Button
                            }
                            focusManager.clearFocus()
                            onOpenTripPlanner()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isPlanButtonEnabled) AmberButtonGradient
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
                        enabled = isPlanButtonEnabled
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (uiState.isCreatingRide) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = HomeDarkBg,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.AltRoute,
                                    contentDescription = null,
                                    tint = HomeDarkBg,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Plan Route & Create Ride",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = HomeDarkBg,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Button: Outlined "Quick Start (No Route)"
                    OutlinedButton(
                        onClick = {
                            if (!hasPermissions) {
                                onRequestPermissions()
                            } else {
                                focusManager.clearFocus()
                                onCreateRide()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = HomeTextPrimary,
                            disabledContentColor = HomeTextMuted
                        ),
                        border = BorderStroke(1.dp, HomeBorder),
                        enabled = isPlanButtonEnabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = if (isPlanButtonEnabled) ElectricAmber else HomeTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Quick Start (No Route)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isPlanButtonEnabled) HomeTextPrimary else HomeTextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // -------------------------------------------------------------
            // 7. "Join Existing Convoy" card: code input placeholder,
            //    paste/clipboard icon, and "Join Convoy" button
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(HomeCardBg)
                    .border(
                        width = 1.dp,
                        brush = AmberGlowBorderBrush,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(HomeSurfaceElevated)
                                .border(1.dp, HomeBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = ElectricAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "JOIN EXISTING CONVOY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricAmber,
                                letterSpacing = 1.3.sp
                            )
                            Text(
                                text = "Connect with your pack",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = HomeTextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = uiState.joinCode,
                        onValueChange = onJoinCodeChange,
                        label = { Text("Convoy Code (e.g. MOTO84)") },
                        placeholder = { Text("ENTER CODE", color = HomeTextMuted) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                            fontSize = 18.sp,
                            color = HomeTextPrimary
                        ),
                        trailingIcon = {
                            // 1-Tap Paste Button from Clipboard
                            IconButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text?.trim()?.uppercase()
                                    if (!clip.isNullOrEmpty()) {
                                        onJoinCodeChange(clip)
                                        Toast.makeText(context, "Pasted: $clip", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste ride code",
                                    tint = ElectricAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricAmber,
                            unfocusedBorderColor = HomeBorder,
                            focusedLabelColor = ElectricAmber,
                            unfocusedLabelColor = HomeTextSecondary,
                            focusedTextColor = HomeTextPrimary,
                            unfocusedTextColor = HomeTextPrimary,
                            focusedContainerColor = HomeSurfaceElevated.copy(alpha = 0.5f),
                            unfocusedContainerColor = HomeSurfaceElevated.copy(alpha = 0.25f),
                            cursorColor = ElectricAmber
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val isJoinButtonEnabled = !uiState.isCreatingRide && !uiState.isJoiningRide && uiState.rejoiningCode == null
                    Button(
                        onClick = {
                            if (!hasPermissions) {
                                onRequestPermissions()
                                return@Button
                            }
                            focusManager.clearFocus()
                            onJoinRide()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.joinCode.isNotBlank()) ElectricAmber else HomeSurfaceElevated,
                            disabledContainerColor = HomeSurfaceElevated.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (uiState.joinCode.isNotBlank()) ElectricAmber else HomeBorder
                        ),
                        enabled = isJoinButtonEnabled
                    ) {
                        if (uiState.isJoiningRide) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = if (uiState.joinCode.isNotBlank()) HomeDarkBg else ElectricAmber,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = if (uiState.joinCode.isNotBlank()) HomeDarkBg else HomeTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Join Convoy",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.joinCode.isNotBlank()) HomeDarkBg else HomeTextSecondary
                            )
                        }
                    }
                }
            }

            // Error message presentation
            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(HomeStatusRed.copy(alpha = 0.14f))
                        .border(1.dp, HomeStatusRed.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = HomeStatusRed,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // -------------------------------------------------------------
            // 8. "Recent Convoys" section header with refresh icon,
            //    showing list of session cards or empty state
            // -------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "RECENT CONVOYS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeTextMuted,
                        letterSpacing = 1.5.sp
                    )
                    if (uiState.sessions.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(HomeSurfaceElevated)
                                .border(1.dp, HomeBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${uiState.sessions.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricAmber
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onRefreshSessions,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh session statuses",
                        tint = if (uiState.isLoadingSessions) ElectricAmber else HomeTextMuted,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (uiState.sessions.isEmpty() && !uiState.isLoadingSessions) {
                // Empty State Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(HomeCardBg)
                        .border(1.dp, HomeBorder, RoundedCornerShape(20.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(HomeSurfaceElevated)
                                .border(1.dp, HomeBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = HomeTextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Saved Convoys",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = HomeTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Convoys you create or join will be saved here so you can quickly jump back into the action.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HomeTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                // Session Cards List
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    uiState.sessions.forEach { sessionUi ->
                        SessionCard(
                            sessionUi = sessionUi,
                            isRejoining = uiState.rejoiningCode == sessionUi.rideCode,
                            onRejoin = {
                                if (!hasPermissions) {
                                    onRequestPermissions()
                                } else {
                                    onRejoinRide(sessionUi.session)
                                }
                            },
                            onDelete = {
                                sessionToDelete = sessionUi
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }

        // Full-page Uber-style Plan Trip Screen
        AnimatedVisibility(
            visible = uiState.isTripPlannerOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            TripPlannerModal(
                startQuery = uiState.startLocationQuery,
                destQuery = uiState.destLocationQuery,
                selectedStartPlace = uiState.selectedStartPlace,
                selectedDestPlace = uiState.selectedDestPlace,
                startSuggestions = uiState.startSuggestions,
                destSuggestions = uiState.destSuggestions,
                isLoadingStartSuggestions = uiState.isLoadingStartSuggestions,
                isLoadingDestSuggestions = uiState.isLoadingDestSuggestions,
                isDetectingStartLocation = uiState.isDetectingStartLocation,
                isCalculatingRoute = uiState.isCalculatingRoute,
                calculatedRoute = uiState.calculatedRoute,
                routeError = uiState.routeError,
                isCreatingRide = uiState.isCreatingRide,
                onStartQueryChange = onStartQueryChange,
                onDestQueryChange = onDestQueryChange,
                onSelectStartPlace = onSelectStartPlace,
                onSelectDestPlace = onSelectDestPlace,
                onUseCurrentLocationForStart = onUseCurrentLocationForStart,
                onClearStartPlace = onClearStartPlace,
                onClearDestPlace = onClearDestPlace,
                onRetryRouteCalculation = onRetryRouteCalculation,
                onCreateRideWithRoute = onCreateRideWithRoute,
                onSkipAndCreateRide = onCreateRide,
                onDismiss = onCloseTripPlanner
            )
        }
    }
}

/**
 * Individual Session Card displaying ride code, relative time, active/ended status,
 * rejoin action, and delete action.
 */
@Composable
private fun SessionCard(
    sessionUi: LocalRideSessionUi,
    isRejoining: Boolean,
    onRejoin: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HomeCardBg)
            .border(
                width = 1.dp,
                brush = if (sessionUi.isActive) ActiveRideBorderBrush else Brush.linearGradient(listOf(HomeBorder, HomeBorder)),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(18.dp)
    ) {
        Column {
            // Header row: Ride Code + Copy Button + Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large readable Ride Code for bikers
                Text(
                    text = sessionUi.rideCode,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.5.sp,
                    color = HomeTextPrimary
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Tap-to-copy button
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(sessionUi.rideCode))
                        Toast.makeText(context, "Copied ${sessionUi.rideCode}", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = ElectricAmber,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Active vs Ended Status Badge
                SessionStatusBadge(isActive = sessionUi.isActive)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Time description (e.g. "Created 2 hours ago" or "Joined yesterday")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatSessionTime(sessionUi.timestamp, sessionUi.isHost),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTextSecondary
                )
                if (sessionUi.riderName.isNotBlank()) {
                    Text(
                        text = " • as ${sessionUi.riderName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons: Join and Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Join / Rejoin button
                Button(
                    onClick = onRejoin,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sessionUi.isActive) ElectricAmber else HomeSurfaceElevated
                    ),
                    border = if (sessionUi.isActive) null else BorderStroke(1.dp, HomeBorder),
                    enabled = !isRejoining
                ) {
                    if (isRejoining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = if (sessionUi.isActive) HomeDarkBg else ElectricAmber,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (sessionUi.isActive) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Refresh,
                            contentDescription = null,
                            tint = if (sessionUi.isActive) HomeDarkBg else HomeTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (sessionUi.isActive) "Join Convoy" else "Rejoin",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (sessionUi.isActive) HomeDarkBg else HomeTextPrimary
                        )
                    }
                }

                // Delete button (removes from local DataStore only)
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(HomeSurfaceElevated)
                        .border(1.dp, HomeBorder, RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete session locally",
                        tint = HomeStatusRed.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Capsule status badge showing [LIVE NOW] in green or [ENDED] in gray.
 */
@Composable
private fun SessionStatusBadge(isActive: Boolean) {
    val bgColor = if (isActive) HomeStatusGreen.copy(alpha = 0.15f) else HomeSurfaceElevated
    val borderColor = if (isActive) HomeStatusGreen.copy(alpha = 0.45f) else HomeBorder
    val dotColor = if (isActive) HomeStatusGreen else HomeTextMuted
    val textColor = if (isActive) HomeStatusGreen else HomeTextMuted
    val label = if (isActive) "LIVE NOW" else "ENDED"

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp,
                color = textColor
            )
        }
    }
}

/**
 * Formats relative time (e.g., "Created 2h ago", "Joined yesterday").
 */
private fun formatSessionTime(timestamp: Long, isHost: Boolean): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val prefix = if (isHost) "Created" else "Joined"
    return when {
        diff < 60_000L -> "$prefix just now"
        diff < 3600_000L -> "$prefix ${diff / 60_000L}m ago"
        diff < 86400_000L -> "$prefix ${diff / 3600_000L}h ago"
        diff < 172800_000L -> "$prefix yesterday"
        else -> "$prefix ${DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.DAY_IN_MILLIS)}"
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090B0E)
@Composable
fun HomeScreenPreview() {
    RideSafeTheme {
        HomeScreenContent(
            uiState = HomeUiState(
                riderName = "Alex",
                joinCode = "CREW99",
                sessions = listOf(
                    LocalRideSessionUi(
                        session = LocalRideSession(
                            rideCode = "MOTO84",
                            riderId = "id1",
                            riderName = "Alex",
                            timestamp = System.currentTimeMillis() - 15 * 60 * 1000L,
                            isHost = true
                        ),
                        isActive = true
                    ),
                    LocalRideSessionUi(
                        session = LocalRideSession(
                            rideCode = "ROAD21",
                            riderId = "id2",
                            riderName = "Alex",
                            timestamp = System.currentTimeMillis() - 4 * 3600 * 1000L,
                            isHost = false
                        ),
                        isActive = false
                    )
                ),
                isLoadingSessions = false
            ),
            hasPermissions = true,
            onRequestPermissions = {},
            onRiderNameChange = {},
            onJoinCodeChange = {},
            onCreateRide = {},
            onJoinRide = {},
            onRejoinRide = {},
            onDeleteSession = {},
            onRefreshSessions = {}
        )
    }
}
