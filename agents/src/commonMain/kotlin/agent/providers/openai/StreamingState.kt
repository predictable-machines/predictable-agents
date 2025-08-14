package predictable.agent.providers.openai

import com.aallam.openai.api.chat.ChatCompletionRequest
import predictable.AI
import predictable.tool.ToolCallback
import predictable.agent.Message

/**
 * Data class to encapsulate the state needed for streaming operations.
 * This makes the code more thread-safe by avoiding shared mutable state.
 */
data class StreamingState(
  // The request being processed
  val request: ChatCompletionRequest,
  // The original messages for the request
  val messages: MutableList<Message>,
  // The tools for the request
  val tools: List<AI<*, *>>,
  // Map to store accumulated arguments for each tool call
  val toolCallArguments: MutableMap<String, StringBuilder> = mutableMapOf(),
  // Map to track tool calls by index
  val toolCallsByIndex: MutableMap<Int, String> = mutableMapOf(),
  // Set to track complete tool calls (name and ID are known)
  val completeToolCalls: MutableSet<String> = mutableSetOf(),
  // Set to track emitted tool calls to avoid duplicates
  val emittedToolCalls: MutableSet<String> = mutableSetOf(),
  // Set to store processed tool calls that have been handled
  val handledToolCalls: MutableSet<String> = mutableSetOf(),
  // Map to store function names for each tool ID
  val toolCallFunctionNames: MutableMap<String, String> = mutableMapOf(),
  // Current step count for tracking tool call iterations
  var currentStep: Int = 1,
  val toolCallBack: ToolCallback?
)
