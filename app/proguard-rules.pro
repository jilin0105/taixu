# app module R8/ProGuard rules. Library-specific rules are supplied by each dependency.

# Keep generic type and nesting metadata used by serialization and reflective APIs.
-keepattributes Signature,InnerClasses,EnclosingMethod

# Persisted polymorphic payloads may use their declared serializable class name. Keep only
# those class names while still allowing unused classes/members to shrink and optimize.
-keep,allowshrinking,allowoptimization @kotlinx.serialization.Serializable class top.wkbin.taixu.**

# JNI symbol lookup includes the declaring class and method names. This precise rule also
# covers zstd-jni native entry points without retaining the whole dependency.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class top.wkbin.taixu.runtime.pty.NativePty { *; }

# PrivilegeManager reflectively invokes Shizuku's private compatibility API.
-keepclassmembers,allowoptimization class rikka.shizuku.Shizuku {
    private static *** newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}

# Harness resolves this app-layer service by a constant class name to avoid a module cycle.
-keepnames class top.wkbin.taixu.service.AgentForegroundService

# Keep line information for deobfuscating production crashes while hiding source filenames.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
