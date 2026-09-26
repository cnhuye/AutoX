package org.autojs.autoxjs.mcp

import android.content.Context
import android.content.pm.PackageInfo
import android.net.Uri
import com.google.gson.Gson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.autojs.autoxjs.mcp.tool.ToolRegistry
import java.net.NetworkInterface

/**
 * Lightweight MCP server wrapper based on Ktor.
 */
class McpServer(
    private val appContext: Context,
    private val registry: ToolRegistry
) {
    private val gson = Gson()
    private var engine: ApplicationEngine? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jsonRpcHandler = McpJsonRpcHandler(
        registry,
        serverInfoProvider = { buildServerInfo() }
    )

    val isRunning: Boolean
        get() = engine?.application?.environment?.monitor != null

    fun start(config: McpConfig) {
        stop()
        if (!config.enabled) {
            McpLog.i(McpLog.TAG_SERVER, "start skipped: config.enabled=false")
            return
        }
        McpLog.i(
            McpLog.TAG_SERVER,
            "starting engine host=${config.host} port=${config.port} " +
                "token=${McpLog.secret(config.token)} allowNetwork=${config.allowNetwork} " +
                "allowBase64=${config.allowBase64}"
        )
        engine = embeddedServer(Netty, port = config.port, host = config.host) {
            install(WebSockets)
            routing {
                post("/mcp") {
                    val started = System.nanoTime()
                    val origin = call.request.headers["Origin"]
                    val tokenHeader = call.request.headers["X-Token"]
                    val remoteHost = call.request.local.remoteHost
                    McpLog.i(
                        McpLog.TAG_HTTP,
                        "POST /mcp from=$remoteHost origin=${origin ?: "<none>"} " +
                            "xToken=${if (tokenHeader.isNullOrBlank()) "<missing>" else "<set,len=${tokenHeader.length}>"}"
                    )

                    if (!isOriginAllowed(config, origin)) {
                        McpLog.w(
                            McpLog.TAG_HTTP,
                            "reject origin=$origin status=403 remote=$remoteHost"
                        )
                        call.respond(HttpStatusCode.Forbidden)
                        return@post
                    }

                    if (!authorize(config, tokenHeader)) {
                        McpLog.w(
                            McpLog.TAG_HTTP,
                            "reject unauthorized remote=$remoteHost status=401"
                        )
                        call.respond(HttpStatusCode.Unauthorized)
                        return@post
                    }

                    val body = call.receiveText()
                    McpLog.d(
                        McpLog.TAG_HTTP,
                        "body received len=${body.length} preview=${McpLog.truncate(body)}"
                    )
                    val response = jsonRpcHandler.handleText(body)
                    val elapsedMs = (System.nanoTime() - started) / 1_000_000
                    if (response.body == null) {
                        McpLog.i(
                            McpLog.TAG_HTTP,
                            "respond status=${response.status.value} elapsedMs=$elapsedMs body=null"
                        )
                        call.respond(response.status)
                    } else {
                        val respJson = gson.toJson(response.body)
                        McpLog.i(
                            McpLog.TAG_HTTP,
                            "respond status=${response.status.value} elapsedMs=$elapsedMs " +
                                "bodyLen=${respJson.length} preview=${McpLog.truncate(respJson, 192)}"
                        )
                        call.respondText(
                            respJson,
                            ContentType.Application.Json,
                            response.status
                        )
                    }
                }

                get("/mcp") {
                    McpLog.i(McpLog.TAG_HTTP, "GET /mcp -> 405 Method Not Allowed")
                    call.respond(HttpStatusCode.MethodNotAllowed)
                }

            }
        }.also { engine -> engine.start(wait = false) }
        McpLog.i(McpLog.TAG_SERVER, "MCP server started on ${config.host}:${config.port}")
    }

    fun stop() {
        McpLog.i(McpLog.TAG_SERVER, "stop requested")
        try {
            engine?.stop(1000, 2000)
            McpLog.i(McpLog.TAG_SERVER, "engine stopped cleanly")
        } catch (e: Exception) {
            McpLog.e(McpLog.TAG_SERVER, "Error stopping server", e)
        } finally {
            engine = null
        }
    }

    private fun authorize(config: McpConfig, tokenHeader: String?): Boolean {
        val expected = config.token
        return expected.isNullOrBlank() || expected == tokenHeader
    }

    private fun isOriginAllowed(config: McpConfig, originHeader: String?): Boolean {
        if (originHeader.isNullOrBlank()) {
            return true
        }
        val host = try {
            Uri.parse(originHeader).host
        } catch (_: Exception) {
            null
        } ?: return false

        val allowedHosts = if (config.allowNetwork) {
            localHosts()
        } else {
            setOf("localhost", "127.0.0.1", "::1")
        }
        return allowedHosts.contains(host)
    }

    private fun localHosts(): Set<String> {
        val hosts = mutableSetOf("localhost", "127.0.0.1", "::1")
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    val host = address.hostAddress?.substringBefore('%')
                    if (!host.isNullOrBlank()) {
                        hosts.add(host)
                    }
                }
            }
        } catch (e: Exception) {
            McpLog.w(McpLog.TAG_SERVER, "Failed to resolve local addresses", e)
        }
        return hosts
    }

    private fun buildServerInfo(): McpServerInfo {
        val name = "AutoX MCP"
        val versionName = try {
            val info: PackageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            info.versionName
        } catch (_: Exception) {
            null
        }
        return McpServerInfo(
            name = name,
            title = name,
            version = versionName,
            description = "AutoX embedded MCP tools server"
        )
    }

    companion object {
        // Kept for backward compatibility; new code should use McpLog.TAG_*.
        private const val TAG = McpLog.TAG_SERVER
    }
}
