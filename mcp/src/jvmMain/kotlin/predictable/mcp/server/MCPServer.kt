package predictable.mcp.server

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import predictable.AI
import predictable.tool.InputSchema
import predictable.tool.KotlinSchema
import java.util.UUID

object MCPServer {

  /**
   * Starts a Ktor server with MCP support for serving AI tools.
   *
   * @param tools List of AI tools to expose through MCP
   * @param port Port to run the server on (default: 8080)
   * @param host Host to bind to (default: "0.0.0.0")
   * @param serverName Name of the MCP server (default: "predictable-mcp-server")
   * @param serverVersion Version of the MCP server (default: "1.0.0")
   * @return The embedded server instance
   */
  @JvmStatic
  @JvmOverloads
  fun startKtorMCPServer(
    tools: List<AI<*, *>>,
    port: Int = 8080,
    host: String = "0.0.0.0",
    serverName: String = "predictable-mcp-server",
    serverVersion: String = "1.0.0"
  ): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    return embeddedServer(CIO, port = port, host = host) {
      mcp {
        createMCPServer(tools, serverName, serverVersion)
      }
    }
  }

  /**
   * Creates an MCP server instance with the provided AI tools.
   *
   * This function creates a server that implements the Model Context Protocol,
   * exposing the provided AI tools for remote invocation.
   *
   * @param tools List of AI tools to expose through the MCP server
   * @return Configured MCP server instance ready for connection
   */
  fun mcpServer(tools: List<AI<*, *>>): io.modelcontextprotocol.kotlin.sdk.server.Server {
    return Server(
      serverInfo = Implementation(
        name = "example-sse-server",
        version = "1.0.0"
      ),
      options = ServerOptions(
        capabilities = ServerCapabilities(
          tools = ServerCapabilities.Tools(
            listChanged = true
          )
        )
      )
    ).also { server ->
      server.addTools(tools.registeredTools())
    }
  }

  /**
   * Converts a list of AI tools to MCP RegisteredTool instances.
   *
   * @receiver List of AI tools to register
   * @return List of RegisteredTool instances compatible with MCP SDK
   */
  private fun List<AI<*, *>>.registeredTools(): List<RegisteredTool> {
    return this.map { tool ->
      RegisteredTool(
        tool = Tool(
          name = tool.name,
          title = tool.name,  // New in 0.7.2
          description = tool.description,
          inputSchema = tool.schema.toolInput(),
          outputSchema = null,
          annotations = null
        ),
        handler = { request ->
          @Suppress("UNCHECKED_CAST")
          val typedTool = tool as AI<Any?, Any?>
          @Suppress("UNCHECKED_CAST")
          val schema = typedTool.schema as predictable.tool.Schema<Any?, Any?>
          val input = schema.inputFromJson(request.arguments.toString())
          val output = typedTool.invoke(input)
          val jsonValue = schema.outputToJson(output)
          CallToolResult(//TODO handle images and other content types
            content = listOf(
              TextContent(jsonValue)
            )
          )
        }
      )
    }
  }

  /**
   * Converts an InputSchema to MCP Tool.Input format.
   *
   * @receiver InputSchema to convert
   * @return Tool.Input compatible with MCP SDK
   */
  private fun InputSchema<*>.toolInput(): Tool.Input {
    val schema = KotlinSchema.json.parseToJsonElement(inputJsonSchema()).jsonObject
    return Tool.Input(
      properties = schema["properties"]?.jsonObject ?: error("Invalid schema: $schema"),
      required = schema["required"]?.jsonArray?.map { it.toString() }
    )
  }

  /**
   * Creates an MCP server instance with the provided tools.
   *
   * Internal function called from within SSE session blocks to create
   * the actual server instance.
   *
   * @param tools List of AI tools to expose
   * @param serverName Name identifier for the server
   * @param serverVersion Version string for the server
   * @return Configured MCP server instance
   */
  private fun createMCPServer(
    tools: List<AI<*, *>>,
    serverName: String = "predictable-mcp-server",
    serverVersion: String = "1.0.0"
  ): io.modelcontextprotocol.kotlin.sdk.server.Server {
    // Just return the configured server - serverInfo is read-only
    return mcpServer(tools)
  }

  /**
   * Configures a Ktor Application module with MCP support.
   * This can be used when you want to add MCP to an existing Ktor application.
   */
  fun Application.configureMCP(
    tools: List<AI<*, *>>,
    serverName: String = "predictable-mcp-server",
    serverVersion: String = "1.0.0"
  ) {
    mcp {
      createMCPServer(tools, serverName, serverVersion)
    }
  }

}
