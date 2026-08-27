package top.wkbin.taixu.harness.prompt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromptAssetLoaderTest {
    private lateinit var loader: PromptAssetLoader

    @Before
    fun setUp() {
        loader = PromptAssetLoader(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun allPromptAssetsCanBeOpened() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val paths = context.assets.list("prompts").orEmpty()
        assertTrue(paths.isNotEmpty())
        paths.forEach { path -> assertTrue(loader.read("prompts/$path").isNotBlank()) }
    }

    @Test
    fun requiredVariablesAreRendered() {
        val rendered = loader.render(
            "prompts/workspace_context.md",
            mapOf(
                "WORKSPACE_PATH" to "/workspace/demo",
                "DISTRO_NAME" to "Debian",
                "TOOL_NAMES" to "read, edit",
                "INSTALLED_TOOL_NAMES" to "JDK",
                "TYPE_GUIDANCE" to "general",
            ),
        )
        assertTrue(rendered.contains("/workspace/demo"))
        assertFalse(rendered.contains("{{"))
    }

    @Test
    fun allKnownAssetVariablesCanBeRendered() {
        val variables = mapOf(
            "DISTRO_NAME" to "Debian",
            "PKG_MANAGER" to "apt-get install",
            "ACTIVE_SKILLS" to "",
            "TRIGGER_POLICY" to "manual",
            "ROLE_LIST" to "- assistant",
            "MARKER_TEXT" to "README.md",
            "WORKSPACE_PATH" to "/workspace/demo",
            "TOOL_NAMES" to "read",
            "INSTALLED_TOOL_NAMES" to "JDK",
            "TYPE_GUIDANCE" to "general",
            "ROLE_NAME" to "Assistant",
            "ROLE_ID" to "assistant",
            "TASK_NAME" to "test",
            "WORKSPACE_LINE" to "工作区：/workspace/demo",
            "ROLE_PROMPT" to "be precise",
            "TASK_PROMPT" to "run tests",
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.assets.list("prompts").orEmpty().forEach { path ->
            val rendered = loader.render("prompts/$path", variables)
            assertFalse("Unresolved variable in $path", rendered.contains(Regex("""\{\{[A-Z]""")))
        }
    }

    @Test
    fun missingVariableFailsImmediately() {
        val error = assertThrows(IllegalStateException::class.java) {
            loader.renderTemplate("test.md", "hello {{NAME}}", emptyMap())
        }
        assertTrue(error.message.orEmpty().contains("NAME"))
    }
}
