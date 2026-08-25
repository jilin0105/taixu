package top.wkbin.taixu.runtime

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import top.wkbin.taixu.core.common.files.SafeFileTree
import top.wkbin.taixu.core.common.result.AppError
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.common.result.ErrorCode
import top.wkbin.taixu.core.database.WorkspaceRepository
import top.wkbin.taixu.core.database.WorkspaceEntity
import top.wkbin.taixu.runtime.shell.ShellCommand
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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
    REVERSE,
    GENERAL;

    val displayName: String
        get() = when (this) {
            ANDROID -> "Android"
            FLUTTER -> "Flutter"
            REVERSE -> "APK 逆向"
            GENERAL -> "通用"
        }
}

enum class ProjectImportSource {
    LOCAL_ARCHIVE,
    GITHUB,
}

enum class GitTransport {
    HTTP,
    SSH,
}

data class ProjectArchiveSource(
    val uri: String,
    val fileName: String,
)

enum class ProjectTemplate {
    EMPTY,
    ANDROID_COMPOSE,
    FLUTTER,
    APK_REVERSE,
    GIT_IMPORT;

    val displayName: String
        get() = when (this) {
            EMPTY -> "空工程 (Empty)"
            ANDROID_COMPOSE -> "Android (Jetpack Compose)"
            FLUTTER -> "Flutter 跨平台"
            APK_REVERSE -> "APK 逆向"
            GIT_IMPORT -> "从 Git 导入"
        }
}

/**
 * APK 逆向模板的安装包来源：
 * - [FromInstalledApp]：从本机已安装应用提取安装包（applicationInfo.sourceDir）；
 * - [FromFileUri]：通过系统文件管理器（SAF OpenDocument）选择 .apk 文件。
 */
sealed class ApkImportSource {
    data class FromInstalledApp(
        val packageName: String,
        val appLabel: String,
    ) : ApkImportSource()

    data class FromFileUri(
        val uri: String,
        val fileName: String,
    ) : ApkImportSource()

