package top.wkbin.taixu.harness.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class JsonRpcNotification(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

@Serializable
data class McpInitializeParams(
    val protocolVersion: String = "2024-11-05",
    val capabilities: JsonObject = JsonObject(emptyMap()),
    val clientInfo: McpClientInfo = McpClientInfo("TaiXu-Agent", "1.0.0"),
)

@Serializable
data class McpClientInfo(
    val name: String,
    val version: String,
)

@Serializable
data class McpToolDto(
    val name: String,
    val description: String = "",
    val inputSchema: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class McpToolsListResponse(
    val tools: List<McpToolDto> = emptyList(),
)

@Serializable
data class McpCallToolParams(
    val name: String,
    val arguments: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class McpContentItem(
    val type: String = "text",
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null,
)

@Serializable
data class McpCallToolResult(
    val content: List<McpContentItem> = emptyList(),
    val isError: Boolean = false,
)
