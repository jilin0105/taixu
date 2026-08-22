import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.io.FileInputStream
import java.util.Properties
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val appVersionName = "0.2.0"
val appVersionCode = 2

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
}

extensions.configure<ApplicationExtension> {
    namespace = "top.wkbin.taixu"
    compileSdk = 37
    ndkVersion = "30.0.15729638"

    defaultConfig {
        applicationId = "top.wkbin.taixu"
        minSdk = 29
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=none"
                cFlags += "-Wall"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
            if (keystorePropertiesFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // PRoot is launched as an extracted ARM64 executable on Android 10+.
            useLegacyPackaging = true
            // The PRoot tracee loader is an executable payload, not a JNI library.
            // Preserve the official package bytes instead of running AGP's strip tool.
            keepDebugSymbols += "**/libproot-loader.so"
            // QEMU user-mode is also an executable payload with a .so suffix so
            // Android packages it under the ABI directory.
            keepDebugSymbols += "**/libqemu-x86_64-static.so"
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
    implementation(project(":feature:theme"))

    implementation(libs.androidx.core.ktx)
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
    // The AAR carries Android ARM64 JNI code; the plain JVM JAR does not.
    implementation(libs.zstd)
    implementation(libs.kotlinx.serialization.json.jvm)
    implementation(libs.kotlinx.coroutines.core.jvm)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

val bundledProotLoader = layout.projectDirectory.file(
    "src/main/jniLibs/arm64-v8a/libproot-loader.so",
)
val bundledQemuX86_64 = layout.projectDirectory.file(
    "src/main/jniLibs/arm64-v8a/libqemu-x86_64-static.so",
)

tasks.configureEach {
    if (name == "preBuild") {
        doFirst {
            check(bundledProotLoader.asFile.isFile && bundledProotLoader.asFile.length() > 4096L) {
                "Missing ARM64 PRoot loader. Run tools/prepare-proot-runtime.ps1 before building."
            }
            check(bundledQemuX86_64.asFile.isFile && bundledQemuX86_64.asFile.length() > 1024L * 1024L) {
                "Missing bundled ARM64 qemu-x86_64-static payload."
            }
        }
    }
}

extensions.configure<ApplicationAndroidComponentsExtension> {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("taixu-v${appVersionName}-${variant.name}.apk")
        }
    }
}
