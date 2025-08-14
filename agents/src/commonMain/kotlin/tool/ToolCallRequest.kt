package predictable.tool

import kotlinx.serialization.Serializable

@Serializable
data class ToolCallRequest(
  val id: String,
  val name: String,
  val arguments: String
)
