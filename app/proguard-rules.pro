# app module R8/proguard 规则
# 启用 isMinifyEnabled 后生效。
# 核心防护点：kotlinx.serialization 字段混淆、JNI（NativePty/Zstd）、Room 数据库、Shizuku 运行时反射、XZ 解压。

# ---- 1. Kotlinx.Serialization（防字段被混淆导致 JSON 解析字段名错位）----
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod,Signature,*Annotation*
-dontwarn kotlinx.serialization.*
-dontnote kotlinx.serialization.AnnotationsKt

# 保留序列化器与伴生对象
-keepclassmembers,allowoptimization,includedescriptorclasses class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class *$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
    <fields>;
}
# 确保核心数据模型与枚举不被混淆
-keep class top.wkbin.taixu.core.model.** { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- 2. JNI 原生层交互（NativePty 与 Zstandard 压缩库）----
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
# 显式保留 NativePty 完整类名及方法，供 pty_native.c C-JNI 准确链接
-keep class top.wkbin.taixu.runtime.pty.NativePty { *; }
-keep,includedescriptorclasses class com.github.luben.zstd.** { *; }

# ---- 3. Room Database（实体类字段、Dao 接口与数据库抽象类）----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { <fields>; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# ---- 4. Shizuku API（跨进程 Binder / AIDL 与反射）----
-dontwarn rikka.shizuku.**
-dontwarn dev.rikka.shizuku.**
-keep class rikka.shizuku.** { *; }
-keep class dev.rikka.shizuku.** { *; }

# ---- 5. XZ / Tar 压缩与安全模块 ----
-keep class org.tukaani.xz.** { *; }
-dontwarn org.tukaani.xz.**
-keep class top.wkbin.taixu.core.security.** { *; }

# ---- 6. 网络与异步库（OkHttp / Ktor / Coroutines）----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-keep class io.ktor.client.engine.okhttp.** { *; }

# ---- 7. Hilt / Dagger 依赖注入 ----
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# ---- 8. 崩溃溯源：保留源码文件名与行号映射表 ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile