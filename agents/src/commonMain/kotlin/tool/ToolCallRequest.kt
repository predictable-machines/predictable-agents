package predictable.tool

import kotlinx.serialization.Serializable

/**
 * Request to execute a tool/function during AI processing.
 * 
 * Represents the AI's intention to call a specific tool with given arguments.
 * This is generated when the AI determines that a tool execution is needed
 * to complete its task or answer a query.
 * 
 * @property id Unique identifier for this tool call request
 * @property name The name of the tool to execute
 * @property arguments JSON string containing the tool's input arguments
 */
@Serializable
data class ToolCallRequest(
  val id: String,
  val name: String,
  val arguments: String
)
