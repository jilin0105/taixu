package top.wkbin.taixu.harness

import top.wkbin.taixu.core.model.AgentPlugin
import top.wkbin.taixu.core.model.AgentSkill
import top.wkbin.taixu.core.model.BuiltinPlugins
import top.wkbin.taixu.core.model.BuiltinSkills
import top.wkbin.taixu.core.model.AgentSubagent
import top.wkbin.taixu.core.model.BuiltinSubagents
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSkillPluginSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun testBuiltinSkillsPresets() {
        val presets = BuiltinSkills.presets
        assertTrue(presets.isNotEmpty())
        val encoded = json.encodeToString(presets)
        val decoded = json.decodeFromString<List<AgentSkill>>(encoded)
        assertEquals(presets.size, decoded.size)
        assertEquals("agent_context", decoded.first().id)
        assertNotNull(decoded.find { it.id == "linux_ops" })
    }

    @Test
    fun testBuiltinPluginsPresets() {
        val plugins = BuiltinPlugins.presets
        assertTrue(plugins.isNotEmpty())
        val encoded = json.encodeToString(plugins)
        val decoded = json.decodeFromString<List<AgentPlugin>>(encoded)
        assertEquals(plugins.size, decoded.size)
        assertNotNull(decoded.find { it.id == "proot_health_probe" })
    }

    @Test
    fun testCustomSkillCreationAndSerialization() {
        val custom = AgentSkill(
            id = "custom_1",
            name = "Flutter 移动端专家",
            description = "Flutter & Dart 代码架构",
            systemPrompt = "遵循 Flutter Clean Architecture",
            triggerCommand = "/flutter",
            isBuiltin = false,
        )
        val encoded = json.encodeToString(custom)
        val decoded = json.decodeFromString<AgentSkill>(encoded)
        assertEquals(custom.name, decoded.name)
        assertEquals(custom.triggerCommand, decoded.triggerCommand)
    }

    @Test
    fun testBuiltinSubagentsAreValidAndSerializable() {
        val profiles = BuiltinSubagents.presets
        assertTrue(profiles.isNotEmpty())
        assertEquals(profiles.size, profiles.map { it.id }.distinct().size)
        assertEquals(profiles.indices.toList(), profiles.map { it.sortOrder })
        assertTrue(profiles.all { it.id.matches(Regex("[a-z0-9_-]+")) })
        assertTrue(profiles.all { it.name.isNotBlank() && it.description.isNotBlank() && it.systemPrompt.isNotBlank() })

        val encoded = json.encodeToString(profiles)
        val decoded = json.decodeFromString<List<AgentSubagent>>(encoded)
        assertEquals(profiles, decoded)
    }
}
