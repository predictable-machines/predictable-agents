package predictable.mcp.client

import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.ktor.server.application.serverConfig
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResultBase
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import predictable.mcp.config.MCPConfig
import predictable.mcp.config.MCPServer
import predictable.mcp.config.ServerConfig
import predictable.mcp.resources.MCPResource
import predictable.mcp.tools.MCPTool
import predictable.tool.KotlinSchema
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Client for connecting to and interacting with MCP (Model Context Protocol) servers.
 * 
 * MCPClient manages connections to multiple MCP servers and provides a unified interface
 * for discovering and using their tools and resources. It supports both SSE (Server-Sent Events)
 * and STDIO (Standard I/O) transport mechanisms.
 * 
 * ## Connection Types
 * 
 * The client supports two transport mechanisms:
 * - **SSE**: HTTP-based connections for remote servers
 * - **STDIO**: Process-based connections for local tools
 * 
 * ## Usage Pattern
 * 
 * MCPClient uses a companion object invoke pattern for safe resource management:
 * ```kotlin
 * MCPClient(config) { client ->
 *     val tools = client.tools()
 *     val resources = client.resources()
 *     // Use tools and resources
 * } // Connections automatically closed
 * ```
 * 
 * @property config Configuration defining the MCP servers to connect to
 * @property clients Map of MCP SDK clients to their corresponding server configurations
 */
class MCPClient private constructor(
  val config: MCPConfig,
  val clients: Map<Client, MCPServer> = config.servers.map {
    Client(
      Implementation(
        name = "Predictable MCP Client",
        version = "0.1.0",
      )
    ) to it.value
  }.toMap(),
) {

  private fun defaultHttpClient(): HttpClient =
    HttpClient {
      install(SSE)
      // default request timeouts
      install(io.ktor.client.plugins.HttpTimeout) {
        requestTimeoutMillis = Duration.INFINITE.inWholeMilliseconds
        connectTimeoutMillis = 30_000 // 30 seconds
      }
    }

  /**
   * Establishes connections to all configured MCP servers.
   * 
   * @return List of cleanup functions to close connections
   */
  suspend fun connect(): List<() -> Unit> =
    clients.map { (client, mcpServer) ->
      when (val config = mcpServer.config) {
        is ServerConfig.SSE -> {
          sseConnection(client, config)
        }
        is ServerConfig.STDIO -> {
          stdioConnection(client, config)
        }
      }
    }


  private suspend fun stdioConnection(
    client: Client,
    serverConfig: ServerConfig.STDIO,
  ): () -> Unit {
    val process = ProcessBuilder(serverConfig.command, *serverConfig.args.toTypedArray())
      .apply {
        serverConfig.env.forEach { (key, value) ->
          environment()[key] = value
        }
      }
      .start()
    client.connect(
      StdioClientTransport(
        input = process.inputStream.asSource().buffered(),
        output = process.outputStream.asSink().buffered()
      )
    )
    return { process.destroy() }
  }

  private suspend fun sseConnection(
    client: Client,
    serverConfig: ServerConfig.SSE
  ) : () -> Unit {
    val httpClient = defaultHttpClient()
    println("Connecting to ${serverConfig.url}")
    client.connect(
      SseClientTransport(
        client = httpClient,
        urlString = serverConfig.url,
      )
    )
    return { httpClient.close() }
  }

  /**
   * Discovers and returns all tools available from connected MCP servers.
   * 
   * Queries each connected server for its available tools and converts them
   * to MCPTool instances that can be invoked.
   * 
   * @return List of available tools from all connected servers
   */
  suspend fun tools(): List<MCPTool> =
    clients.keys.flatMap { client ->
      client.listTools()?.tools.orEmpty().map {
        toolFromMcp(client, it)
      }
    }

  /**
   * Discovers and returns all resources available from connected MCP servers.
   * 
   * Queries each connected server for its available resources.
   * 
   * @return List of available resources from all connected servers
   */
  suspend fun resources(): List<MCPResource> =
    clients.keys.flatMap {
      it.listResources()?.resources.orEmpty().map {
        MCPResource(
          name = it.name,
          description = it.description,
          uri = it.uri,
          mimeType = it.mimeType
        )
      }
    }

  private fun toolFromMcp(
    client: Client,
    tool: Tool
  ): MCPTool = MCPTool(tool) { args ->
    val request = CallToolRequest(
      name = tool.name,
      arguments = args
    )
    val result = client.callTool(request, options = RequestOptions(
      onProgress = null, // TODO bubble up progress events
      timeout = 1.minutes
    ))
    if (result != null) {
      KotlinSchema.Companion.json.encodeToJsonElement(CallToolResultBase.Companion.serializer(), result).jsonObject
    } else JsonObject(emptyMap())
  }

  companion object {
    /**
     * Creates an MCPClient, executes the provided block, and ensures proper cleanup.
     * 
     * This is the recommended way to use MCPClient as it ensures all connections
     * are properly closed after use.
     * 
     * @param config Configuration for the MCP servers to connect to
     * @param block Suspend function to execute with the connected client
     * @return Result of the block execution
     * @throws Exception if connection fails or block execution fails
     */
    suspend operator fun <A> invoke(
      config: MCPConfig,
      block: suspend (MCPClient) -> A
    ): A {
      val mcpClient = MCPClient(config)
      println("Connecting to MCP servers: ${mcpClient.clients.keys.joinToString { it.toString() }}")
      val closeables = mcpClient.connect()
      var result: A? = null
      try {
        result = block(mcpClient)
      } finally {
        closeables.forEach { it.invoke() }
        mcpClient.clients.keys.forEach { it.close() }
      }
      return result ?: error("Result is null, something went wrong during the block execution.")
    }
  }
}
