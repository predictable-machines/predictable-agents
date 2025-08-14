package predictable.tool

import kotlinx.serialization.Serializable

@Serializable
data class ToolCallResponse(
  val request: ToolCallRequest,
  val toolCallId: String,
  val content: String
)
