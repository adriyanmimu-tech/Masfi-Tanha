# Add project specific ProGuard rules here.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep all app data models, Firestore models, and decryption utilities
-keep class com.example.data.** { *; }
-keep class com.example.player.** { *; }

# Media3 / ExoPlayer HLS, DASH & ClearKey DRM
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Firebase Firestore & Auth
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Kotlinx Coroutines
-dontwarn kotlinx.coroutines.**
