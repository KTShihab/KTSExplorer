# Keep native methods and classes with native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep App Models and Callbacks
-keep class com.kts.fileexplorer.model.** { *; }
-keep class com.kts.fileexplorer.network.** { *; }
-keep class com.kts.fileexplorer.cloud.** { *; }
-keep class com.kts.fileexplorer.util.** { *; }

# Keep SMBJ (SMB Client) & dependencies
-keep class com.hierynomus.** { *; }
-keep interface com.hierynomus.** { *; }
-keep class net.engio.mbassador.** { *; }
-keepattributes *Annotation*

# Keep Bouncy Castle crypto algorithms required for SMB2/3
-keep class org.bouncycastle.crypto.** { *; }

# Keep Junrar (RAR archive decompression)
-keep class com.github.junrar.** { *; }

# Keep Apache Commons Compress & Commons Net (FTP)
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.net.** { *; }

# OkHttp & Okio & dependencies rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.commons.**
