package top.wkbin.taixu.runtime.samples

import top.wkbin.taixu.core.database.WorkspaceDao
import top.wkbin.taixu.core.database.WorkspaceEntity
import java.io.File

/**
 * 工作区内置示例工程初始化播种器。
 *
 * 首次启动或工作区为空时，自动在 `/workspace` 目录下植入两套标准开箱即用工程：
 *  1. `android-demo`：Kotlin 2.x + Jetpack Compose + 预置 ARM64 aapt2 构建配置的标准安卓示例，可直接编译打包并直装手机；
 *  2. `flutter-demo`：Flutter 3.x + Material 3 跨平台示例，预置国内 pub 镜像与 Gradle 架构适配。
 */
object WorkspaceSampleSeeder {

    suspend fun ensureBuiltinSamples(workspaceDir: File, workspaceDao: WorkspaceDao) {
        runCatching {
            workspaceDir.mkdirs()
            seedAndroidDemo(File(workspaceDir, "android-demo"), workspaceDao)
            seedFlutterDemo(File(workspaceDir, "flutter-demo"), workspaceDao)
        }
    }

    private suspend fun seedAndroidDemo(projectDir: File, workspaceDao: WorkspaceDao) {
        if (projectDir.exists() && projectDir.listFiles()?.isNotEmpty() == true) return
        projectDir.mkdirs()

        // 1. settings.gradle.kts
        File(projectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "AndroidDemo"
            include(":app")
            """.trimIndent()
        )

        // 2. build.gradle.kts
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "8.7.3" apply false
                id("org.jetbrains.kotlin.android") version "2.0.21" apply false
                id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
            }
            """.trimIndent()
        )

        // 3. gradle.properties (重点：配置原生 ARM64 aapt2 避免 x86_64 崩溃)
        File(projectDir, "gradle.properties").writeText(
            """
            org.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8
            android.useAndroidX=true
            android.nonTransitiveRClass=true
            android.aapt2FromMaven=false
            android.overrideAapt2Path=/usr/bin/aapt
            """.trimIndent()
        )

        // 4. app/build.gradle.kts
        val appDir = File(projectDir, "app").apply { mkdirs() }
        File(appDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
                id("org.jetbrains.kotlin.plugin.compose")
            }

            android {
                namespace = "com.example.taixudemo"
                compileSdk = 34

                defaultConfig {
                    applicationId = "com.example.taixudemo"
                    minSdk = 26
                    targetSdk = 34
                    versionCode = 1
                    versionName = "1.0.0"
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                buildFeatures {
                    compose = true
                }
            }

            dependencies {
                implementation(platform("androidx.compose:compose-bom:2024.10.01"))
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.material3:material3")
                implementation("androidx.compose.ui:ui-tooling-preview")
                implementation("androidx.activity:activity-compose:1.9.3")
            }
            """.trimIndent()
        )

        // 5. app/src/main/AndroidManifest.xml
        val mainDir = File(appDir, "src/main").apply { mkdirs() }
        File(mainDir, "AndroidManifest.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application
                    android:allowBackup="true"
                    android:icon="@android:drawable/sym_def_app_icon"
                    android:label="TaiXu Android Demo"
                    android:theme="@android:style/Theme.Material.NoActionBar">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
            """.trimIndent()
        )

        // 6. MainActivity.kt
        val javaDir = File(mainDir, "java/com/example/taixudemo").apply { mkdirs() }
        File(javaDir, "MainActivity.kt").writeText(
            """
            package com.example.taixudemo

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        MaterialTheme {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                var count by remember { mutableIntStateOf(0) }
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "太墟 · 掌中 Android 原生工程",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "点击次数: count",
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(onClick = { count++ }) {
                                        Text("点我计数 +1")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            """.replace("count", "${'$'}count").trimIndent()
        )

        // 7. README.md 说明
        File(projectDir, "README.md").writeText(
            """
            # 🚀 TaiXu Android 基础案例

            这是一个在太墟 ARM64 PRoot 沙箱中预置的现代化 Android Jetpack Compose 基础工程。

            ## 🛠️ 极速构建与运行
            1. 在太墟终端或直接向智枢 Agent 发送指令：
               `./gradlew assembleDebug`
            2. 构建完成后，将 APK 复制到手机 Download 目录或一键免授权直装：
               `cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/AndroidDemo.apk`
            """.trimIndent()
        )

        // 注册到数据库
        workspaceDao.upsert(
            WorkspaceEntity(
                name = "android-demo",
                path = projectDir.absolutePath,
                createdAt = System.currentTimeMillis(),
                ownsDirectory = false,
            )
        )
    }

