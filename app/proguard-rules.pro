# RideSafe Proguard Rules
# Keep Firebase Realtime Database models (ensures JSON reflection doesn't strip fields)
-keepclassmembers class com.ridesafe.app.data.model.** {
    <fields>;
    <init>(...);
}