    val displayName: String
        get() = when (this) {
            is FromInstalledApp -> "$appLabel ($packageName)"
            is FromFileUri -> fileName
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
    @ApplicationContext private val context: Context,
    private val pathManager: RuntimePathManager,
    private val workspaceDao: WorkspaceRepository,
    private val fileService: WorkspaceFileService,
    private val linuxRuntime: dagger.Lazy<LinuxRuntime>,
) {
    constructor(pathManager: RuntimePathManager, workspaceDao: WorkspaceRepository) : this(
        ContextWrapper(null),
        pathManager,
        workspaceDao,
        WorkspaceFileService(pathManager, workspaceDao),
        object : dagger.Lazy<LinuxRuntime> {
            override fun get(): LinuxRuntime = error("Linux runtime is unavailable in this test constructor")
        },
    )
    fun observeProjects(): Flow<List<WorkspaceProject>> = workspaceDao.observeAll().map { entities ->
        val projectPaths = entities.mapNotNull { runCatching { File(it.path).canonicalPath }.getOrNull() }.toSet()
        val filtered = entities.filter { entity ->
            val entityCanonical = runCatching { File(entity.path).canonicalPath }.getOrNull() ?: return@filter true
            projectPaths.none { otherPath ->
                otherPath != entityCanonical && otherPath.startsWith(entityCanonical + File.separator)
            }
        }
        filtered.mapNotNull(::projectFromEntity)
    }.flowOn(Dispatchers.IO)

    suspend fun listProjects(): List<WorkspaceProject> = withContext(Dispatchers.IO) {
        pathManager.workspaceDir.mkdirs()
        // 自动播种内置开箱即用示例工程 (android-demo, flutter-demo)
        top.wkbin.taixu.runtime.samples.WorkspaceSampleSeeder.ensureBuiltinSamples(context, pathManager.workspaceDir, workspaceDao)
        // 目录为准；缺失的目录从 Room 补录
        val known = workspaceDao.listAll().associateBy { it.name }
        val knownPaths = known.values.mapNotNull { runCatching { File(it.path).canonicalPath }.getOrNull() }.toSet()
        val directories = pathManager.workspaceDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory && isValidProjectName(it.name) && !File(it, UNLINKED_MARKER).exists() }
        directories.forEach { directory ->
            if (directory.name !in known && directory.canonicalPath !in knownPaths) {
                workspaceDao.upsert(
                    WorkspaceEntity(directory.name, directory.absolutePath, System.currentTimeMillis()),
                )
            }
        }
        // 先收集所有有效实体，再过滤掉作为其他项目父目录的实体
        // （避免嵌套路径创建项目时，父目录被误注册为独立项目）
        val allEntities = workspaceDao.listAll().filter { it.name in known || File(it.path).isDirectory }
        val projectPaths = allEntities.mapNotNull { runCatching { File(it.path).canonicalPath }.getOrNull() }.toSet()
        val filtered = allEntities.filter { entity ->
            val entityCanonical = runCatching { File(entity.path).canonicalPath }.getOrNull() ?: return@filter true
            // 如果存在其他项目的路径以此实体路径为前缀，则此实体是父目录，应过滤掉
            projectPaths.none { otherPath ->
                otherPath != entityCanonical && otherPath.startsWith(entityCanonical + File.separator)
            }
        }
        filtered
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
        apkSource: ApkImportSource? = null,
        exportApkToDownload: Boolean = false,
        gitUrl: String = "",
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
            if (template == ProjectTemplate.GIT_IMPORT) {
                require(isValidGitUrl(gitUrl)) { "Git 仓库地址必须是 HTTPS、SSH 或 git@ 地址" }
                require(!existed || directory.isDirectory) { "Git 导入目标不是目录" }
                require(!existed || directory.listFiles().orEmpty().isEmpty()) { "Git 导入目标目录必须为空" }
                if (!existed) require(directory.mkdirs()) { "无法创建 Git 导入目录" }
            } else {
                check((existed && directory.isDirectory) || (!existed && directory.mkdirs())) { "无法创建或访问关联目录" }
                if (template != ProjectTemplate.EMPTY) {
                    check(!existed || directory.listFiles().orEmpty().isEmpty()) { "模板目标目录必须为空" }
                }
            }
            File(directory, UNLINKED_MARKER).delete()

            // 模板初始化处理
            // APK 逆向模板：包名无需用户输入，由导入的安装包决定（无则留空）
            var effectivePackage = ""
            if (template == ProjectTemplate.ANDROID_COMPOSE || template == ProjectTemplate.FLUTTER) {
                val cleanPkg = packageName.trim().ifBlank { "com.example.${safeName.lowercase().filter { it.isLetterOrDigit() }}" }
                require(PACKAGE_NAME.matches(cleanPkg)) { "包名必须是合法的 Java/Kotlin 包名：$cleanPkg" }
                effectivePackage = cleanPkg
            }
            when (template) {
                ProjectTemplate.ANDROID_COMPOSE -> copyAndroidTemplate(directory, safeName, effectivePackage)
                ProjectTemplate.FLUTTER -> copyFlutterTemplate(directory, safeName, effectivePackage)
                ProjectTemplate.APK_REVERSE -> {
                    val imported = importApkForReverse(directory, safeName, apkSource)
                    effectivePackage = imported.packageName
                    if (exportApkToDownload) {
                        exportApkToDownload(imported.apkFileName, directory, safeName)
                    }
                }
                ProjectTemplate.GIT_IMPORT -> cloneGitRepository(directory, gitUrl, cleanupOnFailure = !existed)
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

    /**
     * Imports a ZIP project archive into an internal sandbox directory and records the user-selected
     * project label. Extraction always happens under /workspace and rejects path traversal entries.
     */
    suspend fun importProjectArchive(
        name: String,
        directoryPath: String = "",
        projectType: ProjectType,
        source: ProjectArchiveSource,
    ): AppResult<WorkspaceProject> = withContext(Dispatchers.IO) {
        importProject(name, directoryPath, projectType, ProjectImportSource.LOCAL_ARCHIVE) { directory, cleanupOnFailure ->
            try {
                extractProjectArchive(source, directory)
            } catch (throwable: Throwable) {
                if (cleanupOnFailure) SafeFileTree.delete(directory)
                throw throwable
            }
        }
    }

    /** Imports a GitHub repository over the explicitly selected HTTP(S) or SSH transport. */
    suspend fun importGithubProject(
        name: String,
        directoryPath: String = "",
        projectType: ProjectType,
        gitUrl: String,
        transport: GitTransport,
    ): AppResult<WorkspaceProject> = withContext(Dispatchers.IO) {
        importProject(name, directoryPath, projectType, ProjectImportSource.GITHUB) { directory, cleanupOnFailure ->
            require(isValidGitUrlForTransport(gitUrl, transport)) {
                when (transport) {
                    GitTransport.HTTP -> "HTTP 地址必须以 http:// 或 https:// 开头"
                    GitTransport.SSH -> "SSH 地址必须使用 ssh:// 或 git@host:path 格式"
                }
            }
            cloneGitRepository(directory, gitUrl, cleanupOnFailure)
        }
    }

    /** Compresses all regular project files and exports the ZIP into a SAF-selected local directory. */
    suspend fun exportProject(name: String, targetTreeUri: String): AppResult<String> = withContext(Dispatchers.IO) {
        try {
            require(isValidProjectName(name)) { "项目名称无效" }
            val entity = workspaceDao.findByName(name) ?: error("项目不存在：$name")
            val projectDir = File(entity.path).canonicalFile
            check(projectDir.isDirectory) { "项目目录不存在：$name" }

            val treeUri = Uri.parse(targetTreeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            val fileName = "$name-${System.currentTimeMillis()}.zip"
            val outputUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                "application/zip",
                fileName,
            ) ?: error("无法在所选目录创建导出文件")
            val output = context.contentResolver.openOutputStream(outputUri, "w")
                ?: error("无法写入导出文件")
            output.use { stream -> writeProjectZip(projectDir, stream) }
            AppResult.Success(fileName)
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "导出项目失败", throwable))
        }
    }

