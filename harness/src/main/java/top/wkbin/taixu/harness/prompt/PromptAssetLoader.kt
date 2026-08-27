package top.wkbin.taixu.harness.prompt

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Loads packaged prompt assets and renders their required {{VARIABLES}} strictly. */
@Singleton
class PromptAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun read(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }.trim()

    fun render(path: String, variables: Map<String, String> = emptyMap()): String =
        renderTemplate(path, read(path), variables)

    fun renderTemplate(path: String, template: String, variables: Map<String, String>): String {
        val required = PLACEHOLDER.findAll(template).map { it.groupValues[1] }.toSet()
        val missing = required - variables.keys
        check(missing.isEmpty()) {
            "Prompt asset $path is missing variables: ${missing.sorted().joinToString()}"
        }
        return required.fold(template) { rendered, name ->
            rendered.replace("{{$name}}", variables.getValue(name))
        }.trim()
    }

    private companion object {
        // NOTE: Android 的 java.util.regex 底层是 ICU4C，未转义的 '}' 会抛
        // PatternSyntaxException（标准 JVM 则容忍），因此两侧花括号都必须转义。
        val PLACEHOLDER = Regex("""\{\{([A-Z][A-Z0-9_]*)\}\}""")
    }
}
