package org.autojs.autoxjs.mcp

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement

/**
 * Centralised logging utilities for the MCP module.
 *
 * Each layer in the MCP stack uses its own independent [android.util.Log] tag so
 * that `adb logcat -s` can filter exactly what the developer needs:
 *
 *  - [TAG_SERVER]       McpServer Ktor engine lifecycle (start/stop)
 *  - [TAG_HTTP]         HTTP request/response handling
 *  - [TAG_RPC]          JSON-RPC method routing
 *  - [TAG_TOOL]         ToolRegistry tool dispatch
 *  - [TAG_JOB]          JobTracker lifecycle
 *  - [TAG_SERVICE]      McpService (MCP engine) start/stop
 *  - [TAG_SERVER_SVC]   McpServerService (foreground Service) lifecycle
 *
 * Sensitive fields (X-Token, base64 image payload, …) are redacted.
 */
object McpLog {

    const val TAG_SERVER = "McpServer"
    const val TAG_HTTP = "McpHttp"
    const val TAG_RPC = "McpRpc"
    const val TAG_TOOL = "McpTool"
    const val TAG_JOB = "McpJob"
    const val TAG_SERVICE = "McpService"
    const val TAG_SERVER_SVC = "McpServerSvc"

    private val gson = Gson()

    /** Maximum length of a logged body / arg value before it is truncated. */
    private const val MAX_BODY_LOG = 256

    /** Fields whose values must never appear in logcat. */
    private val REDACTED_KEYS = setOf(
        "token", "X-Token", "x-token",
        "base64", "image", "screenshot"
    )

    fun v(tag: String, msg: String) { Log.v(tag, msg) }
    fun d(tag: String, msg: String) { Log.d(tag, msg) }
    fun i(tag: String, msg: String) { Log.i(tag, msg) }
    fun w(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg)
    }
    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
    }

    /**
     * Truncate a string to [MAX_BODY_LOG] characters with an appended
     * "...(+N chars)" marker. Returns the input unchanged when short enough.
     */
    fun truncate(value: String, max: Int = MAX_BODY_LOG): String {
        if (value.length <= max) return value
        return value.substring(0, max) + "...(+" + (value.length - max) + " chars)"
    }

    /**
     * Render a [JsonElement] to compact JSON and truncate the result. Sensitive
     * keys are replaced with the literal string "<redacted>" before rendering.
     */
    fun formatJson(value: JsonElement?, max: Int = MAX_BODY_LOG): String {
        if (value == null || value.isJsonNull) return "null"
        val safe = redact(value.deepCopy())
        val raw = gson.toJson(safe)
        return truncate(raw, max)
    }

    /** Mark a value as provided/missing without revealing the actual value. */
    fun secret(value: String?): String = if (value.isNullOrBlank()) "<missing>" else "<set,len=${value.length}>"

    private fun redact(node: JsonElement): JsonElement {
        if (!node.isJsonObject) return node
        val obj = node.asJsonObject
        obj.keySet().forEach { key ->
            if (REDACTED_KEYS.any { it.equals(key, ignoreCase = true) }) {
                obj.addProperty(key, "<redacted>")
            } else {
                obj.add(key, redact(obj.get(key)))
            }
        }
        return obj
    }
}