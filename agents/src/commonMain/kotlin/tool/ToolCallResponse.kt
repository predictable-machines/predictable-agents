package predictable.tool

import kotlinx.serialization.Serializable

/**
 * Response from a tool execution.
 * 
 * Contains the result of executing a tool call request, including
 * the original request, the tool's output, and tracking information.
 * 
 * @property request The original tool call request that was executed
 * @property toolCallId Unique identifier linking this response to the request
 * @property content The output/result from the tool execution as a string
 */
@Serializable
data class ToolCallResponse(
  val request: ToolCallRequest,
  val toolCallId: String,
  val content: String
)
