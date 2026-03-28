# LibreChat Android ProGuard Rules

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.librechat.android.**$$serializer { *; }
-keepclassmembers class com.librechat.android.** { *** Companion; }
-keepclasseswithmembers class com.librechat.android.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep Room entities
-keep class com.librechat.android.core.data.db.entity.** { *; }

# Koin - keep ViewModel constructors for reflection-based instantiation
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
