package predictable.mcp.config

import kotlinx.serialization.Serializable

@Serializable
data class MCPConfig(
  val servers: Map<String, MCPServer>,
)

