# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile


# ---- Sip Shop R8 rules ----
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Room (entities / DAOs / generated database)
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# App data models (enums are read by name: valueOf / entries lookups)
-keep class com.example.data.model.** { *; }
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# Moshi / Retrofit / OkHttp (not used at runtime yet, keep build warning-free)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * { @com.squareup.moshi.* <methods>; }

# Firebase / Play services
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Kotlin coroutines
-dontwarn kotlinx.coroutines.debug.**

# Strip log calls for a little extra speed
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
