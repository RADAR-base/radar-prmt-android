# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/andrea/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep public class * implements org.radarbase.android.source.SourceProvider { *; }
-keep class * implements org.radarbase.android.source.SourceManager { *; }
-keep class com.empatica.empalink.delegate.** { *; }
-keep class com.empatica.empalink.** { *; }

-keep interface com.empatica.empalink.delegate.** { *; }
-keep interface com.empatica.empalink.** { *; }

# Native methods: https://www.guardsquare.com/en/products/proguard/manual/examples#native
# note that <methods> means any method
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
    static native <methods>;
}

# Avro
-keep class org.apache.avro.** { *; }
-keep @org.apache.avro.specific.AvroGenerated class * {
    public <fields>;
    public <methods>;
}
-keep class org.codehaus.jackson.** { *; }
-dontwarn org.apache.avro.**
-dontwarn org.codehaus.jackson.map.ext.**

-keep class com.google.firebase.crashlytics.FirebaseCrashlytics {
    static com.google.firebase.crashlytics.FirebaseCrashlytics getInstance();
    void log(java.lang.String);
    void recordException(java.lang.Throwable);
}

# ==== OkHttp3 ==== #
# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**

# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*

# OkHttp platform used only on JVM and when Conscrypt dependency is available.
-dontwarn okhttp3.internal.platform.ConscryptPlatform

-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
