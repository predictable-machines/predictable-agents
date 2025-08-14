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

  suspend fun tools(): List<MCPTool> =
    clients.keys.flatMap { client ->
      client.listTools()?.tools.orEmpty().map {
        toolFromMcp(client, it)
      }
    }

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
