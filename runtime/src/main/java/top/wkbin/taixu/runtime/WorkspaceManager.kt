package top.wkbin.taixu.runtime

import top.wkbin.taixu.core.common.files.SafeFileTree
import top.wkbin.taixu.core.common.result.AppError
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.common.result.ErrorCode
import top.wkbin.taixu.core.database.WorkspaceDao
import top.wkbin.taixu.core.database.WorkspaceEntity
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

enum class ProjectType {
    ANDROID,
    FLUTTER,
    GENERAL;

    val displayName: String
        get() = when (this) {
            ANDROID -> "Android"
            FLUTTER -> "Flutter"
            GENERAL -> "通用"
        }
}

enum class ProjectTemplate {
    EMPTY,
    ANDROID_COMPOSE,
    FLUTTER;

    val displayName: String
        get() = when (this) {
            EMPTY -> "空工程 (Empty)"
            ANDROID_COMPOSE -> "Android (Jetpack Compose)"
            FLUTTER -> "Flutter 跨平台"
        }
}

data class WorkspaceProject(
    val name: String,
    val path: String,
    val linuxPath: String,
    val sizeBytes: Long,
    val ownsDirectory: Boolean = true,
    val projectType: ProjectType = ProjectType.GENERAL,
    val packageName: String = "",
)

enum class WorkspaceStorage { INTERNAL, SHARED }

data class WorkspaceFileItem(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String = "",
)

