package top.wkbin.taixu.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ToolManifest(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val description: String,
    val dependencies: List<String> = emptyList(),
    val launchType: String = "one_shot",
    val servicePort: Int? = null,
    val servicePath: String = "/",
    val version: String = "0.1.0",
    val latestVersion: String? = null,
    val enabled: Boolean = true,
    val icon: String? = null,
    val homepage: String? = null,
    val publisher: String = "",
    val category: String = "AI_AGENT",
    val architectures: List<String> = listOf("ARM64"),
    val permissions: List<String> = emptyList(),
    val updateStrategy: String = "REINSTALL",
    val installMethod: String = "SCRIPT",
    val installScript: String? = null,
    val uninstallScript: String? = null,
    val launchCommand: String? = null,
    val verifyCommand: String? = null,
    val commandLinks: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
)
