package predictable.agent.providers

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import arrow.core.raise.Raise
import arrow.core.raise.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import predictable.AI
import predictable.agent.*
import predictable.agent.compression.CompressionError
import predictable.tool.OutputSchema
import predictable.tool.ToolCallback

/**
 * Provider-agnostic implementation for AI chat completions.
 *
 * Handles communication with multiple AI providers (OpenAI, Anthropic, Google, Ollama)
 * through Koog's unified interface. Supports chat completions, structured outputs,
 * streaming responses, and tool calling.
 *
 * Automatically infers the correct provider based on the model name and creates
 * the appropriate LLMClient instance.
 *
 * @property apiKey The API key for authentication with the AI provider
 */
@OptIn(ExperimentalUuidApi::class)
class AgentProvider(
  private val apiKey: String,
) {
  private val clientCache = mutableMapOf<String, LLMClient>()

  /**
   * Executes a standard chat completion request.
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
    val client = getClient(model)
    val prompt = buildPrompt(messages, parameters, tools.isNotEmpty())
    val llModel = ModelProvider.fromModel(model)
    val koogTools = convertTools(tools)
    val responses = executeWithTools(client, prompt, llModel, koogTools, tools, messages, parameters, toolCallBack)
    return convertTextResponse(responses, model.name)
  }

  /**
   * Executes a structured chat completion request.
   *
   * @param T The type of the structured output
   * @param messages The conversation history
   * @param model The AI model configuration to use
   * @param tools Available tools the AI can call
   * @param schema Schema defining the expected output structure
   * @param parameters Request configuration parameters
   * @param toolCallBack Optional callbacks for tool execution events
   * @return Structured response with parsed data
   */
  suspend fun <T> chatCompletionStructured(
    messages: List<Message>,
    model: Model,
    tools: List<AI<*, *>>,
    schema: OutputSchema<T>,
    parameters: RequestParameters,
    toolCallBack: ToolCallback?
  ): AgentResponse.Structured<T> {
    val client = getClient(model)
    val schemaInstruction = createSchemaInstruction(schema)
    val messagesWithSchema = listOf(schemaInstruction) + messages
    val prompt = buildPrompt(messagesWithSchema, parameters, tools.isNotEmpty())
    val llModel = ModelProvider.fromModel(model)
    val koogTools = convertTools(tools)
    val responses = executeWithTools(client, prompt, llModel, koogTools, tools, messagesWithSchema, parameters, toolCallBack)
    return parseStructured(responses, messagesWithSchema, schema, model, llModel, tools, parameters, toolCallBack, 0)
  }

  /**
   * Creates a streaming chat completion.
   *
   * @param model The AI model configuration to use
   * @param messages The conversation history
   * @param tools Available tools the AI can call
   * @param parameters Request configuration parameters
   * @param toolCallBack Optional callbacks for tool execution events
   * @return StringStream response with Flow of chunks
   */
  fun chatCompletionStream(
    model: Model,
    messages: List<Message>,
    tools: List<AI<*, *>>,
    parameters: RequestParameters,
    toolCallBack: ToolCallback?
  ): AgentResponse.StringStream {
    val client = getClient(model)
    val llModel = ModelProvider.fromModel(model)
    val koogTools = convertTools(tools)
    val stream = streamWithTools(client, llModel, koogTools, tools, messages.toMutableList(), parameters, toolCallBack, 1, model.name)
    return AgentResponse.StringStream(stream)
  }

  /**
   * Creates a streaming structured chat completion.
   *
   * @param T The type of the structured output
   * @param messages The conversation history
   * @param model The AI model configuration to use
   * @param tools Available tools the AI can call
   * @param schema Schema defining the expected output structure
   * @param parameters Request configuration parameters
   * @param toolCallBack Optional callbacks for tool execution events
   * @return StructuredStream response with Flow of structured chunks
   */
  fun <T> chatCompletionStructuredStream(
    messages: List<Message>,
    model: Model,
    tools: List<AI<*, *>>,
    schema: OutputSchema<T>,
    parameters: RequestParameters,
    toolCallBack: ToolCallback?
  ): AgentResponse.StructuredStream<T> {
    val client = getClient(model)
    val schemaInstruction = createSchemaInstruction(schema)
    val messagesWithSchema = (listOf(schemaInstruction) + messages).toMutableList()
    val llModel = ModelProvider.fromModel(model)
    val koogTools = convertTools(tools)
    val stream = streamStructuredWithTools(client, llModel, koogTools, tools, messagesWithSchema, schema, parameters, toolCallBack, 1, model.name)
    return AgentResponse.StructuredStream(stream)
  }

  // Helper functions (all ≤10 lines)

  private fun getClient(model: Model): LLMClient =
    clientCache.getOrPut(model.name) {
      ClientFactory.create(model, apiKey)
    }

  private fun buildPrompt(messages: List<Message>, parameters: RequestParameters = RequestParameters.defaultParameters, hasTools: Boolean = false): Prompt {
    val koogMessages = messages.flatMap { convertMessage(it) }
    val llmParams = convertRequestParameters(parameters, hasTools)
    return Prompt(koogMessages, Uuid.random().toString(), llmParams)
  }

  private fun convertRequestParameters(params: RequestParameters, hasTools: Boolean): LLMParams {
    val toolChoice = if (hasTools) convertToolChoice(params.toolChoice) else null
    val additionalProps = buildAdditionalProperties(params)

    return LLMParams(
      temperature = params.temperature,
      maxTokens = params.maxCompletionTokens,
      numberOfChoices = params.n,
      user = params.user,
      toolChoice = toolChoice,
      additionalProperties = if (additionalProps.isEmpty()) null else additionalProps
    )
  }

  private fun convertToolChoice(choice: predictable.tool.ToolChoice): LLMParams.ToolChoice? =
    when (choice) {
      is predictable.tool.ToolChoice.Mode -> when (choice.value) {
        "auto" -> LLMParams.ToolChoice.Auto
        "none" -> LLMParams.ToolChoice.None
        "required" -> LLMParams.ToolChoice.Required
        else -> LLMParams.ToolChoice.Auto
      }
      is predictable.tool.ToolChoice.Named -> choice.function?.name?.let {
        LLMParams.ToolChoice.Named(it)
      } ?: LLMParams.ToolChoice.Auto
    }

  private fun buildAdditionalProperties(params: RequestParameters): Map<String, JsonElement> {
    val props = mutableMapOf<String, JsonElement>()
    params.topP?.let { props["top_p"] = JsonPrimitive(it) }
    params.stop?.let { props["stop"] = JsonArray(it.map { s -> JsonPrimitive(s) }) }
    params.presencePenalty?.let { props["presence_penalty"] = JsonPrimitive(it) }
    params.frequencyPenalty?.let { props["frequency_penalty"] = JsonPrimitive(it) }
    params.logitBias?.let { bias ->
      props["logit_bias"] = JsonObject(bias.mapValues { JsonPrimitive(it.value) })
    }
    params.store?.let { props["store"] = JsonPrimitive(it) }
    params.logprobs?.let { props["logprobs"] = JsonPrimitive(it) }
    params.topLogprobs?.let { props["top_logprobs"] = JsonPrimitive(it) }
    return props
  }

  private fun convertMessage(message: Message): List<KoogMessage> {
    val requestInfo = RequestMetaInfo(Clock.System.now())
    val responseInfo = ResponseMetaInfo.Empty
    return when (message.role) {
      MessageRole.System -> listOf(KoogMessage.System(message.content, requestInfo))
      MessageRole.User -> listOf(KoogMessage.User(message.content, requestInfo))
      MessageRole.Assistant -> convertAssistant(message, responseInfo)
      MessageRole.ToolResult -> listOf(KoogMessage.Tool.Result(message.toolCallId, message.name ?: "", message.content, requestInfo))
      is MessageRole.Custom -> listOf(KoogMessage.User(message.content, requestInfo))
    }
  }

  private fun convertAssistant(message: Message, info: ResponseMetaInfo): List<KoogMessage> =
    if (message.toolCalls != null && message.toolCalls.isNotEmpty()) {
      message.toolCalls.map { KoogMessage.Tool.Call(it.id, it.name, it.arguments, info) }
    } else {
      listOf(KoogMessage.Assistant(message.content, info))
    }

  private fun <T> createSchemaInstruction(schema: OutputSchema<T>): Message {
    val json = schema.outputJsonSchema()
    return Message.system("You must respond ONLY with valid JSON that matches this exact schema: $json. Do not include any explanatory text, only the JSON object.")
  }

  internal fun convertTools(tools: List<AI<*, *>>): List<ToolDescriptor> =
    tools.map { convertTool(it) }

  internal fun convertTool(tool: AI<*, *>): ToolDescriptor {
    val schemaJson = tool.schema.inputJsonSchema()
    val schema = Json.parseToJsonElement(schemaJson).jsonObject
    val params = extractParams(schema)
    return ToolDescriptor(tool.name, tool.description, params.first, params.second)
  }

  internal fun extractParams(schema: JsonObject): Pair<List<ToolParameterDescriptor>, List<ToolParameterDescriptor>> {
    val props = schema["properties"]?.jsonObject ?: return emptyList<ToolParameterDescriptor>() to emptyList()
    val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    val requiredParams = props.filter { it.key in required }.map { createParam(it.key, it.value.jsonObject) }
    val optionalParams = props.filterNot { it.key in required }.map { createParam(it.key, it.value.jsonObject) }
    return requiredParams to optionalParams
  }

  private fun createParam(name: String, schema: JsonObject): ToolParameterDescriptor {
    val desc = schema["description"]?.jsonPrimitive?.content ?: ""
    val type = mapJsonSchemaToKoogType(schema)
    return ToolParameterDescriptor(name, desc, type)
  }

  private fun mapJsonSchemaToKoogType(schema: JsonObject): ToolParameterType {
    val enumValues = schema["enum"]?.jsonArray?.map { it.jsonPrimitive.content }
    if (enumValues != null) return ToolParameterType.Enum(enumValues.toTypedArray())

    val typeStr = schema["type"]?.jsonPrimitive?.content ?: "string"
    return when (typeStr) {
      "string" -> ToolParameterType.String
      "integer" -> ToolParameterType.Integer
      "number" -> mapNumberType(schema)
      "boolean" -> ToolParameterType.Boolean
      "array" -> mapArrayType(schema)
      "object" -> mapObjectType(schema)
      else -> ToolParameterType.String
    }
  }

  private fun mapNumberType(schema: JsonObject): ToolParameterType {
    val format = schema["format"]?.jsonPrimitive?.content
    return when (format) {
      "float", "double" -> ToolParameterType.Float
      else -> ToolParameterType.Integer
    }
  }

  private fun mapArrayType(schema: JsonObject): ToolParameterType {
    val items = schema["items"]?.jsonObject ?: return ToolParameterType.List(ToolParameterType.String)
    val itemType = mapJsonSchemaToKoogType(items)
    return ToolParameterType.List(itemType)
  }

  private fun mapObjectType(schema: JsonObject): ToolParameterType {
    val props = schema["properties"]?.jsonObject ?: return ToolParameterType.Object(emptyList())
    val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    val additionalProps = schema["additionalProperties"]?.let { elem ->
      try { elem.jsonPrimitive.content.toBoolean() } catch (e: Exception) { null }
    }
    val additionalPropsType = schema["additionalProperties"]?.jsonObject?.let { mapJsonSchemaToKoogType(it) }

    val properties = props.map { (key, value) ->
      ToolParameterDescriptor(key, value.jsonObject["description"]?.jsonPrimitive?.content ?: "", mapJsonSchemaToKoogType(value.jsonObject))
    }

    return ToolParameterType.Object(properties, required, additionalProps, additionalPropsType)
  }

  private suspend fun executeKoog(client: LLMClient, prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<KoogMessage.Response> =
    client.execute(prompt, model, tools)

  private fun streamKoog(client: LLMClient, prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> =
    client.executeStreaming(prompt, model, tools)

  private suspend fun executeWithTools(
    client: LLMClient,
    prompt: Prompt,
    llModel: LLModel,
    koogTools: List<ToolDescriptor>,
    tools: List<AI<*, *>>,
    messages: List<Message>,
    parameters: RequestParameters,
    toolCallBack: ToolCallback?
  ): List<KoogMessage.Response> {
    val responses = executeKoog(client, prompt, llModel, koogTools)
    return handleTools(client, responses, llModel, koogTools, tools, messages.toMutableList(), parameters, toolCallBack, 1)
  }

  private suspend fun handleTools(
    client: LLMClient,
    responses: List<KoogMessage.Response>,
    llModel: LLModel,
    koogTools: List<ToolDescriptor>,
    tools: List<AI<*, *>>,
    messages: MutableList<Message>,
    parameters: RequestParameters,
    toolCallBack: ToolCallback?,
    step: Int
  ): List<KoogMessage.Response> {
    val calls = responses.filterIsInstance<KoogMessage.Tool.Call>()
    if (calls.isEmpty() || step >= parameters.maxSteps) return responses
    addAssistantWithCalls(messages, calls)
    executeTools(calls, tools, messages, toolCallBack)
    val prompt = buildPrompt(messages, parameters, true)
    val newResponses = executeKoog(client, prompt, llModel, koogTools)
    return handleTools(client, newResponses, llModel, koogTools, tools, messages, parameters, toolCallBack, step + 1)
  }

  private fun addAssistantWithCalls(messages: MutableList<Message>, calls: List<KoogMessage.Tool.Call>) {
    val requests = calls.map { predictable.tool.ToolCallRequest(it.id ?: "", it.tool, it.content) }
    messages.add(Message(MessageRole.Assistant, "", toolCalls = requests))
  }

  private suspend fun executeTools(calls: List<KoogMessage.Tool.Call>, tools: List<AI<*, *>>, messages: MutableList<Message>, toolCallBack: ToolCallback?) {
    calls.forEach { call -> executeTool(call, tools, messages, toolCallBack) }
  }

  private suspend fun executeTool(call: KoogMessage.Tool.Call, tools: List<AI<*, *>>, messages: MutableList<Message>, toolCallBack: ToolCallback?) {
    try {
      val tool = tools.firstOrNull { it.name == call.tool } ?: error("Tool ${call.tool} not found")
      val id = call.id ?: ""
      toolCallBack?.onToolCall(tool, id, call.content)
      val input = (tool as AI<Any?, Any?>).schema.inputFromJson(call.content)
      val output = tool.invoke(input)
      val result = serializeOutput(output, tool)
      toolCallBack?.onToolResponse(tool, id, result)
      messages.add(Message(MessageRole.ToolResult, result, toolCallId = id, name = call.tool))
    } catch (e: Exception) {
      messages.add(Message(MessageRole.ToolResult, "Error: ${e.message}", toolCallId = call.id, name = call.tool))
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun serializeOutput(output: Any?, tool: AI<*, *>): String {
    if (output is String) return output
    val schema = tool.schema as predictable.tool.OutputSchema<Any?>
    return schema.outputToJson(output)
  }

  private fun streamWithTools(
    client: LLMClient,
    llModel: LLModel,
    koogTools: List<ToolDescriptor>,
    tools: List<AI<*, *>>,
    messages: MutableList<Message>,
    parameters: RequestParameters,
    toolCallBack: ToolCallback?,
    step: Int,
    modelId: String
  ): Flow<StreamResponse<String>> = flow {
    val prompt = buildPrompt(messages, parameters, true)
    val stream = streamKoog(client, prompt, llModel, koogTools)
    val calls = mutableListOf<StreamFrame.ToolCall>()
    stream.collect { frame ->
      when (frame) {
        is StreamFrame.Append -> emit(StreamResponse.Chunk(frame.text))
        is StreamFrame.ToolCall -> {
          calls.add(frame)
          emit(StreamResponse.ToolCall(predictable.tool.ToolCallRequest(frame.id ?: "", frame.name, frame.content)))
        }
        is StreamFrame.End -> {
          val metadata = createMetadata(frame.metaInfo, modelId)
          if (calls.isNotEmpty() && step < parameters.maxSteps) {
            emit(StreamResponse.Metadata(metadata))
            @Suppress("UNCHECKED_CAST")
            executeStreamTools(calls, tools, messages, toolCallBack) { emit(it as StreamResponse<String>) }
            streamWithTools(client, llModel, koogTools, tools, messages, parameters, toolCallBack, step + 1, modelId).collect { emit(it) }
          } else {
            emit(StreamResponse.Metadata(metadata))
            emit(StreamResponse.End)
          }
        }
      }
    }
  }

  private suspend fun executeStreamTools(
    calls: List<StreamFrame.ToolCall>,
    tools: List<AI<*, *>>,
    messages: MutableList<Message>,
    toolCallBack: ToolCallback?,
    emit: suspend (StreamResponse<*>) -> Unit
  ) {
    val koogCalls = calls.map { KoogMessage.Tool.Call(it.id, it.name, it.content, ResponseMetaInfo.Empty) }
    addAssistantWithCalls(messages, koogCalls)
    koogCalls.forEach { call -> executeToolWithEmit(call, tools, messages, toolCallBack, emit) }
  }

  private fun <T> streamStructuredWithTools(
    client: LLMClient,
    llModel: LLModel,
    koogTools: List<ToolDescriptor>,
    tools: List<AI<*, *>>,
    messages: MutableList<Message>,
    schema: OutputSchema<T>,
    parameters: RequestParameters,
    toolCallBack: ToolCallback?,
    step: Int,
    modelId: String
  ): Flow<StreamResponse<T>> = flow {
    val prompt = buildPrompt(messages, parameters, true)
    val stream = streamKoog(client, prompt, llModel, koogTools)
    val calls = mutableListOf<StreamFrame.ToolCall>()
    stream.collect { frame ->
      when (frame) {
        is StreamFrame.Append -> tryParse(frame.text, schema)?.let { emit(it) }
        is StreamFrame.ToolCall -> {
          calls.add(frame)
          emit(StreamResponse.ToolCall(predictable.tool.ToolCallRequest(frame.id ?: "", frame.name, frame.content)))
        }
        is StreamFrame.End -> {
          val metadata = createMetadata(frame.metaInfo, modelId)
          if (calls.isNotEmpty() && step < parameters.maxSteps) {
            emit(StreamResponse.Metadata(metadata))
            @Suppress("UNCHECKED_CAST")
            executeStreamTools(calls, tools, messages, toolCallBack) { emit(it as StreamResponse<T>) }
            streamStructuredWithTools(client, llModel, koogTools, tools, messages, schema, parameters, toolCallBack, step + 1, modelId).collect { emit(it) }
          } else {
            emit(StreamResponse.Metadata(metadata))
            emit(StreamResponse.End)
          }
        }
      }
    }
  }

  private suspend fun executeToolWithEmit(
    call: KoogMessage.Tool.Call,
    tools: List<AI<*, *>>,
    messages: MutableList<Message>,
    toolCallBack: ToolCallback?,
    emit: suspend (StreamResponse<*>) -> Unit
  ) {
    try {
      val tool = tools.firstOrNull { it.name == call.tool } ?: error("Tool ${call.tool} not found")
      val id = call.id ?: ""
      val request = predictable.tool.ToolCallRequest(id, call.tool, call.content)
      toolCallBack?.onToolCall(tool, id, call.content)
      val input = (tool as AI<Any?, Any?>).schema.inputFromJson(call.content)
      val output = tool.invoke(input)
      val result = serializeOutput(output, tool)
      toolCallBack?.onToolResponse(tool, id, result)
      messages.add(Message(MessageRole.ToolResult, result, toolCallId = id, name = call.tool))
      emit(StreamResponse.ToolResult(predictable.tool.ToolCallResponse(request, id, result)))
    } catch (e: Exception) {
      val id = call.id ?: ""
      val request = predictable.tool.ToolCallRequest(id, call.tool, call.content)
      val error = "Error: ${e.message}"
      messages.add(Message(MessageRole.ToolResult, error, toolCallId = call.id, name = call.tool))
      emit(StreamResponse.ToolResult(predictable.tool.ToolCallResponse(request, id, error)))
    }
  }

  private suspend fun <T> parseStructured(
    responses: List<KoogMessage.Response>,
    originalMessages: List<Message>,
    schema: OutputSchema<T>,
    model: Model,
    llModel: LLModel,
    tools: List<AI<*, *>>,
    parameters: RequestParameters,
    toolCallBack: ToolCallback?,
    retryCount: Int,
    maxRetries: Int = 3
  ): AgentResponse.Structured<T> {
    try {
      return convertStructuredResponse(responses, schema, model.name)
    } catch (e: Exception) {
      if (retryCount >= maxRetries) throw IllegalStateException("Failed after $maxRetries retries: ${e.message}", e)
      val client = getClient(model)
      val error = Message.user("Error parsing JSON: ${e.message}. Try again with valid JSON.")
      val messages = originalMessages + responses.map { convertResponseToMessage(it) } + error
      val prompt = buildPrompt(messages, parameters, tools.isNotEmpty())
      val koogTools = convertTools(tools)
      val newResponses = executeWithTools(client, prompt, llModel, koogTools, tools, messages, parameters, toolCallBack)
      return parseStructured(newResponses, originalMessages, schema, model, llModel, tools, parameters, toolCallBack, retryCount + 1, maxRetries)
    }
  }

  private fun convertTextResponse(responses: List<KoogMessage.Response>, modelId: String): AgentResponse.Text {
    val assistant = responses.filterIsInstance<KoogMessage.Assistant>().lastOrNull()
      ?: throw IllegalStateException("No assistant response")
    val metadata = createMetadata(assistant.metaInfo, modelId)
    return AgentResponse.Text(assistant.content, metadata, responses.map { convertResponseToMessage(it) })
  }

  private fun <T> convertStructuredResponse(responses: List<KoogMessage.Response>, schema: OutputSchema<T>, modelId: String): AgentResponse.Structured<T> {
    val assistant = responses.filterIsInstance<KoogMessage.Assistant>().lastOrNull()
      ?: throw IllegalStateException("No assistant response")
    val parsed = schema.outputFromJson(assistant.content)
    val metadata = createMetadata(assistant.metaInfo, modelId)
    return AgentResponse.Structured(parsed, metadata, responses.map { convertResponseToMessage(it) })
  }

  private fun convertResponseToMessage(response: KoogMessage.Response): Message =
    when (response) {
      is KoogMessage.Assistant -> Message(MessageRole.Assistant, response.content)
      is KoogMessage.Tool.Call -> Message(MessageRole.Assistant, response.content, toolCalls = listOf(predictable.tool.ToolCallRequest(response.id ?: "", response.tool, response.content)))
      is KoogMessage.Tool.Result -> Message(MessageRole.ToolResult, response.content, toolCallId = response.id, name = response.tool)
    }

  private fun createMetadata(info: ResponseMetaInfo, modelId: String): AgentMetadata =
    AgentMetadata(
      promptTokens = info.inputTokensCount ?: 0,
      completionTokens = info.outputTokensCount ?: 0,
      totalTokens = info.totalTokensCount ?: 0,
      model = modelId,
      provider = inferProviderName(modelId)
    )

  private fun inferProviderName(modelId: String): String = when {
    modelId.startsWith("gpt-") -> "openai"
    modelId.startsWith("claude-") -> "anthropic"
    modelId.startsWith("gemini-") -> "google"
    else -> "openai"  // Default to openai for custom/unknown models
  }

  private fun <T> tryParse(text: String, schema: OutputSchema<T>): StreamResponse.Chunk<T>? =
    try { StreamResponse.Chunk(schema.outputFromJson(text)) } catch (e: Exception) { null }

  // Compression support (all functions ≤10 lines)

  context(raise: Raise<CompressionError>)
  internal suspend fun compressHistory(
    strategy: predictable.agent.CompressionStrategy,
    messages: List<Message>,
    model: Model
  ): List<Message> {
    val koogStrategy = CompressionConverter.toKoog(strategy)
    val context = createCompressionContext(messages, model)
    return executeCompression(context, koogStrategy)
  }

  private fun createCompressionContext(messages: List<Message>, model: Model): AIAgentLLMContext {
    val client = getClient(model)
    val executor = SingleLLMPromptExecutor(client)
    val llModel = ModelProvider.fromModel(model)
    val koogMessages = messages.flatMap { convertMessage(it) }
    val prompt = Prompt(koogMessages, Uuid.random().toString(), LLMParams())
    val config = AIAgentConfig(prompt, llModel, 1)
    return AIAgentLLMContext(emptyList(), ToolRegistry.EMPTY, prompt, llModel, executor, MinimalEnvironment, config, Clock.System)
  }

  context(raise: Raise<CompressionError>)
  private suspend fun executeCompression(
    context: AIAgentLLMContext,
    strategy: HistoryCompressionStrategy
  ): List<Message> = catch({
    context.writeSession {
      strategy.compress(this, emptyList())
      prompt.messages.map { convertKoogToMessage(it) }
    }
  }) { e: Throwable -> raise.raise(CompressionError.CompressionFailed(strategy.toString(), e)) }

  private fun convertKoogToMessage(msg: KoogMessage): Message = when (msg) {
    is KoogMessage.System -> Message(MessageRole.System, msg.content)
    is KoogMessage.User -> Message(MessageRole.User, msg.content)
    is KoogMessage.Assistant -> Message(MessageRole.Assistant, msg.content)
    is KoogMessage.Tool.Call -> Message(MessageRole.Assistant, msg.content, toolCalls = listOf(predictable.tool.ToolCallRequest(msg.id ?: "", msg.tool, msg.content)))
    is KoogMessage.Tool.Result -> Message(MessageRole.ToolResult, msg.content, toolCallId = msg.id, name = msg.tool)
  }

  private companion object {
    val MinimalEnvironment = object : AIAgentEnvironment {
      override suspend fun executeTools(calls: List<KoogMessage.Tool.Call>) = emptyList<ReceivedToolResult>()
      override suspend fun reportProblem(e: Throwable) { throw e }
    }
  }
}
