package top.wkbin.taixu.template

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class ProjectTemplateEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val templatesDir = File(context.filesDir, "linux-runtime/templates")
    private val json = Json { ignoreUnknownKeys = false }
    private val bundledDirectories: Map<String, String> by lazy {
        discoverBundledDirectories().associateBy { relativePath ->
            val bytes = context.assets.open("templates/$relativePath/${ProjectTemplateManifest.MANIFEST_PATH}")
                .use { it.readBytes() }
            json.decodeFromString<ProjectTemplateManifest>(bytes.toString(Charsets.UTF_8)).id
        }
    }

    fun inspect(id: String): ProjectTemplateManifest {
        val source = resolveSource(id)
        val manifest = json.decodeFromString<ProjectTemplateManifest>(source.read(ProjectTemplateManifest.MANIFEST_PATH).toString(Charsets.UTF_8))
        require(manifest.id == id) { "模板标识不匹配：${manifest.id}" }
        require(manifest.schemaVersion == ProjectTemplateManifest.CURRENT_SCHEMA_VERSION) {
            "不支持的模板规范版本：${manifest.schemaVersion}"
        }
        return manifest
    }

    fun resolvedValues(manifest: ProjectTemplateManifest, supplied: Map<String, String>): Map<String, String> =
        manifest.variables.associate { variable ->
            variable.name to if (variable.name in supplied) supplied.getValue(variable.name) else variable.defaultValue
        } + supplied

    fun materialize(id: String, projectDir: File, values: Map<String, String>): ProjectTemplateManifest {
        val source = resolveSource(id)
        val manifest = inspect(id)
        val resolvedValues = resolvedValues(manifest, values)
        validateVariables(manifest, resolvedValues)

        fun replaceVariables(text: String): String {
            var result = text
            resolvedValues.forEach { (name, value) -> result = result.replace("{{$name}}", value) }
            val unresolved = PLACEHOLDER.find(result)?.value
            require(unresolved == null) { "模板包含未赋值变量：$unresolved" }
            return result
        }

        fun outputPath(relativePath: String): String {
            val compatiblePath = when {
                id == ANDROID_COMPOSE_ID && relativePath == "app/src/main/java/MainActivity.kt.template" ->
                    "app/src/main/java/__PACKAGE_PATH__/MainActivity.kt.template"
                id == FLUTTER_ID && relativePath == "android/app/src/main/kotlin/MainActivity.kt.template" ->
                    "android/app/src/main/kotlin/__PACKAGE_PATH__/MainActivity.kt.template"
                else -> relativePath
            }
            return replaceVariables(
                compatiblePath.replace("__PACKAGE_PATH__", resolvedValues["packagePath"].orEmpty()),
            ).removeSuffix(".template")
        }

        fun write(relativePath: String, bytes: ByteArray) {
            val normalizedPath = relativePath.replace('\\', '/')
            val previewPaths = listOf(manifest.previewImage)
                .filter(String::isNotBlank)
                .map { it.replace('\\', '/') }
                .toSet()
            if (relativePath == ProjectTemplateManifest.MANIFEST_PATH ||
                normalizedPath.startsWith("template-hooks/") ||
                normalizedPath in previewPaths
            ) return
            val target = File(projectDir, outputPath(relativePath)).canonicalFile
            require(target.path.startsWith(projectDir.canonicalPath + File.separator)) { "模板路径越界：$relativePath" }
            target.parentFile?.mkdirs()
            if (isTextFile(relativePath, target.name)) {
                target.writeText(replaceVariables(bytes.toString(Charsets.UTF_8)), Charsets.UTF_8)
            } else target.writeBytes(bytes)
        }

        source.files().forEach { relativePath -> write(relativePath, source.read(relativePath)) }
        validateOutput(id, projectDir, resolvedValues["packageName"].orEmpty())
        return manifest
    }

    fun readHook(id: String, relativePath: String): ByteArray {
        val normalized = relativePath.replace('\\', '/')
        require(!normalized.startsWith('/') && !WINDOWS_ABSOLUTE_PATH.containsMatchIn(normalized) &&
            normalized.startsWith("template-hooks/") && normalized.split('/').none { it == ".." }
        ) {
            "模板脚本必须位于 template-hooks/ 目录"
        }
        return resolveSource(id).read(normalized)
    }

    private fun validateVariables(manifest: ProjectTemplateManifest, values: Map<String, String>) {
        manifest.variables.forEach { variable ->
            val value = values[variable.name].orEmpty()
            require(!variable.required || value.isNotBlank()) { "模板变量不能为空：${variable.label}" }
            if (value.isNotBlank() && variable.validationRegex.isNotBlank()) {
                require(Regex(variable.validationRegex).matches(value)) { "模板变量格式无效：${variable.label}" }
            }
            if (variable.inputType == ProjectTemplateInputType.SELECT && value.isNotBlank()) {
                require(variable.options.any { it.value == value }) { "模板变量选项无效：${variable.label}" }
            }
        }
    }

    private fun resolveSource(id: String): TemplateSource = bundledDirectories[id]?.let { relativePath ->
        AssetTemplateSource("templates/$relativePath")
    } ?: DirectoryTemplateSource(findInstalledDirectory(id))

    private fun discoverBundledDirectories(): List<String> {
        val result = mutableListOf<String>()
        fun visit(relativePath: String) {
            val assetPath = if (relativePath.isBlank()) "templates" else "templates/$relativePath"
            val children = context.assets.list(assetPath).orEmpty()
            if (ProjectTemplateManifest.MANIFEST_PATH in children) {
                result += relativePath
            } else children.forEach { child ->
                val childRelative = if (relativePath.isBlank()) child else "$relativePath/$child"
                if (context.assets.list("templates/$childRelative").orEmpty().isNotEmpty()) visit(childRelative)
            }
        }
        visit("")
        return result
    }

    private fun findInstalledDirectory(id: String): File = templatesDir.walkTopDown()
        .filter(File::isDirectory)
        .firstOrNull { directory ->
            val manifest = File(directory, ProjectTemplateManifest.MANIFEST_PATH)
            manifest.isFile && runCatching {
                json.decodeFromString<ProjectTemplateManifest>(manifest.readText(Charsets.UTF_8)).id == id
            }.getOrDefault(false)
        } ?: error("项目模板不可用：$id")

    private fun validateOutput(id: String, projectDir: File, packageName: String) {
        val packagePath = packageName.replace('.', File.separatorChar)
        when (id) {
            ANDROID_COMPOSE_ID -> require(File(projectDir, "app/src/main/java/$packagePath/MainActivity.kt").isFile) {
                "Android 模板缺少 MainActivity.kt"
            }
            ANDROID_NO_ACTIVITY_ID -> require(
                "<activity" !in File(projectDir, "app/src/main/AndroidManifest.xml").readText(Charsets.UTF_8),
            ) { "No Activity 模板包含了 Activity 启动入口" }
            ANDROID_XPOSED_ID -> {
                require(File(projectDir, "app/src/main/java/$packagePath/MainHook.kt").isFile) {
                    "Xposed 模板缺少 MainHook.kt"
                }
                require(
                    File(projectDir, "app/src/main/assets/xposed_init").readText(Charsets.UTF_8).trim() ==
                        "$packageName.MainHook",
                ) { "Xposed 模板入口声明无效" }
            }
            FLUTTER_ID -> require(File(projectDir, "android/app/src/main/kotlin/$packagePath/MainActivity.kt").isFile) {
                "Flutter 模板缺少 MainActivity.kt"
            }
        }
    }

    private fun isTextFile(relativePath: String, name: String): Boolean = relativePath.endsWith(".template") ||
        name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS || name in TEXT_FILE_NAMES

    private interface TemplateSource {
        fun read(relativePath: String): ByteArray
        fun files(): Sequence<String>
    }

    private inner class AssetTemplateSource(private val root: String) : TemplateSource {
        override fun read(relativePath: String): ByteArray =
            context.assets.open("$root/$relativePath").use { it.readBytes() }

        override fun files(): Sequence<String> = sequence {
            suspend fun SequenceScope<String>.visit(assetPath: String, relativePath: String) {
                val children = context.assets.list(assetPath).orEmpty()
                if (children.isEmpty()) yield(relativePath) else children.forEach { child ->
                    visit("$assetPath/$child", if (relativePath.isBlank()) child else "$relativePath/$child")
                }
            }
            visit(root, "")
        }
    }

    private class DirectoryTemplateSource(private val root: File) : TemplateSource {
        override fun read(relativePath: String): ByteArray {
            val file = File(root, relativePath).canonicalFile
            require(file.path.startsWith(root.canonicalPath + File.separator) && file.isFile) { "模板文件不存在：$relativePath" }
            return file.readBytes()
        }

        override fun files(): Sequence<String> = root.walkTopDown().filter(File::isFile)
            .map { it.relativeTo(root).invariantSeparatorsPath }
    }

    companion object {
        const val ANDROID_COMPOSE_ID = "builtin.android-compose"
        const val ANDROID_NO_ACTIVITY_ID = "builtin.android-no-activity"
        const val ANDROID_XPOSED_ID = "builtin.android-xposed"
        const val FLUTTER_ID = "builtin.flutter"
        private val PLACEHOLDER = Regex("\\{\\{[A-Za-z][A-Za-z0-9_]*\\}\\}")
        private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:/")
        private val TEXT_EXTENSIONS = setOf(
            "c", "cc", "cpp", "css", "dart", "gradle", "h", "hpp", "html", "java", "js", "json",
            "kt", "kts", "md", "properties", "pro", "py", "rs", "sh", "toml", "ts", "txt", "xml",
            "yaml", "yml",
        )
        private val TEXT_FILE_NAMES = setOf(
            "Dockerfile", "Gemfile", "Makefile", "Podfile", "analysis_options.yaml", "gradlew", "pubspec.yaml",
            "xposed_init",
        )
    }
}
