package predictable.mcp.config

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

/**
 * Sealed class representing different types of MCP server connection configurations.
 * 
 * MCP servers can be connected to using different transport mechanisms. This sealed
 * class provides configurations for the supported transport types.
 */
@Serializable
sealed class ServerConfig {
  /**
   * Configuration for connecting to an MCP server via Server-Sent Events (SSE).
   * 
   * SSE is used for HTTP-based connections where the server can push events to the client.
   * This is suitable for web-based MCP servers or servers exposed via HTTP endpoints.
   * 
   * @property url The complete URL of the SSE endpoint (e.g., "http://localhost:8080/sse")
   * @property headers Optional HTTP headers to include in requests to the server
   * @property alwaysAllow List of capabilities to always allow without prompting
   * @property disabled Whether this server configuration is disabled
   * 
   * Example:
   * ```kotlin
   * val sseConfig = ServerConfig.SSE(
   *     url = "http://localhost:8080/sse",
   *     headers = mapOf("Authorization" to "Bearer token123"),
   *     alwaysAllow = listOf("read", "write")
   * )
   * ```
   */
  @Serializable
  data class SSE @JvmOverloads constructor(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val alwaysAllow: List<String> = emptyList(),
    val disabled: Boolean = false,
  ): ServerConfig()

  /**
   * Configuration for connecting to an MCP server via Standard I/O (STDIO).
   * 
   * STDIO is used for local process-based connections where the MCP server runs
   * as a subprocess and communicates via standard input/output streams.
   * This is suitable for local tools and scripts.
   * 
   * @property command The command to execute to start the MCP server process
   * @property args Command-line arguments to pass to the process
   * @property env Environment variables to set for the process
   * @property alwaysAllow List of capabilities to always allow without prompting
   * @property disabled Whether this server configuration is disabled
   * 
   * Example:
   * ```kotlin
   * val stdioConfig = ServerConfig.STDIO(
   *     command = "python",
   *     args = listOf("mcp_server.py", "--mode", "production"),
   *     env = mapOf("PYTHONPATH" to "/usr/local/lib/python"),
   *     alwaysAllow = listOf("execute")
   * )
   * ```
   */
  @Serializable
  data class STDIO @JvmOverloads constructor(
    val command: String,
    val args: List<String>,
    val env: Map<String, String> = emptyMap(),
    val alwaysAllow: List<String> = emptyList(),
    val disabled: Boolean = false,
  ) : ServerConfig()
}
