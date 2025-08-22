package predictable.mcp.config

import kotlinx.serialization.Serializable

/**
 * Configuration for MCP (Model Context Protocol) client connections.
 * 
 * This class holds the configuration for connecting to one or more MCP servers.
 * Each server is identified by a unique name and has its own connection configuration.
 * 
 * @property servers A map of server names to their configurations. The key is a unique
 *                   identifier for the server, and the value contains the server's 
 *                   connection details.
 * 
 * Example:
 * ```kotlin
 * val config = MCPConfig(
 *     servers = mapOf(
 *         "local-server" to MCPServer(
 *             config = ServerConfig.SSE(
 *                 url = "http://localhost:8080/sse"
 *             )
 *         ),
 *         "remote-server" to MCPServer(
 *             config = ServerConfig.STDIO(
 *                 command = "python",
 *                 args = listOf("mcp_server.py")
 *             )
 *         )
 *     )
 * )
 * ```
 */
@Serializable
data class MCPConfig(
  val servers: Map<String, MCPServerConfig>,
)

