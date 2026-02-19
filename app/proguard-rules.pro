# UniFFI/JNA keep rules are provided by the :lib module's consumer-rules.pro
# and are automatically applied to this consumer module during R8/ProGuard.

# JNA references java.awt.* classes for desktop AWT integration that do not
# exist on Android. Suppress R8 missing-class errors for these references.
-dontwarn java.awt.**

# OkHttp platform-specific classes that may not be present on all API levels.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# WorkManager: keep the worker factory and generated initializer.
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker

# SQLCipher: keep native library loader and JNI bindings.
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**