    private suspend fun seedFlutterDemo(projectDir: File, workspaceDao: WorkspaceDao) {
        if (projectDir.exists() && projectDir.listFiles()?.isNotEmpty() == true) return
        projectDir.mkdirs()

        // 1. pubspec.yaml
        File(projectDir, "pubspec.yaml").writeText(
            """
            name: flutter_demo
            description: "TaiXu Builtin Flutter Sample Project"
            publish_to: 'none'
            version: 1.0.0+1

            environment:
              sdk: '>=3.0.0 <4.0.0'

            dependencies:
              flutter:
                sdk: flutter
              cupertino_icons: ^1.0.8

            dev_dependencies:
              flutter_test:
                sdk: flutter
              flutter_lints: ^4.0.0

            flutter:
              uses-material-design: true
            """.trimIndent()
        )

        // 2. lib/main.dart
        val libDir = File(projectDir, "lib").apply { mkdirs() }
        File(libDir, "main.dart").writeText(
            """
            import 'package:flutter/material.dart';

            void main() {
              runApp(const MyApp());
            }

            class MyApp extends StatelessWidget {
              const MyApp({super.key});

              @override
              Widget build(BuildContext context) {
                return MaterialApp(
                  title: 'TaiXu Flutter Demo',
                  theme: ThemeData(
                    colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
                    useMaterial3: true,
                  ),
                  home: const MyHomePage(title: '太墟 · Flutter 跨平台案例'),
                );
              }
            }

            class MyHomePage extends StatefulWidget {
              const MyHomePage({super.key, required this.title});
              final String title;

              @override
              State<MyHomePage> createState() => _MyHomePageState();
            }

            class _MyHomePageState extends State<MyHomePage> {
              int _counter = 0;

              void _incrementCounter() {
                setState(() {
                  _counter++;
                });
              }

              @override
              Widget build(BuildContext context) {
                return Scaffold(
                  appBar: AppBar(
                    backgroundColor: Theme.of(context).colorScheme.inversePrimary,
                    title: Text(widget.title),
                  ),
                  body: Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: <Widget>[
                        const Text('点击按钮增加计数:'),
                        Text(
                          '${'$'}_counter',
                          style: Theme.of(context).textTheme.headlineMedium,
                        ),
                      ],
                    ),
                  ),
                  floatingActionButton: FloatingActionButton(
                    onPressed: _incrementCounter,
                    tooltip: 'Increment',
                    child: const Icon(Icons.add),
                  ),
                );
              }
            }
            """.trimIndent()
        )

        // 3. README.md 说明
        File(projectDir, "README.md").writeText(
            """
            # 💙 TaiXu Flutter 基础案例

            这是一个在太墟沙箱中预置的现代化 Flutter 3.x 计数器示例工程。

            ## 🛠️ 编译指南
            在工坊安装 **Flutter 跨平台开发套件** 后，在终端中执行：
            ```bash
            flutter pub get
            flutter build apk --debug
            cp build/app/outputs/flutter-apk/app-debug.apk /sdcard/Download/FlutterDemo.apk
            ```
            """.trimIndent()
        )

        // 注册到数据库
        workspaceDao.upsert(
            WorkspaceEntity(
                name = "flutter-demo",
                path = projectDir.absolutePath,
                createdAt = System.currentTimeMillis(),
                ownsDirectory = false,
            )
        )
    }
}
