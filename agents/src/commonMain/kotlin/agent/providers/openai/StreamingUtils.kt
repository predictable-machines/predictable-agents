package predictable.agent.providers.openai

import com.aallam.openai.api.chat.*
import com.aallam.openai.api.core.FinishReason
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import predictable.agent.RequestParameters
import predictable.agent.StreamResponse
import predictable.tool.Schema
import predictable.tool.ToolCallRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import predictable.agent.Message
import predictable.agent.Model
import predictable.tool.OutputSchema

private val logger = KotlinLogging.logger {}

/**
 * Creates a flow for streaming responses
 */
fun <T> streamResponseFlow(
  openAI: OpenAI,
  request: ChatCompletionRequest,
  model: Model,
  state: StreamingState,
  parameters: RequestParameters,
  lastFinishReason: FinishReason?,
  emitChunk: suspend FlowCollector<StreamResponse<T>>.(ChatCompletionChunk, StreamingState) -> Unit,
): Flow<StreamResponse<T>> = flow {
  // Get the streaming response
  val streamingResponse = openAI.chatCompletions(request)
  var finishReason = lastFinishReason
  streamingResponse.collect { chunk ->
    logger.debug { "Streaming chunk: $chunk" }
    // Extract and emit tool calls if present
    emitToolCalls(chunk.choices.firstOrNull()?.delta, state, openAI, parameters)
    emitMetadata(chunk, model)
    emitChunk(chunk, state)
    finishReason = chunk.choices.firstOrNull()?.finishReason
  }

  if (state.currentStep >= parameters.maxSteps && finishReason == FinishReason.ToolCalls) {
    logger.debug { "max steps reached" }
    val updatedRequest = request.copy(
      messages = (state.messages + (
        Message.system("I have to provide a final response without tool calls due to max steps reached")
        )).map(::convertMessageToChatMessage),
      tools = emptyList(),
    )
    //final call without tools
    streamResponseFlow(
      openAI = openAI,
      request = updatedRequest,
      model = model,
      state = state.copy(
        handledToolCalls = mutableSetOf(),
        completeToolCalls = mutableSetOf(),
        emittedToolCalls = mutableSetOf(),
        toolCallArguments = mutableMapOf(),
        toolCallFunctionNames = mutableMapOf(),
        toolCallsByIndex = mutableMapOf(),
        currentStep = state.currentStep + 1,
        toolCallBack = state.toolCallBack,
      ),
      parameters = parameters,
      lastFinishReason = finishReason,
      emitChunk = emitChunk,
    ).collect { chunk ->
      emit(chunk)
    }
    finishReason = FinishReason.Stop
  }

  if (finishReason == FinishReason.ToolCalls) {
    logger.debug { "Streaming finished with tool calls" }
    val updatedRequest = request.copy(
      messages = state.messages.map(::convertMessageToChatMessage),
    )
    //this call had tool calls we need to call ourselves again with the updated messages
    //which includes tools execution
    streamResponseFlow(
      openAI = openAI,
      request = updatedRequest,
      model = model,
      state = state.copy(
        handledToolCalls = mutableSetOf(),
        completeToolCalls = mutableSetOf(),
        emittedToolCalls = mutableSetOf(),
        toolCallArguments = mutableMapOf(),
        toolCallFunctionNames = mutableMapOf(),
        toolCallsByIndex = mutableMapOf(),
        currentStep = state.currentStep + 1,
        toolCallBack = state.toolCallBack,
      ),
      parameters = parameters,
      lastFinishReason = finishReason,
      emitChunk = emitChunk
    ).collect { chunk ->
      emit(chunk)
    }
  }
  // Emit end marker
  if (finishReason == FinishReason.Stop)
    emit(StreamResponse.End)
}

/**
 * Creates a flow for streaming string responses
 */
fun stringStreamResponseFlow(
  openAI: OpenAI,
  request: ChatCompletionRequest,
  model: Model,
  state: StreamingState,
  parameters: RequestParameters
): Flow<StreamResponse<String>> =
  streamResponseFlow(openAI, request, model, state, parameters, null) { chunk, state ->
    emitContent(chunk)
  }

/**
 * Creates a flow for streaming structured responses
 */
fun <T> structuredStreamResponseFlow(
  openAI: OpenAI,
  request: ChatCompletionRequest,
  model: Model,
  schema: OutputSchema<T>,
  state: StreamingState,
  parameters: RequestParameters
): Flow<StreamResponse<T>> {
  val buffer = StringBuilder()
  return streamResponseFlow(openAI, request, model, state, parameters, null) { chunk, state ->
    tryEmitStructuredObjects(chunk, buffer, schema)
  }
}

