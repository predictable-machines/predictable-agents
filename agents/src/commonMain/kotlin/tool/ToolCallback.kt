package predictable.tool

import predictable.AI

class ToolCallback(
  val onToolCall : suspend (tool: AI<*, *>, toolCallId: String, input: Any?) -> Unit,
  val onToolResponse : suspend (tool: AI<*, *>, toolCallId: String, response: Any?) -> Unit,
)
