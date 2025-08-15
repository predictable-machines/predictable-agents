package predictable.agent.providers.openai

import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import predictable.AI
import predictable.agent.AgentResponse
import predictable.agent.Message
import predictable.agent.RequestParameters
import predictable.agent.Model
import predictable.tool.ToolCallback
import predictable.tool.OutputSchema

/**
 * OpenAI provider implementation for the Agent.
 */
class OpenAIProvider(
  private val baseUrl: String?,
  private val apiKey: String,
) {
  private val openAI = OpenAI(
    host = baseUrl?.let { OpenAIHost(it) } ?: OpenAIHost.OpenAI,
    token = apiKey,
    logging = LoggingConfig(
      logLevel = LogLevel.None
    )
  )

  /**
   * Execute a chat completion request with the given messages and tools.
   */
  suspend fun chatCompletion(
    model: Model,
    messages: List<Message>,
    tools: List<AI<*, *>>,
    parameters: RequestParameters,
    toolCallBack: ToolCallback?
  ): AgentResponse.Text {
    val modelId = ModelId(model.name)
    val request = buildChatCompletionRequest(
      modelId = modelId,
      chatMessages = messages.toOpenAIChatMessages(),
      tools = tools,
      schema = null,
      parameters = parameters
    )
    val response = openAI.chatCompletion(request)
    // If there were tool calls, make a second request to get the final response
    val finalResponse = handleToolCalls<Nothing>(openAI, tools, messages.toMutableList(), response, null, parameters, toolCallBack)
    // Create metadata from the response
    val metadata = createAgentMetadata(finalResponse.usage, modelId)
    return AgentResponse.Text(
      value = finalResponse.choices.first().message.content.orEmpty(),
      metadata = metadata,
      messages = listOfNotNull(finalResponse.choices.firstOrNull()?.message?.let {
        convertChatMessageToMessage(it)
      })
    )
  }

  /**
   * Execute a structured chat completion request with the given messages, tools, and schema.
   */
  suspend fun <T> chatCompletionStructured(
    messages: List<Message>,
    model: Model,
    tools: List<AI<*, *>>,
    schema: OutputSchema<T>,
    parameters: RequestParameters,
    toolCallBack: ToolCallback?
  ): AgentResponse.Structured<T> {
    val modelId = ModelId(model.name)
    val request = buildChatCompletionRequest(
      modelId = modelId,
      chatMessages = messages.toOpenAIChatMessages(),
      tools = tools,
      // If there are no tools, we need to set the schema in the request
      schema = if (tools.isEmpty()) schema else null,
      parameters = parameters
    )
    val response = openAI.chatCompletion(request)
    // If there were tool calls, make a second request to get the final response
    val finalResponse = handleToolCalls(openAI, tools, messages.toMutableList(), response, schema, parameters, toolCallBack)
    // Create metadata from the response
    //TODO we need to accumulate usage from all inner responses
    val metadata = createAgentMetadata(finalResponse.usage, modelId)
    val buffer = StringBuilder()
    val content = finalResponse.choices.first().message.content
    var element: T? = null
    // First attempt to parse the JSON

    try {
      element = tryParseJson(buffer, content, schema) {
        it
      }
    } catch (e: Exception) {
      // try again with a new message mentioning the error
      val errorMessage = Message.user("Error parsing JSON: ${e.message}. Please try again.")
      return chatCompletionStructured(
        messages = messages + errorMessage,
        model = model,
        tools = tools,
        schema = schema,
        parameters = parameters,
        toolCallBack = toolCallBack
      )
    }

    // If parsing failed, try to clean up the JSON and parse again
    if (element == null && !content.isNullOrEmpty()) {
      val cleanedJson = cleanupJson(content)
      if (cleanedJson != null) {
        try {
          element = schema.outputFromJson(cleanedJson)
        } catch (e: Exception) {
          //
        }
      }
    }

    // If we still couldn't parse the JSON, throw an exception
    if (element == null) {
      throw IllegalStateException("Failed to parse structured response: $content")
    }

    return AgentResponse.Structured<T>(
      value = element,
      metadata = metadata,
      messages = listOfNotNull(finalResponse.choices.firstOrNull()?.message?.let {
        convertChatMessageToMessage(it)
      })
    )
  }

  /**
   * Execute a streaming chat completion request with the given messages and tools.
   */
  fun chatCompletionStream(
    model: Model,
    messages: List<Message>,
    tools: List<AI<*, *>>,
    parameters: RequestParameters,
    toolCallBack: ToolCallback?
  ): AgentResponse.StringStream {
    val modelId = ModelId(model.name)
    val chatMessages = messages.toOpenAIChatMessages()
    val request = buildChatCompletionRequest(modelId, chatMessages, tools, null, parameters)
    val state = StreamingState(request = request, messages = messages.toMutableList(), tools = tools, toolCallBack = toolCallBack)
    val stream = stringStreamResponseFlow(openAI, request, model, state, parameters)
    return AgentResponse.StringStream(value = stream)
  }

  /**
   * Execute a streaming structured chat completion request with the given messages, tools, and schema.
   */
  fun <T> chatCompletionStructuredStream(
    messages: List<Message>,
    model: Model,
    tools: List<AI<*, *>>,
    schema: OutputSchema<T>,
    parameters: RequestParameters,
    toolCallBack: ToolCallback?
  ): AgentResponse.StructuredStream<T> {
    val modelId = ModelId(model.name)
    val chatMessages = messages.toOpenAIChatMessages()
    val elementsSchema = schema.elementsSchema()
    val request = buildChatCompletionRequest(modelId, chatMessages, tools, elementsSchema, parameters)
    val state = StreamingState(request = request, messages = messages.toMutableList(), tools = tools, toolCallBack = toolCallBack)
    return AgentResponse.StructuredStream(
      structuredStreamResponseFlow(openAI, request, model, schema, state, parameters)
    )
  }
}
