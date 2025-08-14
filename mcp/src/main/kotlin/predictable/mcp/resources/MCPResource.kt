package predictable.mcp.resources

data class MCPResource(
  val uri: String,
  val name: String,
  val description: String?,
  val mimeType: String?,
)
