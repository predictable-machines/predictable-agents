package predictable.mcp.config

import kotlinx.serialization.Serializable

/**
 * Represents an MCP server configuration entry.
 * 
 * This class contains the metadata and connection configuration for an MCP server.
 * It describes how to connect to and interact with a specific MCP server instance.
 * 
 * @property name The human-readable name of the server
 * @property namespace A unique namespace identifier for the server's capabilities
 * @property description A description of what the server provides or its purpose
 * @property config The connection configuration specifying how to connect to the server
 * 
 * Example:
 * ```kotlin
 * val server = MCPServer(
 *     name = "Translation Service",
 *     namespace = "translation",
 *     description = "Provides multi-language translation capabilities",
 *     config = ServerConfig.SSE(
 *         url = "http://localhost:8080/sse"
 *     )
 * )
 * ```
 */
@Serializable
data class MCPServer(
  val name: String,
  val namespace: String,
  val description: String,
  val config: ServerConfig
)
