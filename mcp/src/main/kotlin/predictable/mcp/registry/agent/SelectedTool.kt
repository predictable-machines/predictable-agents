package predictable.mcp.registry.agent

import kotlinx.serialization.Serializable

@Serializable
data class SelectedTool(
  val name: String,
  val namespace: String,
)