/** 工作区：目录在 App 私有挂载点，元数据（路径/创建时间）存 Room。 */
@Singleton
class WorkspaceManager @Inject constructor(
    private val pathManager: RuntimePathManager,
    private val workspaceDao: WorkspaceDao,
) {
    fun observeProjects(): Flow<List<WorkspaceProject>> = workspaceDao.observeAll().map { entities ->
        entities.mapNotNull(::projectFromEntity)
    }.flowOn(Dispatchers.IO)

    suspend fun listProjects(): List<WorkspaceProject> = withContext(Dispatchers.IO) {
        pathManager.workspaceDir.mkdirs()
        // 自动播种内置开箱即用示例工程 (android-demo, flutter-demo)
        top.wkbin.taixu.runtime.samples.WorkspaceSampleSeeder.ensureBuiltinSamples(pathManager.workspaceDir, workspaceDao)
        // 目录为准；缺失的目录从 Room 补录
        val known = workspaceDao.listAll().associateBy { it.name }
        val knownPaths = known.values.mapNotNull { runCatching { File(it.path).canonicalPath }.getOrNull() }.toSet()
        val directories = pathManager.workspaceDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory && isValidProjectName(it.name) }
        directories.forEach { directory ->
            if (directory.name !in known && directory.canonicalPath !in knownPaths) {
                workspaceDao.upsert(
                    WorkspaceEntity(directory.name, directory.absolutePath, System.currentTimeMillis()),
                )
            }
        }
        val entities = workspaceDao.listAll().filter { it.name in known || File(it.path).isDirectory }
        entities
            .filter { entity -> File(entity.path).isDirectory }
            .sortedBy { it.name.lowercase() }
            .mapNotNull(::projectFromEntity)
    }

    suspend fun createProject(
        name: String,
        storage: WorkspaceStorage = WorkspaceStorage.INTERNAL,
        directoryPath: String = "",
        template: ProjectTemplate = ProjectTemplate.EMPTY,
        packageName: String = "",
    ): AppResult<WorkspaceProject> = withContext(Dispatchers.IO) {
        try {
            val safeName = name.trim()
            require(isValidProjectName(safeName)) { "名称需以文字或数字开头，只能包含文字、数字、点、下划线和短横线" }
            require(safeName != "sdcard") { "sdcard 是系统共享空间保留名称" }
            check(workspaceDao.findByName(safeName) == null) { "项目已存在：$safeName" }
            pathManager.workspaceDir.mkdirs()
            val base = when (storage) {
                WorkspaceStorage.INTERNAL -> pathManager.workspaceDir
                WorkspaceStorage.SHARED -> SHARED_STORAGE_ROOT
            }
            check(base.isDirectory || base.mkdirs()) { "关联空间不可用：${base.absolutePath}" }
            val prefix = if (storage == WorkspaceStorage.INTERNAL) "/workspace/" else "/sdcard/"
            val requested = directoryPath.trim().replace('\\', '/').removePrefix(prefix).trim('/')
            val relative = requested.ifBlank { safeName }
            require(relative.split('/').none { it.isBlank() || it == "." || it == ".." }) { "关联目录包含无效路径" }
            val directory = File(base, relative).canonicalFile
            check(isInside(base.canonicalFile, directory) && directory != base.canonicalFile) { "关联目录越界" }
            val duplicate = workspaceDao.listAll().any {
                it.name != safeName && runCatching { File(it.path).canonicalFile == directory }.getOrDefault(false)
            }
            check(!duplicate) { "该目录已关联其他工程" }
            val existed = directory.exists()
            check((existed && directory.isDirectory) || (!existed && directory.mkdirs())) { "无法创建或访问关联目录" }

            // 模板初始化处理
            val cleanPkg = packageName.trim().ifBlank { "com.example.${safeName.lowercase().filter { it.isLetterOrDigit() }}" }
            when (template) {
                ProjectTemplate.ANDROID_COMPOSE -> generateAndroidTemplate(directory, safeName, cleanPkg)
                ProjectTemplate.FLUTTER -> generateFlutterTemplate(directory, safeName, cleanPkg)
                ProjectTemplate.EMPTY -> { /* 保持空目录 */ }
            }

            val ownsDirectory = storage == WorkspaceStorage.INTERNAL && !existed
            workspaceDao.upsert(
                WorkspaceEntity(safeName, directory.absolutePath, System.currentTimeMillis(), ownsDirectory),
            )
            AppResult.Success(projectFromEntity(workspaceDao.findByName(safeName)!!)!!)
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "创建项目失败", throwable))
        }
    }

    suspend fun deleteProject(name: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            require(isValidProjectName(name)) { "项目名称无效" }
            val entity = workspaceDao.findByName(name) ?: error("项目不存在：$name")
            val directory = File(entity.path)
            if (entity.ownsDirectory && directory.exists()) SafeFileTree.delete(directory)
            workspaceDao.delete(name)
            AppResult.Success(Unit)
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "删除项目失败", throwable))
        }
    }

    suspend fun linuxWorkingDirectory(name: String): String {
        if (name == "sdcard") return "/sdcard"
        require(isValidProjectName(name)) { "项目名称无效" }
        val entity = workspaceDao.findByName(name) ?: error("项目不存在：$name")
        check(File(entity.path).isDirectory) { "关联目录不存在：$name" }
        return linuxPathFor(File(entity.path))
    }

    /** 会话关联的工作区目录；返回 null 表示不关联。 */
    suspend fun workspaceForName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        return runCatching { linuxWorkingDirectory(name) }.getOrNull()
    }

    // ==================== 项目内文件管理 API ====================

    /** 列出项目指定相对路径下的所有文件与子目录（目录优先排序）。 */
    suspend fun listFiles(projectName: String, relativePath: String = ""): AppResult<List<WorkspaceFileItem>> =
        withContext(Dispatchers.IO) {
            try {
                val directory = resolveInProject(projectName, relativePath)
                val displayPath = displayPath(projectName, relativePath)
                check(directory.isDirectory) { "不是目录：$displayPath" }
                val projectRoot = getProjectRoot(projectName)
                val items = directory.listFiles().orEmpty()
                    .map { file ->
                        val rel = file.toRelativeString(projectRoot).replace(File.separatorChar, '/')
                        WorkspaceFileItem(
                            name = file.name,
                            relativePath = rel,
                            isDirectory = file.isDirectory,
                            sizeBytes = if (file.isFile) file.length() else 0L,
                            lastModified = file.lastModified(),
                            extension = if (file.isFile) file.extension.lowercase() else "",
                        )
                    }
                    .sortedWith(
                        compareBy<WorkspaceFileItem> { !it.isDirectory }
                            .thenBy { it.name.lowercase() },
                    )
                AppResult.Success(items)
            } catch (throwable: Throwable) {
                AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "读取文件列表失败", throwable))
            }
        }

    /** 读取文件内容（UTF-8，限制单文件最大读取大小）。 */
    suspend fun readFile(projectName: String, relativePath: String): AppResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val file = resolveInProject(projectName, relativePath)
                val displayPath = displayPath(projectName, relativePath)
                check(file.isFile) { "不是文件：$displayPath" }
                check(file.length() <= MAX_FILE_READ_BYTES) {
                    "文件过大（${file.length()} 字节，上限 ${MAX_FILE_READ_BYTES / 1024 / 1024} MB）"
                }
                AppResult.Success(file.readText(Charsets.UTF_8))
            } catch (throwable: Throwable) {
                AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "读取文件失败", throwable))
            }
        }

    /** 写入文件内容（原子临时文件替换）。 */
    suspend fun writeFile(projectName: String, relativePath: String, content: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                require(content.length <= MAX_FILE_WRITE_CHARS) {
                    "内容过长（${content.length} 字符，上限 $MAX_FILE_WRITE_CHARS）"
                }
                val file = resolveInProject(projectName, relativePath, allowMissing = true)
                if (file.exists() && file.isDirectory) {
                    throw IllegalArgumentException("目标是目录：${displayPath(projectName, relativePath)}")
                }
                file.parentFile?.mkdirs()
                val temporary = File(file.parentFile, ".${file.name}.tmp-${System.nanoTime()}")
                try {
                    temporary.writeText(content, Charsets.UTF_8)
                    if (!temporary.renameTo(file)) {
                        temporary.copyTo(file, overwrite = true)
                        temporary.delete()
                    }
                } finally {
                    temporary.delete()
                }
                AppResult.Success(Unit)
            } catch (throwable: Throwable) {
                AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "保存文件失败", throwable))
            }
        }

    /** 创建新文件（空文件）。 */
    suspend fun createFile(projectName: String, relativePath: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = resolveInProject(projectName, relativePath, allowMissing = true)
                check(!file.exists()) { "文件已存在：${file.name}" }
                file.parentFile?.mkdirs()
                check(file.createNewFile()) { "无法创建文件" }
                AppResult.Success(Unit)
            } catch (throwable: Throwable) {
                AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "创建文件失败", throwable))
            }
        }

    /** 创建新目录。 */
    suspend fun createDirectory(projectName: String, relativePath: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val dir = resolveInProject(projectName, relativePath, allowMissing = true)
                check(!dir.exists()) { "目录已存在：${dir.name}" }
                check(dir.mkdirs()) { "无法创建目录" }
                AppResult.Success(Unit)
            } catch (throwable: Throwable) {
                AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "创建目录失败", throwable))
            }
        }

    /** 重命名文件或目录。 */
    suspend fun renameItem(projectName: String, oldRelativePath: String, newName: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val safeNewName = newName.trim()
                require(safeNewName.isNotBlank() && !safeNewName.contains('/') && !safeNewName.contains('\\')) {
                    "新名称不合法"
                }
                val file = resolveInProject(projectName, oldRelativePath)
                val target = File(file.parentFile, safeNewName)
                check(!target.exists()) { "目标已存在：$safeNewName" }
                check(file.renameTo(target)) { "重命名失败" }
                AppResult.Success(Unit)
            } catch (throwable: Throwable) {
                AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "重命名失败", throwable))
            }
        }

    /** 删除文件或目录。 */
    suspend fun deleteItem(projectName: String, relativePath: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = resolveInProject(projectName, relativePath)
                val projectRoot = getProjectRoot(projectName)
                check(file.canonicalFile != projectRoot.canonicalFile) {
                    "不能通过此接口删除工作区根目录"
                }
                if (file.isDirectory) {
                    SafeFileTree.delete(file)
                } else {
                    check(file.delete()) { "删除文件失败" }
                }
                AppResult.Success(Unit)
            } catch (throwable: Throwable) {
                AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "删除失败", throwable))
            }
        }

    private suspend fun getProjectRoot(projectName: String): File {
        if (projectName == "sdcard") {
            val sdcard = File("/storage/emulated/0")
            if (sdcard.exists()) return sdcard
        }
        require(isValidProjectName(projectName)) { "项目名称无效：$projectName" }
        val entity = workspaceDao.findByName(projectName) ?: error("项目不存在：$projectName")
        val root = File(entity.path)
        check(root.isDirectory) { "关联目录不存在：$projectName" }
        return root
    }

    /**
     * 安全解析项目内相对路径：
     * - 过滤 `..` 与空段；
     * - 校验最终 canonical path 位于项目根目录内部；
     * - 防范跨工作区与系统越界。
     */
    private suspend fun resolveInProject(projectName: String, relativePath: String, allowMissing: Boolean = false): File {
        val root = getProjectRoot(projectName)
        val rootCanonical = root.canonicalFile
        val trimmed = relativePath.trim().removePrefix("/workspace/$projectName").removePrefix("/sdcard").removePrefix("/")
        val segments = trimmed.split('/', '\\').filter { it.isNotEmpty() && it != "." }
        if (segments.any { it == ".." }) {
            throw IllegalArgumentException("路径包含越界操作符 (..)")
        }
        var candidate = root
        for (segment in segments) {
            candidate = File(candidate, segment)
        }
        if (candidate == root) return root
        val canonical = candidate.canonicalFile
        if (!isInside(rootCanonical, canonical)) {
            throw IllegalArgumentException("路径越界：$relativePath")
        }
        if (!allowMissing && !candidate.exists()) {
            throw IllegalArgumentException("目标不存在：${displayPath(projectName, relativePath)}")
        }
        return candidate
    }

    private fun isInside(root: File, candidate: File): Boolean =
        candidate.absolutePath == root.absolutePath ||
            candidate.absolutePath.startsWith(root.absolutePath + File.separator)

    private suspend fun displayPath(projectName: String, relativePath: String): String =
        if (projectName == "sdcard") "/sdcard/${relativePath.trimStart('/')}"
        else "${linuxPathFor(getProjectRoot(projectName))}/${relativePath.trimStart('/')}"

    private fun projectFromEntity(entity: WorkspaceEntity): WorkspaceProject? {
        val directory = File(entity.path)
        if (!directory.isDirectory) return null
        val type = detectProjectType(directory)
        val pkg = extractPackageName(directory, type)
        return WorkspaceProject(
            name = entity.name,
            path = entity.path,
            linuxPath = linuxPathFor(directory),
            sizeBytes = sizeOf(directory),
            ownsDirectory = entity.ownsDirectory,
            projectType = type,
            packageName = pkg,
        )
    }

    private fun detectProjectType(directory: File): ProjectType {
        return when {
            File(directory, "pubspec.yaml").exists() -> ProjectType.FLUTTER
            File(directory, "settings.gradle.kts").exists() ||
                File(directory, "app/build.gradle.kts").exists() ||
                File(directory, "build.gradle").exists() -> ProjectType.ANDROID
            else -> ProjectType.GENERAL
        }
    }

    private fun extractPackageName(directory: File, type: ProjectType): String {
        return runCatching {
            when (type) {
                ProjectType.ANDROID -> {
                    val appBuild = File(directory, "app/build.gradle.kts").takeIf { it.exists() }
                        ?: File(directory, "app/build.gradle").takeIf { it.exists() }
                    val content = appBuild?.readText()
                    val namespaceMatch = Regex("""(?:namespace|applicationId)\s*=\s*["']([^"']+)["']""").find(content ?: "")
                    namespaceMatch?.groupValues?.get(1) ?: ""
                }
                ProjectType.FLUTTER -> {
                    val pubspec = File(directory, "pubspec.yaml").takeIf { it.exists() }
                    val nameMatch = Regex("""name:\s*([a-zA-Z0-9_]+)""").find(pubspec?.readText() ?: "")
                    nameMatch?.groupValues?.get(1) ?: ""
                }
                ProjectType.GENERAL -> ""
            }
        }.getOrDefault("")
    }

    private fun generateAndroidTemplate(projectDir: File, name: String, packageName: String) {
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
            rootProject.name = "$name"
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

        // 3. gradle.properties
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
                namespace = "$packageName"
                compileSdk = 34

                defaultConfig {
                    applicationId = "$packageName"
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
                    android:label="$name"
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
        val packagePath = packageName.replace('.', '/')
        val javaDir = File(mainDir, "java/$packagePath").apply { mkdirs() }
        File(javaDir, "MainActivity.kt").writeText(
            """
            package $packageName

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
                                        text = "$name",
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "点击次数: ${'$'}count",
                                        style = MaterialTheme.typography.titleLarge
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
            """.trimIndent()
        )
    }

    private fun generateFlutterTemplate(projectDir: File, name: String, packageName: String) {
        projectDir.mkdirs()
        val cleanName = name.lowercase().replace('-', '_').filter { it.isLetterOrDigit() || it == '_' }

        // 1. pubspec.yaml
        File(projectDir, "pubspec.yaml").writeText(
            """
            name: $cleanName
            description: "$name Flutter Application"
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
                  title: '$name',
                  theme: ThemeData(
                    colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
                    useMaterial3: true,
                  ),
                  home: const MyHomePage(title: '$name'),
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
    }

    private fun linuxPathFor(directory: File): String {
        val canonical = directory.canonicalFile
        val internal = pathManager.workspaceDir.canonicalFile
        val shared = SHARED_STORAGE_ROOT.canonicalFile
        return when {
            isInside(internal, canonical) -> "/workspace/${canonical.toRelativeString(internal).replace(File.separatorChar, '/')}"
            isInside(shared, canonical) -> "/sdcard/${canonical.toRelativeString(shared).replace(File.separatorChar, '/')}"
            else -> error("目录不在可关联空间内")
        }.trimEnd('/')
    }

    private fun sizeOf(file: File): Long = file.walkTopDown()
        .onEnter { directory -> !java.nio.file.Files.isSymbolicLink(directory.toPath()) }
        .filter { it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) }
        .sumOf { it.length() }

    private fun isValidProjectName(name: String): Boolean {
        if (name.isEmpty() || name.length > MAX_PROJECT_NAME_LENGTH) return false
        if (!name.first().isLetterOrDigit()) return false
        return name.drop(1).all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
    }

    companion object {
        const val MAX_PROJECT_NAME_LENGTH = 64
        const val MAX_FILE_READ_BYTES = 4 * 1024 * 1024L // 4 MB
        const val MAX_FILE_WRITE_CHARS = 4 * 1024 * 1024 // 4 M 字符
        val SHARED_STORAGE_ROOT: File = File("/storage/emulated/0")
    }
}
