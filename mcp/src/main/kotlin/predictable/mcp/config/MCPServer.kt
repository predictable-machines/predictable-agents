package predictable.mcp.config

@Serializable
data class MCPServer(
  val name: String,
  val namespace: String,
  val description: String,
  val config: ServerConfig
)
