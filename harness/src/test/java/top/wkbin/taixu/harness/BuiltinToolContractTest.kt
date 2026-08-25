package top.wkbin.taixu.harness

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val search = ProviderClient.TOOLS.single { it.function.name == "history.search" }
        val read = ProviderClient.TOOLS.single { it.function.name == "history.read" }
        assertTrue(search.function.parameters.toString().contains("query"))
        assertTrue(read.function.parameters.toString().contains("message_id"))
        assertTrue(read.function.parameters.toString().contains("index"))
    }
}
