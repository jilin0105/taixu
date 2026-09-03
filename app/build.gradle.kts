import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.io.FileInputStream
import java.util.Properties
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val appVersionName = "0.9.0"
val appVersionCode = 15

// TaiXuDev 双包共存构建开关：GitHub Actions 的 taixudev-build.yml 会注入 TAIXU_DEV_BUILD=1。
// 开启后包名切换为 top.wkbin.taixu.dev、应用名显示 TaiXuDev，
// 与正式版（top.wkbin.taixu）在设备上独立共存、互不覆盖。
val taiXuDevBuild = System.getenv("TAIXU_DEV_BUILD") == "1"

// 低内存降级开关：仅供手机 PRoot 沙箱等小内存环境本地构建 R8 包时启用，
// 会关闭 R8 的 IR 优化阶段以压低内存峰值（裁剪与混淆仍然生效）。
// GitHub Actions 云端构建不设置该变量，产出完整优化强度的 R8 包。
val taiXuDevLowMem = System.getenv("TAIXU_DEV_LOWMEM") == "1"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
}

extensions.configure<ApplicationExtension> {
    namespace = "top.wkbin.taixu"
    resourcePrefix = "taixu_"
    compileSdk = 37
    ndkVersion = "30.0.15729638"

    defaultConfig {
        applicationId = if (taiXuDevBuild) "top.wkbin.taixu.dev" else "top.wkbin.taixu"
        minSdk = 29
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties").takeIf { it.exists() }
        ?: project.file("keystore.properties").takeIf { it.exists() }
    val keystoreProperties = Properties()
    if (keystorePropertiesFile != null) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile != null) {
                val storeFilePath = keystoreProperties.getProperty("storeFile") ?: ""
                val resolvedStoreFile = if (storeFilePath.startsWith("/") || storeFilePath.contains(":\\")) {
                    file(storeFilePath)
                } else {
                    rootProject.file(storeFilePath).takeIf { it.exists() } ?: project.file(storeFilePath)
                }
                storeFile = resolvedStoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // TaiXuDev 构建保持与正式版完全独立的包名（top.wkbin.taixu.dev），
            // 因此不再追加 .debug 后缀，避免产物变成 top.wkbin.taixu.dev.debug。
            // 应用名切换由 src/dev/res 资源覆盖完成（manifest 的 label 引用 @string/taixu_app_name）。
            if (!taiXuDevBuild) {
                applicationIdSuffix = ".debug"
                versionNameSuffix = "-debug"
            }
            manifestPlaceholders["appLabel"] = "太墟 (Debug)"
        }
        release {
            manifestPlaceholders["appLabel"] = "太墟"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // TaiXuDev 自测包追加低内存 R8 规则：关闭 IR 优化阶段以压低内存峰值，
            // 仍保留代码裁剪、标识符混淆与资源压缩（详见 proguard-dev-lowmem.pro）。
            if (taiXuDevBuild && taiXuDevLowMem) {
                proguardFiles("proguard-dev-lowmem.pro")
            }
            val onCi = System.getenv("CI") == "true"
            if (keystorePropertiesFile != null && !onCi) {
                signingConfig = signingConfigs.getByName("release")
            } else if (taiXuDevBuild) {
                // TaiXuDev 的 R8 压缩包在缺少正式发布密钥时改用 debug 密钥签名，
                // 保证 assembleRelease 产物可直接侧载自测（正式发布路径不受影响）。
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // TaiXuDev 自测包在低内存环境（手机 PRoot 沙箱 / 小规格 CI Runner）构建时，
    // lintVitalRelease 会与 minifyReleaseWithR8 抢占同一个 Gradle JVM 堆并触发 OOM。
    // 仅对 dev 构建关闭 release lint；正式发布构建的检查强度保持不变。
    if (taiXuDevBuild) {
        lint {
            checkReleaseBuilds = false
        }
    }

    buildFeatures {
        compose = true
    }

    // TaiXuDev 构建加载 dev 资源覆盖目录：应用名等文案切换为 TaiXuDev 品牌，
    // 与正式版资源（main）物理隔离，互不影响。
    // debug 与 release 两个变体都要覆盖：R8 压缩后的 dev 包同样需要 TaiXuDev 品牌名，
    // 否则 assembleRelease 产物会沿用 main 的“太墟”，与正式版无法从桌面图标区分。
    if (taiXuDevBuild) {
        sourceSets.getByName("debug") { res.srcDir("src/dev/res") }
        sourceSets.getByName("release") { res.srcDir("src/dev/res") }
    }

    packaging {
        jniLibs {
            // PRoot is launched as an extracted ARM64 executable on Android 10+.
            useLegacyPackaging = true
            // TaiXu only supports arm64-v8a; Android AARs may also publish legacy/x86 ABIs.
            excludes += listOf(
                "**/armeabi-v7a/*.so",
                "**/x86/*.so",
                "**/x86_64/*.so",
            )
            // The PRoot tracee loader is an executable payload, not a JNI library.
            // Preserve the official package bytes instead of running AGP's strip tool.
            keepDebugSymbols += "**/libproot-loader.so"
        }
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/license.txt",
                "META-INF/notice.txt"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Baseline Profile 运行时安装器：首帧前将打包进 APK 的 profile 提交给 ART 预编译。
    implementation(libs.androidx.profileinstaller)
    // 生成者模块：generateBaselineProfile 时由此拉起 macrobenchmark 采集
    baselineProfile(project(":baselineprofile"))
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(project(":core:datastore"))
    implementation(project(":runtime"))
    implementation(project(":tools"))
    implementation(project(":harness"))
    implementation(project(":feature:components"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:workspace"))
    implementation(project(":feature:navigation"))
    implementation(project(":feature:custom_iteration"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:theme"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.okhttp)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    // Android must use the AAR; the default JVM JAR does not package Android JNI libraries.
    implementation(libs.zstd) {
        artifact { type = "aar" }
    }
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

val bundledProot = layout.projectDirectory.file(
    "src/main/jniLibs/arm64-v8a/libproot.so",
)
val bundledProotLoader = layout.projectDirectory.file(
    "src/main/jniLibs/arm64-v8a/libproot-loader.so",
)
val bundledPtyNative = layout.projectDirectory.file(
    "src/main/jniLibs/arm64-v8a/libpty_native.so",
)

tasks.configureEach {
    if (name == "preBuild") {
        dependsOn(rootProject.tasks.named("architectureCheck"))
        doFirst {
            check(bundledProot.asFile.isFile && bundledProot.asFile.length() > 4096L) {
                "Missing ARM64 PRoot tracer. Run tools/prepare-proot-runtime.ps1 before building."
            }
            check(bundledProotLoader.asFile.isFile && bundledProotLoader.asFile.length() > 4096L) {
                "Missing ARM64 PRoot loader. Run tools/prepare-proot-runtime.ps1 before building."
            }
            // libpty_native 必须是 NDK/Bionic 构建：若依赖 glibc 的 libc.so.6，设备上
            // dlopen 必失败并静默回退到 script PTY 路径（PTY 回显问题会随之复发）。
            val ptyNativeBytes = bundledPtyNative.asFile.readBytes()
            val glibcMarker = "libc.so.6".toByteArray()
            check(ptyNativeBytes.size < glibcMarker.size ||
                (0..ptyNativeBytes.size - glibcMarker.size).none { offset ->
                    glibcMarker.indices.all { ptyNativeBytes[offset + it] == glibcMarker[it] }
                }) {
                "libpty_native.so is linked against glibc (libc.so.6). Rebuild it with the NDK " +
                    "aarch64-linux-android clang (see app/src/main/cpp/CMakeLists.txt)."
            }
        }
    }
}

extensions.configure<ApplicationAndroidComponentsExtension> {
    onVariants { variant ->
        val buildAppName = "taixu-v${appVersionName}-${variant.name}.apk"
        variant.outputs.forEach { output ->
            output.outputFileName.set(buildAppName)
        }
    }
}