/**
 * Emits content from a chat completion chunk
 */
suspend fun FlowCollector<StreamResponse<String>>.emitContent(
  chunk: ChatCompletionChunk
) {
  // Extract content from the chunk
  val delta = chunk.choices.firstOrNull()?.delta
  val content = delta?.content

  // If there's content in this chunk, emit it
  if (!content.isNullOrEmpty()) {
    emit(StreamResponse.Chunk(content))
  }
}

/**
 * Emits metadata from a chat completion chunk
 */
suspend fun <T> FlowCollector<StreamResponse<T>>.emitMetadata(
  chunk: ChatCompletionChunk,
  model: Model
) {
  chunk.usage?.let { usage ->
    val promptTokens = usage.promptTokens
    val completionTokens = usage.completionTokens
    val totalTokens = usage.totalTokens
    if (promptTokens != null && completionTokens != null && totalTokens != null) {
      emit(
        StreamResponse.Metadata(
          createAgentMetadata(
            usage = usage,
            modelId = ModelId(model.name),
          )
        )
      )
    }
  }
}

/**
 * Emits tool calls from a chat delta
 */
suspend fun <T> FlowCollector<StreamResponse<T>>.emitToolCalls(
  delta: ChatDelta?,
  state: StreamingState,
  openAI: OpenAI,
  parameters: RequestParameters,
) {

  // Extract tool calls from the delta
  val toolCalls = delta?.toolCalls

  // If there are tool calls, process them
  toolCalls?.forEach { toolCallChunk ->
    val function = toolCallChunk.function
    val toolId = toolCallChunk.id
    val index = toolCallChunk.index

    // Get the function name safely without directly accessing the property
    // This avoids calling getName() which might throw an exception if name is null
    val functionName = try {
      function?.name
    } catch (e: IllegalArgumentException) {
      null
    }

    // If we have a tool ID, associate it with the index for future reference
    if (toolId != null && index >= 0) {
      state.toolCallsByIndex[index] = toolId.id

      // Initialize the arguments buffer for this tool call if it doesn't exist
      if (!state.toolCallArguments.containsKey(toolId.id)) {
        state.toolCallArguments[toolId.id] = StringBuilder()
      }
    }

    // If we have a function name and tool ID, mark this tool call as complete
    if (functionName != null && toolId != null) {
      state.completeToolCalls.add(toolId.id)
      state.toolCallFunctionNames[toolId.id] = functionName
    }

    // Process arguments if present
    if (function?.arguments != null) {
      // If we know which tool ID this index belongs to, append to that buffer
      if (index >= 0 && state.toolCallsByIndex.containsKey(index)) {
        val id = state.toolCallsByIndex[index]!!
        state.toolCallArguments[id]?.append(function.arguments)
      }
      // Otherwise, if we have a tool ID directly, use that
      else if (toolId != null) {
        state.toolCallArguments[toolId.id]?.append(function.arguments)
      }
    }

    // Emit complete tool calls that haven't been emitted yet
    state.completeToolCalls.forEach { id ->
      if (!state.emittedToolCalls.contains(id) && state.toolCallArguments.containsKey(id)) {
        // Get the function name for this tool call from our map
        val name = state.toolCallFunctionNames[id] ?: return@forEach // Skip if we can't find the name

        // Create and emit the tool call with accumulated arguments
        val toolCallRequest = ToolCallRequest(
          id = id,
          name = name,
          arguments = state.toolCallArguments[id]?.toString() ?: ""
        )

        // Only emit if we have some arguments and they form valid JSON
        val args = toolCallRequest.arguments
        if (args.isNotEmpty() && isValidJson(args)) {
          // Debug log to see what's happening
          logger.debug { "Emitting tool call: $toolCallRequest" }

          emit(StreamResponse.ToolCall(toolCallRequest))
          state.emittedToolCalls.add(id)

          // Handle the tool call if it hasn't been handled yet
          if (!state.handledToolCalls.contains(id)) {
            logger.debug { "Handling tool call: $toolCallRequest" }
            handleStreamingToolCall(openAI, toolCallRequest, state, parameters, state.toolCallBack)
            state.handledToolCalls.add(id)
          }
        } else if (args.isNotEmpty()) {
          // Debug log for incomplete JSON
          logger.warn { "Not emitting tool call with incomplete JSON: $args" }
        }
      }
    }
  }
}
