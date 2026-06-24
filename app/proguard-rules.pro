# ProGuard rules for Media3
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }

# ProGuard rules for Compose
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# Preserve R class for resources
-keep class **.R$* { *; }

# Keep Model classes for serialization if needed
-keep class com.example.data.** { *; }
