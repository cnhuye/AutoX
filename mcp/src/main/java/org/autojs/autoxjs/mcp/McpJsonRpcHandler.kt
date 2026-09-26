package org.autojs.autoxjs.mcp

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.http.HttpStatusCode
import org.autojs.autoxjs.mcp.tool.ToolDefinition
import org.autojs.autoxjs.mcp.tool.ToolRegistry

data class McpServerInfo(
    val name: String,
    val title: String? = null,
    val version: String? = null,
    val description: String? = null
)

data class RpcHttpResponse(
    val status: HttpStatusCode,
    val body: JsonObject? = null
)

class McpJsonRpcHandler(
    private val registry: ToolRegistry,
    private val serverInfoProvider: () -> McpServerInfo,
    private val protocolVersion: String = PROTOCOL_VERSION
) {
    private val gson = Gson()

    suspend fun handleText(body: String): RpcHttpResponse {
        val started = System.nanoTime()
        McpLog.d(McpLog.TAG_RPC, "handleText bodyLen=${body.length}")
        val json = try {
            JsonParser.parseString(body)
        } catch (e: Exception) {
            McpLog.w(McpLog.TAG_RPC, "parse error: ${e.message}")
            return RpcHttpResponse(
                HttpStatusCode.BadRequest,
                errorResponse(null, -32700, "Parse error")
            )
        }
        if (!json.isJsonObject) {
            McpLog.w(McpLog.TAG_RPC, "invalid request: root is not a JSON object")
            return RpcHttpResponse(
                HttpStatusCode.BadRequest,
                errorResponse(null, -32600, "Invalid Request")
            )
        }
        val resp = handleJson(json.asJsonObject)
        McpLog.d(
            McpLog.TAG_RPC,
            "handleText done elapsedMs=${(System.nanoTime() - started) / 1_000_000} status=${resp.status.value}"
        )
        return resp
    }

    suspend fun handleJson(json: JsonObject): RpcHttpResponse {
        val jsonrpc = json.get("jsonrpc")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        if (jsonrpc != "2.0") {
            McpLog.w(McpLog.TAG_RPC, "invalid jsonrpc version=$jsonrpc")
            return RpcHttpResponse(HttpStatusCode.BadRequest, errorResponse(null, -32600, "Invalid Request"))
        }

        val methodElement = json.get("method")
        val idElement = json.get("id")

        if (methodElement == null) {
            val hasResultOrError = json.has("result") || json.has("error")
            return if (hasResultOrError) {
                RpcHttpResponse(HttpStatusCode.Accepted)
            } else {
                McpLog.w(McpLog.TAG_RPC, "invalid request: missing method and no result/error")
                RpcHttpResponse(HttpStatusCode.BadRequest, errorResponse(null, -32600, "Invalid Request"))
            }
        }

        if (!methodElement.isJsonPrimitive || !methodElement.asJsonPrimitive.isString) {
            McpLog.w(McpLog.TAG_RPC, "invalid request: method is not a string")
            return RpcHttpResponse(HttpStatusCode.BadRequest, errorResponse(null, -32600, "Invalid Request"))
        }

        val method = methodElement.asString
        val params = json.get("params")

        if (idElement == null || idElement.isJsonNull) {
            McpLog.i(McpLog.TAG_RPC, "notification method=$method paramsKeys=${params?.asJsonObject?.keySet()?.joinToString(",") ?: "<none>"}")
            handleNotification(method, params)
            return RpcHttpResponse(HttpStatusCode.Accepted)
        }

        val started = System.nanoTime()
        McpLog.i(
            McpLog.TAG_RPC,
            "request method=$method id=$idElement params=${McpLog.formatJson(params)}"
        )
        val response = handleRequest(method, idElement, params)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        val summary = summarize(response)
        McpLog.i(
            McpLog.TAG_RPC,
            "response method=$method id=$idElement elapsedMs=$elapsedMs $summary"
        )
        return RpcHttpResponse(HttpStatusCode.OK, response)
    }

    private fun summarize(response: JsonObject): String {
        if (response.has("error")) {
            val err = response.getAsJsonObject("error")
            return "ok=false code=${err.get("code")?.asString ?: "?"} " +
                "message=${err.get("message")?.asString ?: ""}"
        }
        if (response.has("result")) {
            val result = response.get("result")
            val resultStr = if (result.isJsonObject) result.asJsonObject.keySet().joinToString(",") else result.javaClass.simpleName
            return "ok=true resultKeys=$resultStr"
        }
        return "ok=?"
    }

    private fun handleNotification(method: String, params: JsonElement?) {
        when (method) {
            "notifications/initialized" -> Unit
            else -> Unit
        }
    }

    private suspend fun handleRequest(method: String, id: JsonElement, params: JsonElement?): JsonObject {
        return when (method) {
            "initialize" -> handleInitialize(id, params)
            "ping" -> successResponse(id, JsonObject())
            "tools/list" -> handleToolsList(id, params)
            "tools/call" -> handleToolsCall(id, params)
            else -> errorResponse(id, -32601, "Method not found")
        }
    }

    private fun handleInitialize(id: JsonElement, params: JsonElement?): JsonObject {
        if (params == null || !params.isJsonObject) {
            McpLog.w(McpLog.TAG_RPC, "initialize id=$id invalid params")
            return errorResponse(id, -32602, "Invalid params")
        }
        val clientVersion = params.asJsonObject.get("protocolVersion")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
        val info = serverInfoProvider()
        McpLog.i(
            McpLog.TAG_RPC,
            "initialize id=$id clientVersion=${clientVersion ?: "<none>"} " +
                "serverVersion=${info.version ?: "<unknown>"} serverName=${info.name}"
        )
        val result = JsonObject().apply {
            addProperty("protocolVersion", clientVersion ?: protocolVersion)
            add("capabilities", JsonObject().apply {
                add("tools", JsonObject().apply {
                    addProperty("listChanged", false)
                })
            })
            add("serverInfo", JsonObject().apply {
                addProperty("name", info.name)
                info.title?.let { addProperty("title", it) }
                info.version?.let { addProperty("version", it) }
                info.description?.let { addProperty("description", it) }
            })
        }
        return successResponse(id, result)
    }

    private fun handleToolsList(id: JsonElement, params: JsonElement?): JsonObject {
        if (params != null && !params.isJsonObject) {
            McpLog.w(McpLog.TAG_RPC, "tools/list id=$id invalid params type")
            return errorResponse(id, -32602, "Invalid params")
        }
        val definitions = registry.definitions()
        McpLog.i(McpLog.TAG_RPC, "tools/list id=$id count=${definitions.size} names=${definitions.joinToString(",") { it.name }}")
        val toolsJson = JsonArray()
        registry.definitions().forEach { toolsJson.add(toolToJson(it)) }
        val result = JsonObject().apply {
            add("tools", toolsJson)
        }
        return successResponse(id, result)
    }

    private suspend fun handleToolsCall(id: JsonElement, params: JsonElement?): JsonObject {
        if (params == null || !params.isJsonObject) {
            McpLog.w(McpLog.TAG_RPC, "tools/call id=$id invalid params (not object)")
            return errorResponse(id, -32602, "Invalid params")
        }
        val paramsObj = params.asJsonObject
        val name = paramsObj.get("name")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?: run {
                McpLog.w(McpLog.TAG_RPC, "tools/call id=$id invalid params (missing name)")
                return errorResponse(id, -32602, "Invalid params")
            }
        val args = when {
            paramsObj.get("arguments") == null -> JsonObject()
            paramsObj.get("arguments").isJsonObject -> paramsObj.get("arguments").asJsonObject
            else -> {
                McpLog.w(McpLog.TAG_RPC, "tools/call id=$id name=$name invalid arguments type")
                return errorResponse(id, -32602, "Invalid params")
            }
        }

        // McpTool (registry) already logs the dispatch in detail, so keep this light.
        McpLog.i(McpLog.TAG_RPC, "tools/call id=$id name=$name argsKeys=${args.keySet().joinToString(",")}")
        val response = registry.invoke(name, args)
        McpLog.i(McpLog.TAG_RPC, "tools/call id=$id name=$name ok=${response.ok} error=${response.errorCode}")
        val content = JsonArray()
        val text = if (response.ok) {
            response.data?.let { data ->
                if (data is String) data else gson.toJson(data)
            } ?: "ok"
        } else {
            response.errorMessage ?: response.errorCode ?: "error"
        }
        content.add(JsonObject().apply {
            addProperty("type", "text")
            addProperty("text", text)
        })

        val result = JsonObject().apply {
            add("content", content)
            addProperty("isError", !response.ok)
        }
        return successResponse(id, result)
    }

    private fun toolToJson(definition: ToolDefinition): JsonObject {
        return JsonObject().apply {
            addProperty("name", definition.name)
            definition.title?.let { addProperty("title", it) }
            addProperty("description", definition.description)
            add("inputSchema", definition.inputSchema)
            definition.outputSchema?.let { add("outputSchema", it) }
        }
    }

    private fun successResponse(id: JsonElement, result: JsonObject): JsonObject {
        return JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id)
            add("result", result)
        }
    }

    private fun errorResponse(id: JsonElement?, code: Int, message: String): JsonObject {
        return JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id ?: JsonNull.INSTANCE)
            add("error", JsonObject().apply {
                addProperty("code", code)
                addProperty("message", message)
            })
        }
    }

    companion object {
        const val PROTOCOL_VERSION = "2025-11-25"
        // Kept for backward compatibility; new code should use McpLog.TAG_RPC.
        private const val TAG = McpLog.TAG_RPC
    }
}
