package predictable.tool

import predictable.AI

/**
 * Callback handlers for tool execution events.
 * 
 * Provides hooks to observe and react to tool calls and responses during
 * AI processing. Useful for logging, monitoring, debugging, or implementing
 * custom tool execution logic.
 * 
 * @property onToolCall Callback invoked when a tool is about to be called
 * @property onToolResponse Callback invoked after a tool returns a response
 */
class ToolCallback(
  /**
   * Callback invoked when a tool is about to be called.
   * 
   * @param tool The AI tool being called
   * @param toolCallId Unique identifier for this tool call
   * @param input The input being passed to the tool
   */
  val onToolCall : suspend (tool: AI<*, *>, toolCallId: String, input: Any?) -> Unit,
  /**
   * Callback invoked after a tool returns a response.
   * 
   * @param tool The AI tool that was called
   * @param toolCallId Unique identifier for this tool call
   * @param response The response returned by the tool
   */
  val onToolResponse : suspend (tool: AI<*, *>, toolCallId: String, response: Any?) -> Unit,
)
