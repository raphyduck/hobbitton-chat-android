# Switchboard ProGuard Rules

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.garfiec.librechat.**$$serializer { *; }
-keepclassmembers class com.garfiec.librechat.** { *** Companion; }
-keepclasseswithmembers class com.garfiec.librechat.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Koin - keep ViewModel constructors for reflection-based instantiation
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# Tink (behind EncryptedSharedPreferences, the portal's tokens) references Error Prone's
# annotations, which are compile-time only. They used to reach the classpath through
# LibreChat-era dependencies; since D-079 nothing brings them, and R8 stops on the
# missing classes. Nothing reads them at runtime.
-dontwarn com.google.errorprone.annotations.**

# sherpa-onnx (11/11/2026, the dictation heard on the phone): its native code reads the config
# classes' fields and builds the results through JNI, by name.
-keep class com.k2fsa.sherpa.onnx.** { *; }
