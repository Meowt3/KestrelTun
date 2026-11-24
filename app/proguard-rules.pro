# Keep JNI bridge for hev-socks5-tunnel (libhev-socks5-tunnel.so)
-keep class hev.htproxy.** { *; }

# Keep Kotlinx Serialization models & generated serializers
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep,includedescriptorclasses class com.flclash.nativevpn.model.** { *; }
-keepclassmembers class com.flclash.nativevpn.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }

# Suppress benign third-party warnings
-dontwarn org.yaml.snakeyaml.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
