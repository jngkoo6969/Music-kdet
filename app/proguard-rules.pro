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


# ---- Music app R8 rules ----
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
# Room entities / DAOs / database
-keep class com.example.data.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
# Service declared in the manifest
-keep class com.example.playback.MusicPlaybackService { *; }
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Extra R8 rules ----
# Kotlin metadata / coroutines
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.debug.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
# Moshi (codegen adapters) and Retrofit
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * { @com.squareup.moshi.Json <fields>; }
-keep class **JsonAdapter { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
# Keep enums used by name
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
# App classes used from the manifest / by name
-keep class com.example.MainActivity { *; }
-keep class com.example.**Receiver { *; }
