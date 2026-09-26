package org.autojs.autoxjs.mcp.tool

import com.google.gson.JsonObject
import org.autojs.autoxjs.mcp.McpLog
import org.autojs.autoxjs.mcp.McpResponse
import java.util.concurrent.ConcurrentHashMap

class ToolRegistry {
    private data class Entry(
        val definition: ToolDefinition?,
        val tool: McpTool
    )

    private val tools = ConcurrentHashMap<String, Entry>()

    fun register(name: String, tool: McpTool) {
        tools[name] = Entry(null, tool)
        McpLog.i(McpLog.TAG_TOOL, "register name=$name (anonymous)")
    }

    fun register(definition: ToolDefinition, tool: McpTool) {
        tools[definition.name] = Entry(definition, tool)
        McpLog.i(McpLog.TAG_TOOL, "register name=${definition.name} title=${definition.title ?: "<none>"}")
    }

    fun names(): Set<String> = tools.keys

    fun definitions(): List<ToolDefinition> {
        return tools.entries.map { (name, entry) ->
            entry.definition ?: ToolDefinition(
                name = name,
                title = null,
                description = "Tool $name",
                inputSchema = ToolSchemas.emptyObject()
            )
        }.sortedBy { it.name }
    }

    suspend fun invoke(name: String, params: JsonObject?): McpResponse {
        val entry = tools[name]
        if (entry == null) {
            McpLog.w(McpLog.TAG_TOOL, "invoke name=$name -> NotFound (registered=${tools.keys.sorted().joinToString(",")})")
            return McpResponse.error("NotFound", "Tool `$name` not registered")
        }
        val started = System.nanoTime()
        McpLog.i(
            McpLog.TAG_TOOL,
            "invoke name=$name args=${McpLog.formatJson(params)}"
        )
        return try {
            val response = entry.tool.handle(params)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            val dataSummary = summarizeData(response.data)
            McpLog.i(
                McpLog.TAG_TOOL,
                "done name=$name ok=${response.ok} elapsedMs=$elapsedMs " +
                    "errorCode=${response.errorCode ?: "<none>"} " +
                    "errorMessage=${response.errorMessage ?: "<none>"} data=$dataSummary"
            )
            response
        } catch (e: Exception) {
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            McpLog.e(McpLog.TAG_TOOL, "exception name=$name elapsedMs=$elapsedMs type=${e.javaClass.name}", e)
            McpResponse.error("Internal", e.message ?: "Internal error")
        }
    }

    private fun summarizeData(data: Any?): String {
        if (data == null) return "null"
        return when (data) {
            is String -> "string(len=${data.length}) preview=\"${McpLog.truncate(data, 64)}\""
            is Map<*, *> -> "map(keys=${data.keys.joinToString(",")},size=${data.size})"
            is List<*> -> "list(size=${data.size})"
            is Number, is Boolean -> "scalar($data)"
            else -> data.javaClass.simpleName
        }
    }
}