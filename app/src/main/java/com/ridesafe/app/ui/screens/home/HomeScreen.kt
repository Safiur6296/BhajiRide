package com.ridesafe.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ridesafe.app.data.repository.RideRepository
import com.ridesafe.app.service.LocationTrackingService
import com.ridesafe.app.ui.theme.BikerBorder
import com.ridesafe.app.ui.theme.BikerCardBg
import com.ridesafe.app.ui.theme.BikerDarkBg
import com.ridesafe.app.ui.theme.BikerSurfaceElevated
import com.ridesafe.app.ui.theme.RideSafeTheme
import com.ridesafe.app.ui.theme.StatusAmber
import com.ridesafe.app.ui.theme.StatusRed
import com.ridesafe.app.ui.theme.TextMuted
import com.ridesafe.app.ui.theme.TextPrimary
import com.ridesafe.app.ui.theme.TextSecondary
import com.ridesafe.app.util.PermissionHelper
import kotlinx.coroutines.launch

/**
 * HomeScreen handles rider onboarding: entering a rider name, creating a new ride,
 * or entering a ride code to join an existing group.
 */
@Composable
fun HomeScreen(
    onRequestPermissions: () -> Unit,
    onRideJoined: (rideCode: String, riderId: String, riderName: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val isPreview = LocalInspectionMode.current
    val repository = if (isPreview) null else remember { RideRepository() }

    var riderName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var isCreatingRide by remember { mutableStateOf(false) }
    var isJoiningRide by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Reactive state: re-checks automatically whenever user returns to the app (ON_RESUME)
    var hasPermissions by remember {
        mutableStateOf(PermissionHelper.hasRequiredRidePermissions(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermissions = PermissionHelper.hasRequiredRidePermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BikerDarkBg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // App Header & Branding
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "BhaijiRide",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
        Text(
            text = "Motorbike Group GPS & Status Tracking",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(28.dp))

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
                        .clip(RoundedCornerShape(16.dp))
                        .background(StatusAmber.copy(alpha = 0.12f))
                        .border(1.dp, StatusAmber.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = StatusAmber,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Location Permission Needed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusAmber
                            )
                            Text(
                                text = "BhaijiRide requires location to share your live GPS with other riders.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = {
                                onRequestPermissions()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusAmber),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Grant", color = BikerDarkBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Rider Name Input Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BikerCardBg)
                .border(1.dp, BikerBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "RIDER IDENTITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = riderName,
                    onValueChange = {
                        riderName = it
                        errorMessage = null
                    },
                    label = { Text("Your Display Name (e.g. Alex)") },
                    placeholder = { Text("Enter your name") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = BikerBorder,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Create Ride Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BikerCardBg)
                .border(1.dp, BikerBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "START A NEW RIDE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Creates a new session and generates a 6-letter code to share with your group.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (!hasPermissions) {
                            onRequestPermissions()
                            return@Button
                        }
                        if (riderName.trim().isEmpty()) {
                            errorMessage = "Please enter your name first."
                            return@Button
                        }
                        focusManager.clearFocus()
                        isCreatingRide = true
                        errorMessage = null

                        scope.launch {
                            try {
                                val result = repository?.createRide(riderName) ?: Result.failure(Exception("Preview mode"))
                                result.onSuccess { (rideCode, riderId) ->
                                    LocationTrackingService.startTracking(
                                        context = context,
                                        rideCode = rideCode,
                                        riderId = riderId,
                                        riderName = riderName
                                    )
                                    onRideJoined(rideCode, riderId, riderName)
                                }.onFailure { error ->
                                    errorMessage = error.localizedMessage ?: "Failed to create ride."
                                }
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage ?: "An unexpected error occurred."
                            } finally {
                                isCreatingRide = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = !isCreatingRide && !isJoiningRide
                ) {
                    if (isCreatingRide) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = BikerDarkBg,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null, tint = BikerDarkBg)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create Ride Session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BikerDarkBg
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Join Ride Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BikerCardBg)
                .border(1.dp, BikerBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "JOIN AN EXISTING RIDE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = {
                        if (it.length <= 8) {
                            joinCode = it.uppercase()
                            errorMessage = null
                        }
                    },
                    label = { Text("Ride Code (e.g. MOTO84)") },
                    placeholder = { Text("ENTER CODE") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = BikerBorder,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (!hasPermissions) {
                            onRequestPermissions()
                            return@Button
                        }
                        if (riderName.trim().isEmpty()) {
                            errorMessage = "Please enter your name first."
                            return@Button
                        }
                        if (joinCode.trim().isEmpty()) {
                            errorMessage = "Please enter the ride code."
                            return@Button
                        }
                        focusManager.clearFocus()
                        isJoiningRide = true
                        errorMessage = null

                        scope.launch {
                            try {
                                val result = repository?.joinRide(joinCode, riderName) ?: Result.failure(Exception("Preview mode"))
                                result.onSuccess { riderId ->
                                    LocationTrackingService.startTracking(
                                        context = context,
                                        rideCode = joinCode.trim().uppercase(),
                                        riderId = riderId,
                                        riderName = riderName
                                    )
                                    onRideJoined(joinCode.trim().uppercase(), riderId, riderName)
                                }.onFailure { error ->
                                    errorMessage = error.localizedMessage ?: "Failed to join ride."
                                }
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage ?: "An unexpected error occurred."
                            } finally {
                                isJoiningRide = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BikerSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BikerBorder),
                    enabled = !isCreatingRide && !isJoiningRide
                ) {
                    if (isJoiningRide) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Join Ride",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Error message presentation
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage ?: "",
                color = StatusRed,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101216)
@Composable
fun HomeScreenPreview() {
    RideSafeTheme {
        HomeScreen(
            onRequestPermissions = {},
            onRideJoined = { _, _, _ -> }
        )
    }
}

