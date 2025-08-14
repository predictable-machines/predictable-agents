package predictable.mcp.config

import kotlinx.serialization.Serializable

@Serializable
data class MCPConfig(
  val servers: Map<String, MCPServer>,
)

@Serializable
data class MCPServer(
  val name: String,
  val namespace: String,
  val description: String,
  val config: ServerConfig
)
