package com.ridesafe.app.ui.screens.home

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalInspectionMode
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
import com.ridesafe.app.R
import com.ridesafe.app.data.model.LocalRideSession
import com.ridesafe.app.data.model.LocalRideSessionUi
import com.ridesafe.app.data.model.PlaceSuggestion
import com.ridesafe.app.ui.theme.BikerAmber
import com.ridesafe.app.ui.theme.BikerBorder
import com.ridesafe.app.ui.theme.BikerCardBg
import com.ridesafe.app.ui.theme.BikerDarkBg
import com.ridesafe.app.ui.theme.BikerSurfaceElevated
import com.ridesafe.app.ui.theme.RideSafeTheme
import com.ridesafe.app.ui.theme.StatusAmber
import com.ridesafe.app.ui.theme.StatusGreen
import com.ridesafe.app.ui.theme.StatusRed
import com.ridesafe.app.ui.theme.TextMuted
import com.ridesafe.app.ui.theme.TextPrimary
import com.ridesafe.app.ui.theme.TextSecondary
import com.ridesafe.app.util.PermissionHelper

/**
 * HomeScreen handles rider onboarding: entering a rider name, creating a new ride,
 * entering a ride code to join an existing group, and viewing recent ride sessions.
 */
@Composable
fun HomeScreen(
    onRequestPermissions: () -> Unit,
    onRideJoined: (rideCode: String, riderId: String, riderName: String) -> Unit,
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
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Remove ride \"${session.rideCode}\" from this device? This will only remove it from your local history — active riders in the group won't be affected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSession(session.rideCode)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { sessionToDelete = null }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BikerCardBg,
            shape = RoundedCornerShape(18.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BikerDarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Hero Emblem & Cockpit Title
        Box(
            modifier = Modifier.size(136.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ambient neon crimson/amber halo backlight
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                StatusRed.copy(alpha = 0.45f),
                                BikerAmber.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Inner badge framing the motorcycle logo
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(BikerCardBg)
                    .border(2.5.dp, Brush.linearGradient(listOf(StatusRed, BikerAmber)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "BhaijiRide App Logo",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "BHAIJI RIDE",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Subtitle badge pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(BikerSurfaceElevated)
                .border(1.dp, BikerBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(StatusGreen)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "GPS CONVOY & EMERGENCY SAFETY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Permission Banner: Disappears automatically when granted
        AnimatedVisibility(
            visible = !hasPermissions,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(StatusAmber.copy(alpha = 0.12f))
                        .border(1.dp, StatusAmber.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(StatusAmber.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = StatusAmber,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Location Permission Needed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusAmber
                            )
                            Text(
                                text = "BhaijiRide requires GPS to share live positions and emergency alerts.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusAmber),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Grant", color = BikerDarkBg, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // -------------------------------------------------------------
        // RIDER IDENTITY CARD
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(BikerCardBg)
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(BikerBorder, if (uiState.riderName.isNotBlank()) BikerAmber.copy(alpha = 0.5f) else BikerBorder)
                    ),
                    shape = RoundedCornerShape(22.dp)
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
                            tint = BikerAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RIDER CALLSIGN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.2.sp
                        )
                    }
                    if (uiState.riderName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(StatusGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "READY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusGreen
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.riderName,
                    onValueChange = onRiderNameChange,
                    label = { Text("Your Display Name (e.g. Alex)") },
                    placeholder = { Text("Enter your name") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (uiState.riderName.isNotBlank()) BikerAmber else TextSecondary
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BikerAmber,
                        unfocusedBorderColor = BikerBorder,
                        focusedLabelColor = BikerAmber,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = BikerSurfaceElevated.copy(alpha = 0.5f),
                        unfocusedContainerColor = BikerSurfaceElevated.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // -------------------------------------------------------------
        // START NEW CONVOY CARD
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(BikerCardBg)
                .border(1.dp, BikerBorder, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(BikerAmber.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = BikerAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "START A NEW CONVOY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Host a new group ride session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Generates a unique 6-letter code to share with your pack for real-time GPS tracking and emergency safety.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Button: Plan Route & Create Ride
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
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BikerAmber),
                    enabled = !uiState.isCreatingRide && !uiState.isJoiningRide && uiState.rejoiningCode == null
                ) {
                    if (uiState.isCreatingRide) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = BikerDarkBg,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, tint = BikerDarkBg)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Plan Route & Create Ride",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = BikerDarkBg
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Button: Quick Start without Route
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
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BikerBorder),
                    enabled = !uiState.isCreatingRide && !uiState.isJoiningRide && uiState.rejoiningCode == null
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Quick Start (No Route)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // -------------------------------------------------------------
        // JOIN CONVOY CARD
        // -------------------------------------------------------------
        val clipboardManager = LocalClipboardManager.current
        val context = LocalContext.current

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(BikerCardBg)
                .border(1.dp, BikerBorder, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(BikerSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "JOIN EXISTING CONVOY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Connect with your pack",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = uiState.joinCode,
                    onValueChange = onJoinCodeChange,
                    label = { Text("Convoy Code (e.g. MOTO84)") },
                    placeholder = { Text("ENTER CODE") },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        fontSize = 18.sp
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
                                tint = BikerAmber,
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
                        focusedBorderColor = BikerAmber,
                        unfocusedBorderColor = BikerBorder,
                        focusedLabelColor = BikerAmber,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = BikerSurfaceElevated.copy(alpha = 0.5f),
                        unfocusedContainerColor = BikerSurfaceElevated.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

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
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BikerSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BikerBorder),
                    enabled = !uiState.isCreatingRide && !uiState.isJoiningRide && uiState.rejoiningCode == null
                ) {
                    if (uiState.isJoiningRide) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = BikerAmber,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Join Convoy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Error message presentation
        if (uiState.errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StatusRed.copy(alpha = 0.15f))
                    .border(1.dp, StatusRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = StatusRed,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // -------------------------------------------------------------
        // CONVOY SESSIONS HISTORY SECTION
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
                    color = TextMuted,
                    letterSpacing = 1.2.sp
                )
                if (uiState.sessions.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BikerSurfaceElevated)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${uiState.sessions.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }
                }
            }

            IconButton(
                onClick = onRefreshSessions,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh session statuses",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (uiState.sessions.isEmpty() && !uiState.isLoadingSessions) {
            // Empty State Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(BikerCardBg)
                    .border(1.dp, BikerBorder, RoundedCornerShape(22.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(BikerSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No Saved Convoys",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Convoys you create or join will be saved here so you can quickly jump back into the action.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Session Cards List
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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

        Spacer(modifier = Modifier.height(40.dp))
    }

    // Uber-style Plan Trip Modal
    if (uiState.isTripPlannerOpen) {
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
            .clip(RoundedCornerShape(22.dp))
            .background(BikerCardBg)
            .border(
                width = 1.dp,
                color = if (sessionUi.isActive) StatusGreen.copy(alpha = 0.45f) else BikerBorder,
                shape = RoundedCornerShape(22.dp)
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
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.5.sp,
                    color = TextPrimary
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
                        tint = BikerAmber,
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
                    color = TextSecondary
                )
                if (sessionUi.riderName.isNotBlank()) {
                    Text(
                        text = " • as ${sessionUi.riderName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sessionUi.isActive) BikerAmber else BikerSurfaceElevated
                    ),
                    border = if (sessionUi.isActive) null else androidx.compose.foundation.BorderStroke(1.dp, BikerBorder),
                    enabled = !isRejoining
                ) {
                    if (isRejoining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = if (sessionUi.isActive) BikerDarkBg else BikerAmber,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (sessionUi.isActive) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Refresh,
                            contentDescription = null,
                            tint = if (sessionUi.isActive) BikerDarkBg else TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (sessionUi.isActive) "Join Convoy" else "Rejoin",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (sessionUi.isActive) BikerDarkBg else TextPrimary
                        )
                    }
                }

                // Delete button (removes from local DataStore only)
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BikerSurfaceElevated)
                        .border(1.dp, BikerBorder, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete session locally",
                        tint = StatusRed.copy(alpha = 0.85f),
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
    val bgColor = if (isActive) StatusGreen.copy(alpha = 0.14f) else BikerSurfaceElevated
    val borderColor = if (isActive) StatusGreen.copy(alpha = 0.5f) else BikerBorder
    val dotColor = if (isActive) StatusGreen else TextMuted
    val textColor = if (isActive) StatusGreen else TextMuted
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
                letterSpacing = 0.5.sp,
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

@Preview(showBackground = true, backgroundColor = 0xFF101216)
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
