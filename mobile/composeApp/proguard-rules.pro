# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class cz.multio.doklady.**$$serializer { *; }
-keepclassmembers class cz.multio.doklady.** { *** Companion; }
-keepclasseswithmembers class cz.multio.doklady.** { kotlinx.serialization.KSerializer serializer(...); }
# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn okhttp3.internal.platform.**
