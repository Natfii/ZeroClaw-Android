# UniFFI/JNA keep rules are provided by the :lib module's consumer-rules.pro
# and are automatically applied to this consumer module during R8/ProGuard.

# JNA references java.awt.* classes for desktop AWT integration that do not
# exist on Android. Suppress R8 missing-class errors for these references.
-dontwarn java.awt.**
