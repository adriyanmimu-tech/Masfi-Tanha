# Keep source file and line numbers for readable stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep all application classes and models 100% intact to prevent any R8 runtime issues
# while allowing R8 to strip unused third-party library classes (Guava, Protobuf, OkHttp, etc.)
-keep class com.example.** { *; }
-dontwarn com.example.**

# Media3 / ExoPlayer constructors loaded via reflection
-keepclassmembers class androidx.media3.** {
    <init>(...);
}
-dontwarn androidx.media3.**

# OkHttp & Okio platform warnings
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Firebase & Google Play Services warnings
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Kotlinx Coroutines
-dontwarn kotlinx.coroutines.**
