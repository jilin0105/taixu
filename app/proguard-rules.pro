# app module R8/proguard 规则
# 启用 isMinifyEnabled 后生效。核心风险点：kotlinx.serialization 的运行时反射、
# JNI（NativePty/Zstd）、Room 生成代码、assets 中的 registry 清单。

# ---- kotlinx.serialization（官方推荐规则）----
# serializer() 通过方法引用发现，需保留类描述符与注解属性
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod,Signature
-dontwarn kotlinx.serialization.*
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers,allowoptimization,includedescriptorclasses class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class *$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Room（DAO/数据库生成代码直接引用，保留注解成员）----
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# ---- JNI：NativePty / Zstd ----
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep,includedescriptorclasses class com.github.luben.zstd.** { *; }

# ---- okhttp / okio（AAR 已带官方 consumer 规则，此处兜底防哑类）----
-dontwarn okhttp3.*
-dontwarn okio.*

# ---- ktor（显式 engine 反射加载）----
-dontwarn io.ktor.**
-keep class io.ktor.client.engine.okhttp.** { *; }

# ---- Hilt / Dagger（官方规则随依赖内置，兜底保留注入构造器）----
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# ---- 保留崩溃日志可读性：保留来源文件名与行号 ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile