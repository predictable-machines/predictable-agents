package predictable.agent.providers.openai

import arrow.core.nonFatalOrThrow
import arrow.fx.coroutines.parMap
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ToolCall
import com.aallam.openai.api.core.FinishReason
import com.aallam.openai.client.OpenAI
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.FlowCollector
import predictable.AI
import predictable.tool.ToolCallback
import predictable.agent.Message
import predictable.agent.MessageRole
import predictable.agent.RequestParameters
import predictable.agent.StreamResponse
import predictable.tool.KotlinSchema
import predictable.tool.OutputSchema
import predictable.tool.ToolCallRequest
import predictable.tool.ToolCallResponse

private val logger = KotlinLogging.logger {}

/**
 * Handles tool calls in a chat completion response
 *
 * @param currentStep The current step count, used to track and limit the number of tool call iterations
 */
suspend fun <T> handleToolCalls(
  openAI: OpenAI,
  tools: List<AI<*, *>>,
  messages: MutableList<Message>,
  response: ChatCompletion,
  schema: OutputSchema<T>?,
  parameters: RequestParameters,
  toolCallback: ToolCallback?,
  currentStep: Int = 1
): ChatCompletion {
  val responseMessage = response.choices.first().message
  val finishReason = response.choices.first().finishReason

  logger.debug { "handleToolCalls: currentStep=$currentStep, maxSteps=${parameters.maxSteps}, finishReason=$finishReason" }
  logger.debug { "handleToolCalls: responseMessage.toolCalls=${responseMessage.toolCalls}" }
  logger.debug { "handleToolCalls: responseMessage.content=${responseMessage.content}" }

  // Check if we've reached the maximum number of steps
  if (currentStep >= parameters.maxSteps) {
    logger.debug { "handleToolCalls: Maximum steps (${parameters.maxSteps}) reached, stopping tool call processing" }

    // When we reach the maximum number of steps, we should make a final call without tools
    // so the model replies naturally
    logger.debug { "handleToolCalls: Making a final request without tools" }

    // Add the assistant message with tool calls to the list if it has tool calls
    if (responseMessage.toolCalls != null && responseMessage.toolCalls!!.isNotEmpty()) {
      processToolCalls(responseMessage, messages, tools, toolCallback)
    }

    // Make a final request without tools
    val finalRequest = buildChatCompletionRequest(
      modelId = response.model,
      chatMessages = messages.toOpenAIChatMessages(),
      tools = emptyList(), // No tools for the final request
      schema = schema,
      parameters = parameters
    )

    val finalResponse = openAI.chatCompletion(finalRequest)
    logger.debug { "handleToolCalls: Final response content: ${finalResponse.choices.first().message.content}" }

    return finalResponse
  }

  // Check if the finish reason indicates we should stop
  if (finishReason != null && finishReason != FinishReason.ToolCalls) {
    logger.debug { "handleToolCalls: Finish reason ($finishReason) indicates we should stop" }
    return response
  }

  return if (responseMessage.toolCalls != null && responseMessage.toolCalls!!.isNotEmpty()) {

    // Add the assistant message with tool calls to the list
    // This is crucial because OpenAI requires that a 'tool' message must be a response to a preceding message with 'tool_calls'
    processToolCalls(responseMessage, messages, tools, toolCallback)

    val secondRequest = buildChatCompletionRequest(
      modelId = response.model,
      chatMessages = messages.toOpenAIChatMessages(),
      tools = tools,
      schema = schema,
      parameters = parameters
    )
    val nextResponse = openAI.chatCompletion(secondRequest)

    // Check if the next response has tool calls and recursively handle them
    val nextResponseMessage = nextResponse.choices.first().message
    if (nextResponseMessage.toolCalls != null && nextResponseMessage.toolCalls!!.isNotEmpty()) {
      // Recursively handle tool calls, incrementing the step counter
      handleToolCalls(openAI, tools, messages, nextResponse, schema, parameters, toolCallback, currentStep + 1)
    } else {
      nextResponse
    }
  } else {
    response
  }
}

private suspend fun processToolCalls(
  responseMessage: ChatMessage,
  updatedMessages: MutableList<Message>,
  tools: List<AI<*, *>>,
  toolCallback: ToolCallback?,
) {
  val assistantMessage = convertChatMessageToMessage(responseMessage)
  updatedMessages.add(assistantMessage)

  val toolCalls = responseMessage.toolCalls.orEmpty().filterIsInstance<ToolCall.Function>()
  // process tool calls in parallel with arrow's `parMap`
  logger.debug { "processToolCalls in Parallel: toolCalls=${toolCalls.size}" }
  val results = toolCalls.parMap { toolCall ->
    val function = toolCall.function
    val targetTool = tools.firstOrNull { it.name == function.name }
    requireNotNull(targetTool) {
      "Tool with name ${function.name} not found in the provided tools : ${tools.joinToString { it.name }}"
    }

    targetTool as AI<Any?, Any?>
    val input = targetTool.schema.inputFromJson(function.arguments)
    val formattedResult = executeTool(targetTool, input, toolCallback, toolCall.id.id)

    // Add the tool result message
    toolCall to formattedResult

  }

  results.forEach { (toolCall, formattedResult) ->
    updatedMessages.appendToolResult(toolCall, formattedResult)
  }

}

suspend fun executeTool(targetTool: AI<Any?, Any?>, input: Any?, toolCallback: ToolCallback?, toolCallId: String): String =
  try {
    toolCallback?.onToolCall(targetTool, toolCallId, input)
    val result = targetTool.invoke(input)

    // Format the result as a proper JSON string
    val formattedResult = when (result) {
      // If it's a string, format it as a JSON string with quotes
      is String -> KotlinSchema.json.encodeToString(result)
      // For other types, use simple toString
      else -> result.toString()
    }
    toolCallback?.onToolResponse(targetTool, toolCallId, formattedResult)
    formattedResult
  } catch (e: Throwable) {
    val finalResult = "Error executing tool: ${e.nonFatalOrThrow().message}"
    toolCallback?.onToolResponse(targetTool, toolCallId, finalResult)
    logger.error(e) { "Error executing tool: ${e.message}" }
    finalResult
  }

/**
 * Appends a tool call and its response to a list of messages.
 */
fun MutableList<Message>.appendToolResult(toolCall: ToolCall.Function, functionResponse: String) {
  val message = Message(
    role = MessageRole.ToolResult,
    content = functionResponse,
    toolCallId = toolCall.id.id,
    name = toolCall.function.name
  )
  add(message)
}

/**
 * Handles a streaming tool call
 */
suspend fun <T> FlowCollector<StreamResponse<T>>.handleStreamingToolCall(
  openAI: OpenAI,
  toolCallRequest: ToolCallRequest,
  state: StreamingState,
  parameters: RequestParameters,
  toolCallback: ToolCallback?
) {
  val request = state.request
  val messages = state.messages
  val tools = state.tools

  // Find the tool by name
  val targetTool = tools.firstOrNull { it.name == toolCallRequest.name } ?: return

  // Add an assistant message with the tool call
  val assistantMessage = Message(
    role = MessageRole.Assistant,
    content = "",
    toolCalls = listOf(toolCallRequest)
  )
  messages.add(assistantMessage)

  // Validate JSON before executing the tool
  val arguments = toolCallRequest.arguments
  if (!isValidJson(arguments)) {
    logger.warn { "Invalid or incomplete JSON: $arguments" }
    return
  }

  try {
    targetTool as AI<Any?, Any?>
    val input = targetTool.schema.inputFromJson(arguments)
    val formattedResult = executeTool(targetTool, input, toolCallback, toolCallRequest.id)

    // Add the tool result message
    messages.add(
      Message(
        role = MessageRole.ToolResult,
        content = formattedResult,
        toolCallId = toolCallRequest.id,
        name = toolCallRequest.name
      )
    )
    emit(
      StreamResponse.ToolResult(
        ToolCallResponse(
          toolCallId = toolCallRequest.id,
          content = formattedResult,
          request = toolCallRequest
        )
      )
    )
  } catch (e: Exception) {
    logger.error(e) { "Error executing tool: ${e.message}" }
    return
  }

}
