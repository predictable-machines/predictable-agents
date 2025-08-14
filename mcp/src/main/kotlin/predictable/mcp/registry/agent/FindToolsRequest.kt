package predictable.mcp.registry.agent

import kotlinx.serialization.Serializable

@Serializable
data class FindToolsRequest(
  val query: String,
)
