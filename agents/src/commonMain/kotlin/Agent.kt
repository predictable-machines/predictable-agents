@file:OptIn(ExperimentalUuidApi::class)

package predictable

import dev.scottpierce.envvar.EnvVar
import kotlinx.coroutines.flow.Flow
import predictable.agent.*
import predictable.agent.providers.openai.OpenAIProvider
import predictable.tool.InputSchema
import predictable.tool.KotlinSchema
import predictable.tool.OutputSchema
import predictable.tool.ToolCallback
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * An AI agent capable of processing various types of inputs and generating responses.
 * 
 * Agents are high-level abstractions that combine language models with system prompts,
 * tools, and configuration to create specialized AI assistants. They support multiple
 * interaction patterns including text generation, structured output, and streaming.
 * 
 * ## Input/Output Patterns
 * 
 * Agents support several interaction patterns:
 * - **Text Generation**: Simple string input/output for conversational AI
 * - **Structured Generation**: Type-safe input/output with automatic serialization
 * - **Streaming**: Real-time response generation for better user experience
 * - **Tool Integration**: Agents can use tools to extend their capabilities
 * 
 * @property name The agent's unique name identifier
 * @property description Human-readable description of the agent's purpose
 * @property system System prompt that defines the agent's behavior and context
 * @property model The AI model configuration to use for processing
 * @property id Unique identifier for this agent instance (default: random UUID)
 * @property tools List of AI tools available to the agent for enhanced capabilities
 * @property apiKey API key for authentication (default: from environment variables)
 * @property toolCallBack Optional callback for tool execution events
 * @property parameters Default request parameters for API calls
 * 
 * @sample predictable.samples.agentBasicUsageSample
 * @sample predictable.samples.agentWithToolsSample
 */
class Agent(
  val name: String,
  val description: String,
  val system: String,
  val model: Model,
  val id: String = Uuid.random().toString(),
  val tools: List<AI<*, *>> = emptyList(),
  val apiKey: String = "",
  val toolCallBack: ToolCallback? = null,
  val parameters: RequestParameters = RequestParameters.default
) {

  private val provider: OpenAIProvider by lazy {
    val key =
      apiKey.ifBlank {
        EnvVar["OPENAI_API_KEY"] ?:
        EnvVar["OPENROUTER_API_KEY"] ?:
        ""
      }
    OpenAIProvider(
      apiKey = key,
      baseUrl = model.apiUrl.ifBlank {
        EnvVar["OPENAI_API_URL"] ?: throw IllegalArgumentException("API URL or env OPENAI_API_URL is required")
      }
    )
  }

  // ==================== Tool Creation ====================
  
  /**
   * Creates a [Tool] that wraps this agent with structured input/output types.
   * 
   * Converts the agent into a reusable tool that can be used by other agents or systems.
   * The tool will use the agent's configuration and system prompt.
   * 
   * @param I The input type for the tool
   * @param O The output type for the tool
   * @return A Tool instance that processes input of type [I] and returns type [O]
   * 
   * @sample predictable.samples.agentAsToolSample
   */
  inline operator fun <reified I, reified O> invoke() : Tool<I, O> =
    Tool(
      name = name,
      description = description,
      schema = KotlinSchema<I, O>(),
      id = Uuid.random().toString()
    ) { input: I ->
      structured(AgentInput.Structured(input, KotlinSchema<I, O>(), parameters), KotlinSchema<I, O>()).value
    }

  // ==================== Structured Generation ====================
  
  /**
   * Creates a tool for generating structured objects with type safety.
   * 
   * @param I The input type
   * @param O The output type to generate
   * @param requestParameters Optional parameters to override defaults
   * @return A Tool that transforms input of type [I] to output of type [O]
   */
  inline fun <reified I, reified O> generateObject(requestParameters: RequestParameters = parameters): Tool<I, O> =
    Tool {
      val schema = KotlinSchema<I, O>()
      structured(AgentInput.Structured(it, schema, requestParameters), schema).value
    }

  /**
   * Generates a structured object from the given input.
   * 
   * Uses the agent's capabilities to transform the input into a structured output
   * with automatic validation and type safety.
   * 
   * @param I The input type
   * @param O The output type to generate
   * @param input The input value to process
   * @param requestParameters Optional parameters to override defaults
   * @return The generated output object of type [O]
   * @throws Exception if generation fails or output validation fails
   * 
   * @sample predictable.samples.agentGenerateObjectSample
   */
  suspend inline fun <reified I, reified O> generateObject(input: I, requestParameters: RequestParameters = parameters): O =
    generateObject<I, O>(requestParameters).invoke(input)

  /**
   * Creates a tool for streaming structured object generation.
   * 
   * @param I The input type
   * @param O The output type to stream
   * @param requestParameters Optional parameters to override defaults
   * @return A Tool that transforms input to a stream of output chunks
   */
  inline fun <reified I, reified O> streamObject(requestParameters: RequestParameters = parameters): Tool<I, Flow<StreamResponse<O>>> =
    Tool {
      val schema = KotlinSchema<I, O>()
      structuredStream(AgentInput.Structured(it, schema, requestParameters), schema).value
    }

  /**
   * Streams a structured object generation process.
   * 
   * Generates the output incrementally, allowing for real-time updates and
   * better user experience for long-running operations.
   * 
   * @param I The input type
   * @param O The output type to stream
   * @param input The input value to process
   * @param requestParameters Optional parameters to override defaults
   * @return A Flow emitting stream responses containing partial results of type [O]
   * 
   * @sample predictable.samples.agentStructuredStreamingSample
   */
  suspend inline fun <reified I, reified O> streamObject(input: I, requestParameters: RequestParameters = parameters): Flow<StreamResponse<O>> =
    streamObject<I, O>(requestParameters).invoke(input)

  // ==================== Text Generation ====================
  
  /**
   * Processes a text input and returns a text response.
   * 
   * Simple text-to-text generation using the agent's model and system prompt.
   * 
   * @param input The text prompt to process
   * @param requestParameters Optional parameters to override defaults
   * @return The generated text response
   * 
   * @sample predictable.samples.agentBasicUsageSample
   */
  suspend operator fun invoke(input: String, requestParameters: RequestParameters = parameters): String =
    stringOutput(AgentInput.Text(input, requestParameters)).value

  /**
   * Processes a conversation history and returns a text response.
   * 
   * Generates a response based on a list of messages representing a conversation.
   * 
   * @param input List of messages representing the conversation history
   * @param requestParameters Optional parameters to override defaults
   * @return The generated text response
   * 
   * @sample predictable.samples.agentChatWithHistorySample
   */
  suspend operator fun invoke(input: List<Message>, requestParameters: RequestParameters = parameters): String =
    stringOutput(AgentInput.Messages(input, requestParameters)).value

  // ==================== Chat with Metadata ====================
  
  /**
   * Initiates a chat conversation with text input, returning response with metadata.
   * 
   * Similar to invoke but returns an AgentResponse containing additional metadata
   * such as token usage, model information, and timing.
   * 
   * @param input The user's text message
   * @param requestParameters Optional parameters to override defaults
   * @return AgentResponse.Text containing the response and metadata
   * 
   * @sample predictable.samples.agentChatWithHistorySample
   */
  suspend fun chat(input: String, requestParameters: RequestParameters = parameters): AgentResponse.Text =
    stringOutput(AgentInput.Text(input, requestParameters))

  /**
   * Initiates a chat conversation with message history, returning response with metadata.
   * 
   * Processes a full conversation history and returns a response with metadata.
   * 
   * @param input List of messages representing the conversation history
   * @param requestParameters Optional parameters to override defaults
   * @return AgentResponse.Text containing the response and metadata
   * 
   * @sample predictable.samples.agentChatWithHistorySample
   */
  suspend fun chat(input: List<Message>, requestParameters: RequestParameters = parameters): AgentResponse.Text =
    stringOutput(AgentInput.Messages(input, requestParameters))

  // ==================== Streaming ====================
  
  /**
   * Streams text responses for a conversation history.
   * 
   * @param input List of messages representing the conversation history
   * @param requestParameters Optional parameters to override defaults
   * @return A Flow emitting stream responses containing partial text results
   */
  fun stream(input: List<Message>, requestParameters: RequestParameters = parameters): Flow<StreamResponse<String>> =
    stream(AgentInput.Messages(input, requestParameters)).value

  /**
   * Streams text responses for the given text input.
   * 
   * Generates responses incrementally for better user experience.
   * 
   * @param input The text prompt to process
   * @param requestParameters Optional parameters to override defaults
   * @return A Flow emitting stream responses containing partial text results
   * 
   * @sample predictable.samples.agentStreamingSample
   */
  fun stream(input: String, requestParameters: RequestParameters = parameters): Flow<StreamResponse<String>> =
    stream(AgentInput.Text(input, requestParameters)).value

  /**
   * Streams text responses for structured input with explicit schema.
   * 
   * @param T The input type
   * @param input The input value to process
   * @param schema Schema for input validation
   * @param requestParameters Optional parameters to override defaults
   * @return A Flow emitting stream responses containing partial text results
   */
  fun <T> stream(
    input: T,
    schema: InputSchema<T>,
    requestParameters: RequestParameters = parameters
  ): Flow<StreamResponse<String>> =
    stream(AgentInput.Structured(input, schema, requestParameters)).value

  /**
   * Streams structured responses with automatic schema inference.
   * 
   * @param T The input type
   * @param R The output type to stream
   * @param input The input value to process
   * @param requestParameters Optional parameters to override defaults
   * @return A Flow emitting stream responses containing partial results of type [R]
   * 
   * @sample predictable.samples.agentStructuredStreamingSample
   */
  inline fun <reified T, reified R> stream(
    input: T,
    requestParameters: RequestParameters = parameters
  ): Flow<StreamResponse<R>> {
    val schema = KotlinSchema<T, R>()
    return structuredStream(
      AgentInput.Structured(input, schema, requestParameters),
      schema
    ).value
  }

  // ==================== Internal Methods ====================
  
  /**
   * Internal method to create a streaming response for any input type.
   * 
   * @param input The agent input wrapper
   * @return AgentResponse.StringStream with the streaming flow and metadata
   */
  fun stream(input: AgentInput): AgentResponse.StringStream =
    provider.chatCompletionStream(
      messages = messages(input),
      model = model,
      tools = tools,
      parameters = input.requestParameters,
      toolCallBack = toolCallBack
    )

  /**
   * Internal method to generate text output for any input type.
   * 
   * @param input The agent input wrapper
   * @return AgentResponse.Text with the response and metadata
   */
  private suspend fun stringOutput(input: AgentInput): AgentResponse.Text =
    provider.chatCompletion(
      messages = messages(input),
      model = model,
      tools = tools,
      parameters = input.requestParameters,
      toolCallBack = toolCallBack
    )

  /**
   * Internal method to create a structured streaming response.
   * 
   * @param T The output type
   * @param input The agent input wrapper
   * @param schema Schema for output validation
   * @return AgentResponse.StructuredStream with the streaming flow and metadata
   */
  fun <T> structuredStream(
    input: AgentInput,
    schema: OutputSchema<T>
  ): AgentResponse.StructuredStream<T> =
    provider.chatCompletionStructuredStream(
      messages = messages(input),
      model = model,
      tools = tools,
      schema = schema,
      parameters = input.requestParameters,
      toolCallBack = toolCallBack
    )

  /**
   * Internal method to generate structured output.
   * 
   * @param T The output type
   * @param input The agent input wrapper
   * @param schema Schema for output validation
   * @return AgentResponse.Structured with the result and metadata
   */
  suspend fun <T> structured(input: AgentInput, schema: OutputSchema<T>): AgentResponse.Structured<T> =
    provider.chatCompletionStructured(
      messages = messages(input),
      model = model,
      tools = tools,
      schema = schema,
      parameters = parameters,
      toolCallBack = toolCallBack
    )

  /**
   * Converts agent input into a list of messages for the AI model.
   * 
   * Prepends the system prompt and handles different input types.
   * 
   * @param input The agent input to convert
   * @return List of messages ready for the AI model
   */
  private fun messages(input: AgentInput): List<Message> {
    val conversation = when (input) {
      is AgentInput.Messages -> input.value
      is AgentInput.Text -> listOf(
        Message.user(input.value)
      )

      is AgentInput.Structured<*> -> {
        val schema: InputSchema<Any?> = input.schema as InputSchema<Any?>
        val value: Any? = input.value
        val json = schema.inputToJson(value)
        listOf(Message.user(json.toString()))
      }
    }// tODO handle history ?
    // TODO we need to trim the conversation to adjust it within the model context window
    return listOf(Message.system(system)) + conversation
  }
}

