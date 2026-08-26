package top.wkbin.taixu.harness

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.core.model.McpToolInfo

class BuiltinToolContractTest {
    @Test
    fun baseExposesBoundedTimeoutOverride() {
        val base = ProviderClient.TOOLS.single { it.function.name == "base" }
        val timeout = base.function.parameters["properties"]
            ?.jsonObject
            ?.get("timeout_seconds")
            ?.jsonObject

        assertNotNull(timeout)
        assertEquals("1", timeout?.get("minimum")?.toString())
        assertEquals("3600", timeout?.get("maximum")?.toString())
    }

    @Test
    fun processExposesManagedLifecycleActions() {
        val process = ProviderClient.TOOLS.single { it.function.name == "process" }
        val encoded = process.function.parameters.toString()

        listOf("start", "status", "logs", "list", "stop").forEach { action ->
            assertTrue(encoded.contains("\"$action\""))
        }
        assertTrue(process.function.description.contains("不要使用 nohup"))
    }

    @Test
    fun historyToolsExposeSearchAndReadContracts() {
        val search = ProviderClient.TOOLS.single { it.function.name == "history_search" }
        val read = ProviderClient.TOOLS.single { it.function.name == "history_read" }
        assertTrue(search.function.parameters.toString().contains("query"))
        assertTrue(read.function.parameters.toString().contains("message_id"))
        assertTrue(read.function.parameters.toString().contains("index"))
    }

    @Test
    fun hostExposesStatusAndPrivilegedExec() {
        val host = ProviderClient.TOOLS.single { it.function.name == "host" }
        val encoded = host.function.parameters.toString()
        assertTrue(encoded.contains("\"status\""))
        listOf("exec", "settings_get", "settings_put", "package_list", "package_disable", "package_enable", "package_uninstall_user", "logcat")
            .forEach { action -> assertTrue(encoded.contains("\"$action\"")) }
        assertTrue(host.function.description.contains("Android"))
    }

    @Test
    fun allProviderFunctionNamesUsePortableCharacters() {
        val unsafeMcp = McpToolInfo(
            serverId = "用户/server.with spaces",
            serverName = "测试 MCP",
            name = "files/read:all?",
            description = "test",
        )
        val names = ProviderClient.buildDynamicTools(listOf(unsafeMcp)).map { it.function.name }

        names.forEach { name ->
            assertTrue("invalid provider function name: $name", name.matches(Regex("^[a-zA-Z0-9_-]+$")))
            assertTrue("provider function name is too long: $name", name.length <= 64)
        }
    }
}
