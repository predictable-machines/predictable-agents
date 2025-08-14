package predictable.mcp.registry.agent

import kotlinx.serialization.Serializable

@Serializable
data class FindToolsResponse(
  val tools: List<SelectedTool>,
)
