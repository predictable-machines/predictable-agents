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
 * Provider implementation for OpenAI-compatible APIs.
 * 
 * Handles communication with OpenAI or compatible APIs (like OpenRouter, local models)
 * for chat completions, structured outputs, and streaming responses. Supports tool
 * calling and manages the complete lifecycle of AI interactions.
 * 
 * @property baseUrl The base URL for the API endpoint (null for default OpenAI)
 * @property apiKey The API key for authentication
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
   * Executes a standard chat completion request.
   * 
   * Sends messages to the AI model and returns a text response. Handles tool
   * calls automatically if tools are provided and the model decides to use them.
   * 
   * @param model The AI model configuration to use
   * @param messages The conversation history
   * @param tools Available tools the AI can call
   * @param parameters Request configuration parameters
   * @param toolCallBack Optional callbacks for tool execution events
   * @return Text response with metadata and message history
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
    val toolCallResult = handleToolCalls<Nothing>(openAI, tools, messages.toMutableList(), response, null, parameters, toolCallBack)
    // Create metadata from the accumulated usage
    val metadata = createAgentMetadata(toolCallResult.accumulatedUsage.toUsage(), modelId)
    return AgentResponse.Text(
      value = toolCallResult.response.choices.first().message.content.orEmpty(),
      metadata = metadata,
      messages = listOfNotNull(toolCallResult.response.choices.firstOrNull()?.message?.let {
        convertChatMessageToMessage(it)
      })
    )
  }

  /**
   * Executes a structured chat completion request.
   * 
   * Generates a response conforming to the specified schema. Automatically retries
   * on parsing errors and handles tool calls if tools are provided.
   * 
   * @param T The type of the structured output
   * @param messages The conversation history
   * @param model The AI model configuration to use
   * @param tools Available tools the AI can call
   * @param schema Schema defining the expected output structure
   * @param parameters Request configuration parameters
   * @param toolCallBack Optional callbacks for tool execution events
   * @return Structured response with parsed data, metadata, and message history
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
    val toolCallResult = handleToolCalls(openAI, tools, messages.toMutableList(), response, schema, parameters, toolCallBack)
    // Create metadata from the accumulated usage
    val metadata = createAgentMetadata(toolCallResult.accumulatedUsage.toUsage(), modelId)
    val buffer = StringBuilder()
    val content = toolCallResult.response.choices.first().message.content
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
      messages = listOfNotNull(toolCallResult.response.choices.firstOrNull()?.message?.let {
        convertChatMessageToMessage(it)
      })
    )
  }

  /**
   * Creates a streaming chat completion.
   * 
   * Returns a Flow that emits response chunks as they're generated. Supports
   * real-time display of AI responses and handles tool calls during streaming.
   * 
   * @param model The AI model configuration to use
   * @param messages The conversation history
   * @param tools Available tools the AI can call
   * @param parameters Request configuration parameters
   * @param toolCallBack Optional callbacks for tool execution events
   * @return StringStream response containing a Flow of response chunks
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
   * Creates a streaming structured chat completion.
   * 
   * Returns a Flow that emits structured data chunks conforming to the schema.
   * Useful for progressively building complex structured outputs.
   * 
   * @param T The type of the structured output
   * @param messages The conversation history
   * @param model The AI model configuration to use
   * @param tools Available tools the AI can call
   * @param schema Schema defining the expected output structure
   * @param parameters Request configuration parameters
   * @param toolCallBack Optional callbacks for tool execution events
   * @return StructuredStream response containing a Flow of structured chunks
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
