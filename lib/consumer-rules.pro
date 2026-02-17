# UniFFI generated bindings -- keep all generated Kotlin classes and methods
-keep class com.zeroclaw.ffi.** { *; }

# UniFFI generated bindings use the uniffi package namespace
-keep class uniffi.** { *; }

# JNA classes used by UniFFI for native library loading
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class * implements com.sun.jna.Callback { *; }

# Keep native method names for JNI linkage
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep UniFFI error classes (used in exception handling across FFI)
-keepattributes Exceptions,InnerClasses,Signature

# Keep enum values used by UniFFI serialization
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
