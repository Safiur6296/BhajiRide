# BhaijiRide: Motorbike Group Ride Tracking App

A native Android application built with **Kotlin**, **Jetpack Compose**, **Firebase Realtime Database**, **Google Maps SDK**, and **Foreground Location Service**.

Designed for motorbike group rides: riders create or join a session via a 6-character code, share live GPS locations in real time (even when the phone is locked or navigation is open), and communicate stop reasons (Refueling, Tire Puncture, Rest, Emergency) with a single tap.

---

## Architecture & Technology Stack

| Component | Technology | Purpose |
| :--- | :--- | :--- |
| **Language** | Kotlin 2.0 | Idiomatic Android development (Coroutines, Flow, Data Classes) |
| **UI Framework** | Jetpack Compose + Material 3 | Declarative UI without XML boilerplate or RecyclerView adapters |
| **Backend / Sync** | Firebase Realtime Database (Free Spark) | Live bi-directional JSON synchronization |
| **Authentication** | Firebase Anonymous Auth | Unique rider UID without login/password friction |
| **Maps & Markers** | Google Maps Compose + Maps SDK | Real-time rider markers colored by status |
| **Location Tracking** | `FusedLocationProviderClient` + Foreground Service | Continuous GPS updates with ongoing notification |

---

## Step 1: Open in Android Studio

1. Launch **Android Studio** (Ladybug, Koala, Jellyfish, or newer).
2. Click **Open** and select the folder:
   `c:\Users\raham\OneDrive\Desktop\RideSafe`
3. Allow Android Studio to complete the initial Gradle Sync.

---

## Step 2: Set up Firebase Realtime Database & Auth

1. Go to the [Firebase Console](https://console.firebase.google.com/) and click **Add project** (name it `RideSafe`).
2. **Add Android App**:
   - Package name: `com.ridesafe.app` (must match exactly)
   - App nickname: `RideSafe`
3. Download the generated `google-services.json` file.
4. Replace the template file in your project:
   `RideSafe/app/google-services.json`
5. **Enable Anonymous Authentication**:
   - In Firebase Console, navigate to **Build > Authentication > Sign-in method**.
   - Select **Anonymous**, enable it, and click **Save**.
6. **Enable Realtime Database**:
   - In Firebase Console, navigate to **Build > Realtime Database > Create Database**.
   - Choose your database location.
   - Go to the **Rules** tab and paste these development rules:
     ```json
     {
       "rules": {
         "rides": {
           "$rideCode": {
             ".read": true,
             ".write": true
           }
         }
       }
     }
     ```
   - Click **Publish**.

---

## Step 3: Set up Google Maps API Key

1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Select your project and navigate to **APIs & Services > Library**.
3. Search for **Maps SDK for Android** and click **Enable**.
4. Navigate to **APIs & Services > Credentials** and click **Create Credentials > API Key**.
5. In the root directory of this project (`RideSafe/`), create or open the `local.properties` file and add:
   ```properties
   MAPS_API_KEY=AIzaSyYourActualGoogleMapsApiKeyHere
   ```
6. Click **Sync Project with Gradle Files** (elephant icon) in Android Studio.

---

## Step 4: Testing with Two Emulators (Dual Rider Simulation)

To test the multi-rider group experience on your computer:

### 1. Launch Two Emulators
- Open **Device Manager** in Android Studio (`Tools > Device Manager`).
- Launch two different Virtual Devices (e.g., *Pixel 8 - API 34* and *Pixel 7 - API 34*).

### 2. Run the App on Both Emulators
- In the top run bar, select **Emulator A** and click **Run (Shift + F10)**.
- Select **Emulator B** and click **Run**.

### 3. Start a Ride
- **On Emulator A (Rider 1)**:
  - Enter name: `Rider Alex`
  - Tap **Grant** for location permissions when prompted.
  - Tap **Create Ride Session**.
  - Note the generated 6-character code (e.g., `MOTO84`).
- **On Emulator B (Rider 2)**:
  - Enter name: `Rider Jordan`
  - Tap **Grant** for location permissions.
  - Enter code `MOTO84` in the Join box.
  - Tap **Join Ride**.

### 4. Simulate Movement & Routes
- On **Emulator A**, click the three dots (`...`) on the emulator sidebar to open **Extended Controls**.
- Click **Location**.
- Click the **Routes** tab, pick a start and end location (or load a GPX file), set speed to 50 km/h, and click **Play Route**.
- Look at **Emulator B's screen**: you will see Emulator A's marker moving across the map in real time!

### 5. Test Stop Reasons
- On **Emulator A**, tap the bottom **My Status** button.
- Choose **⛽ Refueling** or **🔧 Tire Puncture**.
- Immediately on **Emulator B**, Emulator A's marker turns yellow/orange, and the rider list shows the updated status.
- Tap **Resume Riding** to clear the status.

---

## Kotlin & Android Concepts Guide for Java Developers

| Java / Selenium Concept | Kotlin / Android Equivalent in RideSafe | Why It's Used Here |
| :--- | :--- | :--- |
| **POJO with Getters/Setters** | `data class Rider(val name: String, ...)` | Automatically generates constructors, `toString()`, `copy()`, and Firebase serialization in 1 line. |
| **Static Methods & Constants** | `companion object { ... }` or Top-Level Functions | Group static factory methods (e.g., `LocationTrackingService.startTracking(...)`). |
| **Callbacks / Listeners** | Kotlin Coroutines & `Flow` (`callbackFlow`) | Avoids "callback hell". Firebase `ValueEventListener` becomes a smooth reactive stream. |
| **Thread / ExecutorService** | `viewModelScope.launch { ... }` | Runs background tasks bound to screen lifecycle, auto-canceling to prevent memory leaks. |
| **XML Layout + `findViewById`** | `@Composable fun LiveMapScreen()` | Declarative UI. Screen updates automatically whenever `uiState` changes. |
| **`RecyclerView` + Adapter** | `LazyColumn { items(riders) { ... } }` | Displays dynamic lists with zero adapter boilerplate. |
| **Background Service** | `LocationTrackingService` (Foreground) | Android throttles background apps unless they run as a Foreground Service with an ongoing notification. |

---

## Continuous Updates with Firebase App Distribution

Whenever you make changes to the app code, you can distribute updates directly to your phone (and group riders) with a single Gradle command:

### 1. One-Time Setup in Firebase Console:
1. Open [Firebase Console](https://console.firebase.google.com/) > `ridesafe-a46dc`.
2. Go to **Release & Monitor** > **App Distribution** > Click **Get started**.
3. Go to the **Testers & Groups** tab, click **Add group** and name it **`riders`**.
4. Add your email (and any group members' emails).

### 2. Pushing New App Updates:
In Android Studio:
- Open the **Terminal** tab at the bottom and run:
  ```powershell
  ./gradlew appDistributionUploadDebug
  ```
- *OR* in the right **Gradle** sidebar: expand `RideSafe > Tasks > app distribution` and double-click `appDistributionUploadDebug`.

### 3. What Happens on Your Phone:
- Testers receive an email with an instant download link for the new build.
- Riders who already have the app open will see a native in-app prompt: *"New version available! Tap to update."*