    suspend fun deleteProject(name: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            require(isValidProjectName(name)) { "项目名称无效" }
            val entity = workspaceDao.findByName(name) ?: error("项目不存在：$name")
            val directory = File(entity.path)
            if (entity.ownsDirectory && directory.exists()) {
                SafeFileTree.delete(directory)
            } else if (directory.isDirectory) {
                File(directory, UNLINKED_MARKER).writeText("unlinkedAt=${System.currentTimeMillis()}\n")
            }
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
        fileService.listFiles(projectName, relativePath)

    /** 读取文件内容（UTF-8，限制单文件最大读取大小）。 */
    suspend fun readFile(projectName: String, relativePath: String): AppResult<String> =
        fileService.readFile(projectName, relativePath)

    /** 写入文件内容（原子临时文件替换）。 */
    suspend fun writeFile(projectName: String, relativePath: String, content: String): AppResult<Unit> =
        fileService.writeFile(projectName, relativePath, content)

    /** 创建新文件（空文件）。 */
    suspend fun createFile(projectName: String, relativePath: String): AppResult<Unit> =
        fileService.createFile(projectName, relativePath)

    /** 创建新目录。 */
    suspend fun createDirectory(projectName: String, relativePath: String): AppResult<Unit> =
        fileService.createDirectory(projectName, relativePath)

    /** 重命名文件或目录。 */
    suspend fun renameItem(projectName: String, oldRelativePath: String, newName: String): AppResult<Unit> =
        fileService.renameItem(projectName, oldRelativePath, newName)

    /** 删除文件或目录。 */
    suspend fun deleteItem(projectName: String, relativePath: String): AppResult<Unit> =
        fileService.deleteItem(projectName, relativePath)

    private fun isInside(root: File, candidate: File): Boolean =
        candidate.absolutePath == root.absolutePath ||
            candidate.absolutePath.startsWith(root.absolutePath + File.separator)

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
        readProjectTypeMetadata(directory)?.let { return it }
        return when {
            File(directory, "pubspec.yaml").exists() -> ProjectType.FLUTTER
            File(directory, "settings.gradle.kts").exists() ||
                File(directory, "app/build.gradle.kts").exists() ||
                File(directory, "build.gradle").exists() -> ProjectType.ANDROID
            // APK 逆向工程：优先使用导入元数据标记，再兼容 .apk/解包目录
            File(directory, "apk-info.properties").isFile ||
                directory.listFiles().orEmpty().any { it.isFile && it.extension.equals("apk", ignoreCase = true) } ||
                (File(directory, "unpacked").isDirectory && File(directory, "unpacked").listFiles().orEmpty()
                    .any { it.isFile && it.name.startsWith("classes") && it.extension == "dex" }) -> ProjectType.REVERSE
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
                ProjectType.REVERSE -> {
                    val info = File(directory, "apk-info.properties").takeIf { it.exists() }?.readText().orEmpty()
                    Regex("""packageName\s*=\s*(.+)""").find(info)?.groupValues?.get(1)?.trim() ?: ""
                }
                ProjectType.GENERAL -> ""
            }
        }.getOrDefault("")
    }

    private fun generateAndroidTemplate(projectDir: File, name: String, packageName: String) {
        val kotlinDisplayName = name.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
        projectDir.mkdirs()
        // 1. settings.gradle.kts
        File(projectDir, "settings.gradle.kts").writeText(
            """
                import org.gradle.api.initialization.resolve.RepositoriesMode

                pluginManagement {
                     repositories {
                         maven("https://maven.aliyun.com/repository/google")
                         maven("https://maven.aliyun.com/repository/gradle-plugin")
                         maven("https://maven.aliyun.com/repository/central")
                         maven("https://maven.aliyun.com/repository/public")
                         google()
                         mavenCentral()
                         gradlePluginPortal()
                     }
                 }
                 dependencyResolutionManagement {
                     repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                     repositories {
                         maven("https://maven.aliyun.com/repository/google")
                         maven("https://maven.aliyun.com/repository/public")
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
                id("com.android.application") version "8.11.1" apply false
                id("org.jetbrains.kotlin.android") version "2.2.20" apply false
                id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
            }
            """.trimIndent()
        )

        // 3. gradle.properties
        File(projectDir, "gradle.properties").writeText(
            """
            org.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC -Dfile.encoding=UTF-8
            org.gradle.daemon=false
            org.gradle.parallel=false
            org.gradle.workers.max=2
            org.gradle.caching=true
            kotlin.daemon.jvmargs=-Xmx512m -XX:MaxMetaspaceSize=256m
            android.useAndroidX=true
            android.nonTransitiveRClass=true
            android.builder.sdkDownload=false
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
                    ndk {
                        abiFilters += "arm64-v8a"
                    }
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
                        android:name="$packageName.MainActivity"
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
                                val countState = remember { mutableStateOf(0) }
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "$kotlinDisplayName",
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "点击次数: ${'$'}{countState.value}",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(onClick = { countState.value++ }) {
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
        // 7. gradle/wrapper/gradle-wrapper.properties (配置国内腾讯云 Gradle 镜像)
        val wrapperDir = File(projectDir, "gradle/wrapper").apply { mkdirs() }
        File(wrapperDir, "gradle-wrapper.properties").writeText(
            """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.14.2-bin.zip
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
            """.trimIndent()
        )

        // 8. gradlew 自适应启动脚本
        val gradlewFile = File(projectDir, "gradlew")
        gradlewFile.writeText(
            """
            #!/bin/sh
            DIR="$(cd "$(dirname "${'$'}0")" && pwd)"
            if [ -f "${'$'}DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
                exec java -jar "${'$'}DIR/gradle/wrapper/gradle-wrapper.jar" "${'$'}@"
            elif [ -x /opt/gradle-8.14.2/bin/gradle ]; then
                exec /opt/gradle-8.14.2/bin/gradle "${'$'}@"
            elif [ -x /opt/gradle-8.7/bin/gradle ]; then
                exec /opt/gradle-8.7/bin/gradle "${'$'}@"
            elif [ -x /usr/local/bin/gradle ]; then
                exec /usr/local/bin/gradle "${'$'}@"
            elif command -v gradle >/dev/null 2>&1; then
                exec gradle "${'$'}@"
            else
                exec /usr/bin/gradle "${'$'}@"
            fi
            """.trimIndent()
        )
        runCatching { gradlewFile.setExecutable(true) }
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

        val packagePath = packageName.replace('.', '/')
        val androidDir = File(projectDir, "android").apply { mkdirs() }
        File(androidDir, "settings.gradle").writeText(
            """
            pluginManagement {
                def flutterProperties = new Properties()
                file("local.properties").withInputStream { flutterProperties.load(it) }
                def flutterSdkPath = flutterProperties.getProperty("flutter.sdk", "/opt/flutter")
                includeBuild("${'$'}{flutterSdkPath}/packages/flutter_tools/gradle")
                repositories { google(); mavenCentral(); gradlePluginPortal() }
            }
            plugins {
                id "dev.flutter.flutter-plugin-loader" version "1.0.0"
                id "com.android.application" version "8.11.1" apply false
                id "org.jetbrains.kotlin.android" version "2.2.20" apply false
            }
            rootProject.name = "$cleanName"
            include ":app"
            """.trimIndent()
        )
        File(androidDir, "build.gradle").writeText(
            """
            allprojects { repositories { google(); mavenCentral() } }
            tasks.register("clean", Delete) { delete rootProject.layout.buildDirectory }
            """.trimIndent()
        )
        val androidAppDir = File(androidDir, "app").apply { mkdirs() }
        File(androidAppDir, "build.gradle").writeText(
            """
            plugins {
                id "com.android.application"
                id "org.jetbrains.kotlin.android"
                id "dev.flutter.flutter-gradle-plugin"
            }
            android {
                namespace "$packageName"
                compileSdk 34
                defaultConfig {
                    applicationId "$packageName"
                    minSdk 21
                    targetSdk 34
                    versionCode 1
                    versionName "1.0"
                    ndk {
                        abiFilters "arm64-v8a"
                    }
                }
                compileOptions {
                    sourceCompatibility JavaVersion.VERSION_17
                    targetCompatibility JavaVersion.VERSION_17
                }
            }
            flutter { source "../.." }
            """.trimIndent()
        )
        val androidMainDir = File(androidAppDir, "src/main").apply { mkdirs() }
        File(androidMainDir, "AndroidManifest.xml").writeText(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:label="$name">
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <meta-data android:name="flutterEmbedding" android:value="2" />
                </application>
            </manifest>
            """.trimIndent()
        )
        val kotlinDir = File(androidMainDir, "kotlin/$packagePath").apply { mkdirs() }
        File(kotlinDir, "MainActivity.kt").writeText(
            """
            package $packageName

            import io.flutter.embedding.android.FlutterActivity

            class MainActivity : FlutterActivity()
            """.trimIndent()
        )
    }

    /** Materializes the checked-in template and expands package directories. */
    private fun copyAndroidTemplate(projectDir: File, name: String, packageName: String) {
        val packagePath = packageName.replace('.', '/')
        val replacements = mapOf(
            "{{projectName}}" to name,
            "{{appName}}" to name,
            "{{packageName}}" to packageName,
            "{{packagePath}}" to packagePath,
        )
        fun visit(assetPath: String, relativePath: String) {
            val children = context.assets.list(assetPath).orEmpty()
            if (children.isNotEmpty()) {
                children.forEach { child ->
                    visit("$assetPath/$child", if (relativePath.isBlank()) child else "$relativePath/$child")
                }
                return
            }
            val outputPath = when (relativePath) {
                "app/src/main/java/MainActivity.kt.template" ->
                    "app/src/main/java/$packagePath/MainActivity.kt"
                else -> relativePath
                    .replace("__PACKAGE_PATH__", packagePath)
                    .removeSuffix(".template")
            }
            val target = File(projectDir, outputPath).canonicalFile
            check(isInside(projectDir.canonicalFile, target)) { "模板路径越界：$relativePath" }
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                val bytes = input.readBytes()
                if (isTemplateTextFile(target.name)) {
                    var text = bytes.toString(Charsets.UTF_8)
                    replacements.forEach { (token, value) -> text = text.replace(token, value) }
                    target.writeText(text, Charsets.UTF_8)
                } else {
                    target.writeBytes(bytes)
                }
            }
        }
        runCatching { visit("templates/android-compose", "") }.getOrElse {
            // Compatibility for unit-test contexts without an AssetManager.
            generateAndroidTemplate(projectDir, name, packageName)
        }
        // Some Android AssetManager/resource-packaging versions do not enumerate
        // placeholder-named directories reliably. Materialize the launcher source
        // directly when recursive enumeration skipped it.
        val expectedSource = File(projectDir, "app/src/main/java/$packagePath/MainActivity.kt")
        if (!expectedSource.isFile) {
            runCatching {
                expectedSource.parentFile?.mkdirs()
                context.assets.open("templates/android-compose/app/src/main/java/MainActivity.kt.template").use { input ->
                    var text = input.readBytes().toString(Charsets.UTF_8)
                    replacements.forEach { (token, value) -> text = text.replace(token, value) }
                    expectedSource.writeText(text, Charsets.UTF_8)
                }
            }.getOrElse {
                generateAndroidTemplate(projectDir, name, packageName)
            }
        }
        validateAndroidTemplate(projectDir, packageName)
    }

    /** Fail during project creation when template expansion produced an unusable launcher. */
    private fun validateAndroidTemplate(projectDir: File, packageName: String) {
        val packagePath = packageName.replace('.', File.separatorChar)
        val source = File(projectDir, "app/src/main/java/$packagePath/MainActivity.kt")
        check(source.isFile) { "Android 模板缺少 MainActivity.kt：${source.absolutePath}" }
        val sourceText = source.readText(Charsets.UTF_8)
        check(Regex("(?m)^\\s*package\\s+${Regex.escape(packageName)}\\s*$").containsMatchIn(sourceText)) {
            "Android 模板包名替换失败：MainActivity.kt"
        }
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
        check(manifest.isFile && manifest.readText(Charsets.UTF_8).contains("$packageName.MainActivity")) {
            "Android 模板启动 Activity 配置无效"
        }
        val buildScript = File(projectDir, "app/build.gradle.kts")
        check(buildScript.isFile && buildScript.readText(Charsets.UTF_8).contains("applicationId = \"$packageName\"")) {
            "Android 模板 applicationId 替换失败"
        }
    }

    private fun isTemplateTextFile(name: String): Boolean =
        name.endsWith(".kt") || name.endsWith(".kts") || name.endsWith(".xml") ||
            name.endsWith(".properties") || name.endsWith(".gradle") || name.endsWith(".md") ||
        name.endsWith(".dart") || name == "pubspec.yaml" || name == "analysis_options.yaml"

    private fun isValidGitUrl(url: String): Boolean =
        url.trim().let { value ->
            value.startsWith("https://") || value.startsWith("http://") ||
                value.startsWith("ssh://") || Regex("^[A-Za-z0-9_.-]+@[A-Za-z0-9_.-]+:.+").matches(value)
        }

    private fun isValidGitUrlForTransport(url: String, transport: GitTransport): Boolean =
        url.trim().let { value ->
            when (transport) {
                GitTransport.HTTP -> value.startsWith("https://") || value.startsWith("http://")
                GitTransport.SSH -> value.startsWith("ssh://") ||
                    Regex("^[A-Za-z0-9_.-]+@[A-Za-z0-9_.-]+:.+").matches(value)
            }
        }

    private suspend fun cloneGitRepository(directory: File, url: String, cleanupOnFailure: Boolean) {
        val result = linuxRuntime.get().execute(
            ShellCommand(
                commandLine = "git clone --depth 1 -- ${shellQuote(url.trim())} ${shellQuote(linuxPathFor(directory))}",
                timeoutMs = GIT_CLONE_TIMEOUT_SECONDS * 1_000L,
            ),
        )
        check(result.isSuccess) {
            if (cleanupOnFailure) SafeFileTree.delete(directory)
            val output = (result.stderr + "\n" + result.stdout).trim().takeLast(1200)
            "Git clone 失败：${output.ifBlank { "请确认 Git 已安装、仓库地址和认证配置可用" }}"
        }
        SafeFileTree.delete(File(directory, ".git/hooks"))
    }

    private suspend fun importProject(
        name: String,
        directoryPath: String,
        projectType: ProjectType,
        source: ProjectImportSource,
        materialize: suspend (directory: File, cleanupOnFailure: Boolean) -> Unit,
    ): AppResult<WorkspaceProject> {
        return try {
            val safeName = name.trim()
            require(isValidProjectName(safeName)) { "名称需以文字或数字开头，只能包含文字、数字、点、下划线和短横线" }
            require(safeName != "sdcard") { "sdcard 是系统共享空间保留名称" }
            check(workspaceDao.findByName(safeName) == null) { "项目已存在：$safeName" }
            pathManager.workspaceDir.mkdirs()
            val base = pathManager.workspaceDir.canonicalFile
            check(base.isDirectory || base.mkdirs()) { "内部沙盒目录不可用" }
            val requested = directoryPath.trim().replace('\\', '/').removePrefix("/workspace/").trim('/')
            val relative = requested.ifBlank { safeName }
            require(relative.split('/').none { it.isBlank() || it == "." || it == ".." }) { "关联目录包含无效路径" }
            val directory = File(base, relative).canonicalFile
            check(isInside(base, directory) && directory != base) { "关联目录越界" }
            val duplicate = workspaceDao.listAll().any {
                runCatching { File(it.path).canonicalFile == directory }.getOrDefault(false)
            }
            check(!duplicate) { "该目录已关联其他工程" }
            val existed = directory.exists()
            require(!existed || directory.isDirectory) { "导入目标不是目录" }
            require(!existed || directory.listFiles().orEmpty().isEmpty()) { "导入目标目录必须为空" }
            if (!existed) require(directory.mkdirs()) { "无法创建导入目录" }
            materialize(directory, !existed)
            writeProjectTypeMetadata(directory, projectType, source)
            workspaceDao.upsert(
                WorkspaceEntity(safeName, directory.absolutePath, System.currentTimeMillis(), ownsDirectory = !existed),
            )
            AppResult.Success(projectFromEntity(workspaceDao.findByName(safeName)!!)!!)
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "导入项目失败", throwable))
        }
    }

    private fun extractProjectArchive(source: ProjectArchiveSource, directory: File) {
        require(source.fileName.endsWith(".zip", ignoreCase = true)) { "本地导入目前仅支持 ZIP 项目压缩包" }
        val input = context.contentResolver.openInputStream(Uri.parse(source.uri))
            ?: error("无法读取所选项目压缩包（URI 授权可能已过期，请重新选择）")
        extractProjectArchive(input, source.fileName, directory)
    }

    internal fun extractProjectArchive(input: java.io.InputStream, fileName: String, directory: File) {
        require(fileName.endsWith(".zip", ignoreCase = true)) { "本地导入目前仅支持 ZIP 项目压缩包" }
        val staging = File(directory, IMPORT_STAGING_DIRECTORY).canonicalFile
        check(isInside(directory.canonicalFile, staging)) { "导入暂存目录越界" }
        SafeFileTree.delete(staging)
        check(staging.mkdirs()) { "无法创建导入暂存目录" }
        var entryCount = 0
        var totalBytes = 0L
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    require(entryCount <= MAX_ARCHIVE_ENTRIES) { "压缩包文件数量过多" }
                    val normalizedName = entry.name.replace('\\', '/').trimStart('/')
                    require(normalizedName.isNotBlank()) { "压缩包包含空路径" }
                    require(normalizedName.split('/').none { it == ".." }) { "压缩包包含越界路径：${entry.name}" }
                    val target = File(staging, normalizedName).canonicalFile
                    require(isInside(staging, target) && target != staging) { "压缩包包含越界路径：${entry.name}" }
                    if (entry.isDirectory) {
                        check(target.isDirectory || target.mkdirs()) { "无法创建目录：${entry.name}" }
                    } else {
                        check(target.parentFile?.isDirectory == true || target.parentFile?.mkdirs() == true) {
                            "无法创建目录：${entry.name}"
                        }
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var entryBytes = 0L
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                entryBytes += read
                                totalBytes += read
                                require(entryBytes <= MAX_ARCHIVE_ENTRY_BYTES) { "压缩包单个文件过大：${entry.name}" }
                                require(totalBytes <= MAX_ARCHIVE_TOTAL_BYTES) { "压缩包解压后体积过大" }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            require(entryCount > 0) { "项目压缩包为空" }
            val meaningful = staging.listFiles().orEmpty().filterNot { it.name == "__MACOSX" }
            val contentRoot = meaningful.singleOrNull()?.takeIf { it.isDirectory } ?: staging
            val children = contentRoot.listFiles().orEmpty().filterNot { it.name == "__MACOSX" }
            require(children.isNotEmpty()) { "项目压缩包没有可导入的文件" }
            children.forEach { child ->
                val destination = File(directory, child.name).canonicalFile
                check(isInside(directory.canonicalFile, destination) && !destination.exists()) { "导入文件冲突：${child.name}" }
                check(child.renameTo(destination)) { "无法写入导入文件：${child.name}" }
            }
        } finally {
            SafeFileTree.delete(staging)
        }
    }

    private fun writeProjectZip(projectDir: File, output: java.io.OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            projectDir.walkTopDown()
                .onEnter { !java.nio.file.Files.isSymbolicLink(it.toPath()) }
                .filter { it != projectDir && !java.nio.file.Files.isSymbolicLink(it.toPath()) }
                .forEach { file ->
                    val relative = file.toRelativeString(projectDir).replace(File.separatorChar, '/')
                    if (relative == UNLINKED_MARKER || relative == IMPORT_STAGING_DIRECTORY) return@forEach
                    val entryName = if (file.isDirectory) "$relative/" else relative
                    zip.putNextEntry(ZipEntry(entryName).apply { time = file.lastModified() })
                    if (file.isFile) file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }

    private fun writeProjectTypeMetadata(directory: File, type: ProjectType, source: ProjectImportSource) {
        File(directory, PROJECT_METADATA_FILE).writeText(
            "type=${type.name}\nsource=${source.name}\nimportedAt=${System.currentTimeMillis()}\n",
            Charsets.UTF_8,
        )
    }

    private fun readProjectTypeMetadata(directory: File): ProjectType? = runCatching {
        val metadata = File(directory, PROJECT_METADATA_FILE)
        if (!metadata.isFile) return@runCatching null
        val typeName = metadata.useLines { lines ->
            lines.firstOrNull { it.startsWith("type=") }?.substringAfter("type=")?.trim()
        }
        ProjectType.entries.firstOrNull { it.name == typeName }
    }.getOrNull()

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    /** Materializes the Flutter template, including its Android Gradle host. */
    private fun copyFlutterTemplate(projectDir: File, name: String, packageName: String) {
        val flutterName = name.lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .ifBlank { "flutter_app" }
        val packagePath = packageName.replace('.', '/')
        val replacements = mapOf(
            "{{projectName}}" to name,
            "{{appName}}" to name,
            "{{flutterProjectName}}" to flutterName,
            "{{packageName}}" to packageName,
            "{{packagePath}}" to packagePath,
        )
        fun visit(assetPath: String, relativePath: String) {
            val children = context.assets.list(assetPath).orEmpty()
            if (children.isNotEmpty()) {
                children.forEach { child ->
                    visit("$assetPath/$child", if (relativePath.isBlank()) child else "$relativePath/$child")
                }
                return
            }
            val outputPath = when (relativePath) {
                "android/app/src/main/kotlin/MainActivity.kt.template" ->
                    "android/app/src/main/kotlin/$packagePath/MainActivity.kt"
                else -> relativePath
                    .replace("__PACKAGE_PATH__", packagePath)
                    .removeSuffix(".template")
            }
            val target = File(projectDir, outputPath).canonicalFile
            check(isInside(projectDir.canonicalFile, target)) { "模板路径越界：$relativePath" }
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                val bytes = input.readBytes()
                if (isTemplateTextFile(target.name)) {
                    var text = bytes.toString(Charsets.UTF_8)
                    replacements.forEach { (token, value) -> text = text.replace(token, value) }
                    target.writeText(text, Charsets.UTF_8)
                } else {
                    target.writeBytes(bytes)
                }
            }
        }
        runCatching { visit("templates/flutter", "") }.getOrElse {
            generateFlutterTemplate(projectDir, name, packageName)
        }
        validateFlutterTemplate(projectDir, packageName)
    }

    private fun validateFlutterTemplate(projectDir: File, packageName: String) {
        val packagePath = packageName.replace('.', File.separatorChar)
        val source = File(projectDir, "android/app/src/main/kotlin/$packagePath/MainActivity.kt")
        check(source.isFile) { "Flutter 模板缺少 MainActivity.kt：${source.absolutePath}" }
        check(source.readText(Charsets.UTF_8).lineSequence().firstOrNull()?.trim() == "package $packageName") {
            "Flutter 模板包名替换失败：MainActivity.kt"
        }
        val requiredFiles = listOf(
            File(projectDir, "pubspec.yaml"),
            File(projectDir, "lib/main.dart"),
            File(projectDir, "android/settings.gradle"),
            File(projectDir, "android/app/build.gradle"),
        )
        requiredFiles.forEach { file ->
            check(file.isFile) { "Flutter 模板缺少文件：${file.absolutePath}" }
            check("{{" !in file.readText(Charsets.UTF_8)) { "Flutter 模板变量未完成替换：${file.name}" }
        }
    }

    // ==================== APK 逆向模板 ====================

    private data class ImportedApk(
        val packageName: String,
        val apkFileName: String,
        val sourceLabel: String,
        val sourceKind: String,
    )

    /**
     * APK 逆向模板初始化：把安装包导入工程目录并做第一层"解包"。
     *
     * 产物结构（以工程名 [name] 为例）：
     * ```
     * <project>/
     * ├── <name>.apk            # 原始安装包（可直接交给 jadx / apktool / MT 管理器）
     * ├── unpacked/             # 标准 ZIP 解包产物（dex / res / assets / lib / 二进制 AXML）
     * │   ├── AndroidManifest.xml
     * │   ├── classes.dex
     * │   ├── resources.arsc
     * │   └── ...
     * ├── apk-info.properties   # 来源与元数据（工程包名读取处）
     * └── REVERSE.md            # 逆向工作流指引（jadx / apktool / MCP）
     * ```
     */
    private fun importApkForReverse(
        projectDir: File,
        name: String,
        apkSource: ApkImportSource?,
    ): ImportedApk {
        requireNotNull(apkSource) { "APK 逆向模板必须选择安装包来源（已安装应用或 APK 文件）" }
        projectDir.mkdirs()

        // 1. 解析来源并拷贝安装包到工程目录
        val apkFileName: String
        val sourceLabel: String
        val sourceKind: String
        val packageHint: String
        val apkFile: File
        when (apkSource) {
            is ApkImportSource.FromInstalledApp -> {
                val info = runCatching {
                    context.packageManager.getApplicationInfo(apkSource.packageName, 0)
                }.getOrElse { error("无法读取已安装应用信息：${apkSource.packageName}") }
                val source = File(info.sourceDir)
                require(source.isFile) { "应用安装包不可读：${source.absolutePath}" }
                apkFileName = "${apkSource.packageName}.apk"
                sourceLabel = apkSource.appLabel
                sourceKind = "installed-app"
                packageHint = apkSource.packageName
                apkFile = File(projectDir, apkFileName).canonicalFile
                check(isInside(projectDir.canonicalFile, apkFile)) { "APK 输出路径越界" }
                source.copyTo(apkFile, overwrite = true)
            }
            is ApkImportSource.FromFileUri -> {
                val uri = android.net.Uri.parse(apkSource.uri)
                val safeBase = apkSource.fileName
                    .substringAfterLast('/')
                    .substringAfterLast('\\')
                    .filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
                    .trim('.')
                    .ifBlank { "target" }
                val displayName = if (safeBase.endsWith(".apk", ignoreCase = true)) safeBase else "$safeBase.apk"
                apkFileName = displayName
                sourceLabel = apkSource.displayName.ifBlank { displayName }
                sourceKind = "file-uri"
                packageHint = ""
                apkFile = File(projectDir, apkFileName).canonicalFile
                check(isInside(projectDir.canonicalFile, apkFile)) { "APK 输出路径越界" }
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("无法读取所选 APK 文件（URI 授权可能已过期，请重新选择）")
                input.use { source ->
                    apkFile.outputStream().use { output -> source.copyTo(output) }
                }
            }
        }
        require(apkFile.isFile && apkFile.length() > 0) { "安装包导入失败：$apkFileName" }

        // 2. 标准 ZIP 解包 -> unpacked/
        val unpackedDir = File(projectDir, "unpacked").apply { mkdirs() }
        unpackApk(apkFile, unpackedDir)

        // 3. 写入元数据与逆向工作流指引
        File(projectDir, "apk-info.properties").writeText(
            buildString {
                appendLine("apk=${apkFile.name}")
                appendLine("apkSizeBytes=${apkFile.length()}")
                appendLine("source=$sourceKind")
                appendLine("sourceLabel=$sourceLabel")
                appendLine("packageName=$packageHint")
                appendLine("importedAt=${System.currentTimeMillis()}")
            },
            Charsets.UTF_8,
        )
        writeReverseReadme(projectDir, name, apkFile.name, unpackedDir, sourceLabel)

        return ImportedApk(
            packageName = packageHint,
            apkFileName = apkFileName,
            sourceLabel = sourceLabel,
            sourceKind = sourceKind,
        )
    }

    /** 用标准 ZIP 读取器把 APK 逐条目解包到 [unpackedDir]（防 zip-slip 路径穿越）。 */
    private fun unpackApk(apkFile: File, unpackedDir: File) {
        val unpackedCanonical = unpackedDir.canonicalFile
        java.util.zip.ZipFile(apkFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                val rawName = entry.name.replace('\\', '/')
                // 防 zip-slip：拒绝绝对路径与 .. 穿越
                if (rawName.startsWith("/") || rawName.split('/').any { it == ".." }) return@forEach
                val target = File(unpackedDir, rawName)
                if (!isInside(unpackedCanonical, target.canonicalFile)) return@forEach
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    /** 生成逆向工作流指引 README，衔接太墟内置的 jadx / apktool / 逆向 MCP 能力。 */
    private fun writeReverseReadme(
        projectDir: File,
        name: String,
        apkFileName: String,
        unpackedDir: File,
        sourceLabel: String,
    ) {
        val entryCount = unpackedDir.walkTopDown().count { it.isFile }
        File(projectDir, "REVERSE.md").writeText(
            """
            # $name · APK 逆向工程

            > 来源：$sourceLabel
            > 导入时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}

            ## 工程结构

            | 路径 | 说明 |
            | :--- | :--- |
            | `$apkFileName` | 原始安装包（未改动） |
            | `unpacked/` | 第一层 ZIP 解包产物（$entryCount 个文件）：`classes.dex`、`resources.arsc`、`AndroidManifest.xml`（二进制 AXML）、`res/`、`assets/`、`lib/` 等 |
            | `apk-info.properties` | 来源与元数据 |

            ## 下一步：在太墟终端 / Agent 中继续深挖

            沙箱内已内置逆向工具链（Android & 移动全栈开发套件 或 apktool 套件装配后可用）：

            ```bash
            # 1) DEX -> Java 源码（推荐，可读性最好）
            jadx -d java-src "$apkFileName"

            # 2) 完整解包资源 + Smali（可回编译）
            apktool d "$apkFileName" -o apktool-out
            #   回编译：apktool b apktool-out -o rebuilt.apk

            # 3) 二进制清单解码（配合 apktool 产物）
            #    aapt dump badging "$apkFileName"   # 包名 / 版本 / 权限
            #    aapt dump xmltree "$apkFileName" AndroidManifest.xml
            ```

            Agent 对话中还可启用内置 **Android 逆向 MCP 服务**（`mcp_apktool`，在 MCP 设置中开启）：
            `decode_apk` / `analyze_manifest` / `extract_strings` / `search_smali` / `build_apk` / `sign_apk`。

            ## 分析关注点

            - **AndroidManifest.xml**：四大组件导出状态、权限声明、Application 类
            - **classes.dex**：核心业务逻辑（jadx 反编译后检索 URL / 密钥 / 加解密特征）
            - **lib/**：native .so（可用 IDA / 玄星逆核 SOMCP 深度分析）
            - **assets/** 与 **res/**：内置资源、配置文件、可能存在的加固壳特征

            > 提示：如果打开 `unpacked/AndroidManifest.xml` 是乱码，属正常现象（AXML 二进制格式），
            > 用 `apktool d` 或 `aapt dump xmltree` 解码即可。

            ## 识别加固壳（jadx 打开看不到真实代码时）

            若 `unpacked/classes.dex` 反编译后只有壳的 stub 加载器，说明 APK 被加固。看 `lib/` 下的 so 名最快定位厂商：

            | 特征 so | 加固厂商 |
            | :--- | :--- |
            | `libjiagu.so` / `libjiagu_art.so` | **360 加固**（入口 `com.stub.StubApp`） |
            | `libDexHelper.so` / `libSecShell.so` / `libsecexe.so` | **梆梆（SecNeo/Bangcle）**（入口 `com.secneo.apkwrapper.ApplicationWrapper`） |
            | `libshellx-super*.so` / `libtup.so` / `libexec.so` | **腾讯乐固 / 御安全**（`com.tencent.StubShell`） |
            | `libnesec.so` | **网易易盾**（`com.netease.nis.wrapper`） |
            | `ijiami.ajm` / `libexecmain.so` / `assets/ijm_lib/` | **爱加密**（入口 `s.h.e.l.l.S`） |
            | `libbaiduprotect.so` / `assets/baiduprotect*` | **百度加固** |
            | `libzuma.so` / `assets/qihoo/` | **阿里聚安全** |
            | `libddog.so` / `libchaosvmp.so` | **娜迦（Nagain，VMP 壳）** |
            | `libx3g.so` | **顶像** |
            | `libkwscmm.so` / `libkwsgmain.so` | **几维** |
            | `libnqshield.so` / `libmobisec.so` / `libkiroro.so` | 网秦 / 阿里旧版 / Kiro 等 |

            辅助判据：`assets/` 下的特征文件（`ijiami.dat`、`bangcleplugin/`、`libjiagu*`、`appsealing*`），以及 AndroidManifest 入口 `android:name`。

            ## 遇到加固壳：脱壳指引

            | 壳级别 | 特征 | 脱壳方案 |
            | :--- | :--- | :--- |
            | **一代壳**（整体 dex 加密） | jadx 只能看到 stub | **通用脱壳**：FRIDA-DEXDump（`frida -U -f 包名 -l frida-dexdump.js`）、BlackDex / FullDump（免 root 一键）、MT 管理器脱壳插件 |
            | **二代壳**（方法抽取 / 函数抽取） | 方法体运行时回填 | **主动调用脱壳**：FART / Youpk / 反射大师（定制 ROM 或 Xposed 级框架触发每个方法回填后再 dump） |
            | **VMP 壳**（指令虚拟化，如娜迦 chaosvmp） | 代码被虚拟化保护 | 极难整体脱，通常只能**动态调试关键逻辑**（Frida hook / Unidbg 模拟执行） |

            脱壳后处理：dump 出的 `classesN.dex` 可能头部/校验被破坏 → 修复 dex header 后再 `jadx` 反编译；若要改逻辑，多数壳允许在原 APK 对应 smali/so 上 patch 后重打包。
            """.trimIndent() + "\n",
            Charsets.UTF_8,
        )
    }

    /**
     * 把工程内导入的 APK 同步导出到宿主公共下载目录（best-effort，供宿主侧 MT 管理器等外部工具直接读取；
     * Android 11+ 需已授予"所有文件访问"权限，未授权时静默跳过，不影响工程创建）。
     */
    private fun exportApkToDownload(apkFileName: String, projectDir: File, projectName: String) {
        runCatching {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists() && !downloadDir.mkdirs()) return
            val source = File(projectDir, apkFileName)
            if (!source.isFile) return
            source.copyTo(File(downloadDir, "$projectName.apk"), overwrite = true)
        }
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
        private const val UNLINKED_MARKER = ".taixu-unlinked-project"
        private const val PROJECT_METADATA_FILE = ".taixu-project.properties"
        private const val IMPORT_STAGING_DIRECTORY = ".taixu-import-staging"
        private const val MAX_ARCHIVE_ENTRIES = 100_000
        private const val MAX_ARCHIVE_ENTRY_BYTES = 1024L * 1024L * 1024L
        private const val MAX_ARCHIVE_TOTAL_BYTES = 4L * 1024L * 1024L * 1024L
        private const val GIT_CLONE_TIMEOUT_SECONDS = 15 * 60L
        private val PACKAGE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+")
        const val MAX_PROJECT_NAME_LENGTH = 64
        const val MAX_FILE_READ_BYTES = 4 * 1024 * 1024L // 4 MB
        const val MAX_FILE_WRITE_CHARS = 4 * 1024 * 1024 // 4 M 字符
        val SHARED_STORAGE_ROOT: File = File("/storage/emulated/0")
    }
}
