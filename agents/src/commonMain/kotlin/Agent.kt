@file:OptIn(ExperimentalUuidApi::class)

package predictable

import dev.scottpierce.envvar.EnvVar
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import predictable.agent.*
import predictable.agent.providers.openai.OpenAIProvider
import predictable.tool.InputSchema
import predictable.tool.KotlinSchema
import predictable.tool.OutputSchema
import predictable.tool.ToolCallback
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class Agent(
  val id: String,
  val name: String,
  val description: String,
  val system: String,
  val model: Model,
  val tools: List<AI<*, *>>,
  val apiKey: String = "",
  val toolCallBack: ToolCallback? = null,
  val parameters: RequestParameters
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

  val generateText: AI<String, String> =
    AI { invoke(it) }

  val streamText: AI<String, Flow<StreamResponse<String>>> =
    AI { stream(it) }

  inline fun <reified I, reified O> generateObject(requestParameters: RequestParameters = parameters): AI<I, O> = AI {
    val schema = KotlinSchema<I, O>()
    structured(AgentInput.Structured(it, schema, requestParameters), schema).value
  }

  suspend inline fun <reified I, reified O> generateObject(input: I, requestParameters: RequestParameters = parameters): O =
    generateObject<I, O>(requestParameters).invoke(input)

  inline fun <reified I, reified O> streamObject(requestParameters: RequestParameters = parameters): AI<I, Flow<StreamResponse<O>>> =
    AI {
      val schema = KotlinSchema<I, O>()
      structuredStream(AgentInput.Structured(it, schema, requestParameters), schema)
    }

  suspend fun generateJson(
    request: RequestWithOutputSchema,
    requestParameters: RequestParameters = request.parameters ?: parameters
  ): JsonElement =
    AI { input: List<Message> ->
      val schema = KotlinSchema<List<Message>, JsonElement>()
      structured(AgentInput.Structured(input, schema, requestParameters), request.outputSchema).value
    }.invoke(request.messages)

  fun streamJson(
    request: RequestWithOutputSchema,
    requestParameters: RequestParameters = request.parameters ?: parameters
  ): AI<List<Message>, Flow<StreamResponse<JsonElement>>> =
    AI {
      val schema = KotlinSchema<List<Message>, JsonElement>()
      structuredStream(AgentInput.Structured(it, schema, requestParameters), request.outputSchema)
    }

  suspend inline fun <reified I, reified O> streamObject(input: I, requestParameters: RequestParameters = parameters): Flow<StreamResponse<O>> =
    streamObject<I, O>(requestParameters).invoke(input)

  suspend inline fun streamJsonObject(
    request: RequestWithOutputSchema
  ): Flow<StreamResponse<JsonElement>> =
    streamJson(request).invoke(request.messages)

  suspend operator fun invoke(input: String, requestParameters: RequestParameters = parameters): String =
    stringOutput(AgentInput.Simple(input, requestParameters)).value

  suspend operator fun invoke(input: Request, requestParameters: RequestParameters = parameters): Response =
    stringOutput(AgentInput.Messages(input.messages, requestParameters)).let { response ->
      Response(
        messages = response.messages,
        metadata = response.metadata
      )
    }

  suspend operator fun invoke(input: List<Message>, requestParameters: RequestParameters = parameters): String =
    stringOutput(AgentInput.Messages(input, requestParameters)).value

  fun stream(input: List<Message>, requestParameters: RequestParameters = parameters): Flow<StreamResponse<String>> =
    stream(AgentInput.Messages(input, requestParameters))

  fun stream(input: String, requestParameters: RequestParameters = parameters): Flow<StreamResponse<String>> =
    stream(AgentInput.Simple(input, requestParameters))

  fun <T> stream(
    input: T,
    schema: InputSchema<T>,
    requestParameters: RequestParameters = parameters
  ): Flow<StreamResponse<String>> =
    stream(AgentInput.Structured(input, schema, requestParameters))

  inline fun <reified T, reified R> stream(
    input: T,
    requestParameters: RequestParameters = parameters
  ): Flow<StreamResponse<R>> {
    val schema = KotlinSchema<T, R>()
    return structuredStream(
      AgentInput.Structured(input, schema, requestParameters),
      schema
    )
  }

  fun stream(input: AgentInput): Flow<StreamResponse<String>> =
    provider.chatCompletionStream(
      messages = messages(input),
      model = model,
      tools = tools,
      parameters = input.requestParameters,
      toolCallBack = toolCallBack
    )

  private suspend fun stringOutput(input: AgentInput): AgentResponse.Simple =
    provider.chatCompletion(
      messages = messages(input),
      model = model,
      tools = tools,
      parameters = input.requestParameters,
      toolCallBack = toolCallBack
    )

  fun <T> structuredStream(
    input: AgentInput,
    schema: OutputSchema<T>
  ): Flow<StreamResponse<T>> =
    provider.chatCompletionStructuredStream(
      messages = messages(input),
      model = model,
      tools = tools,
      schema = schema,
      parameters = input.requestParameters,
      toolCallBack = toolCallBack
    )

  suspend fun <T> structured(input: AgentInput, schema: OutputSchema<T>): AgentResponse.Structured<T> =
    provider.chatCompletionStructured(
      messages = messages(input),
      model = model,
      tools = tools,
      schema = schema,
      parameters = parameters,
      toolCallBack = toolCallBack
    )

  private fun messages(input: AgentInput): List<Message> {
    val conversation = when (input) {
      is AgentInput.Messages -> input.value
      is AgentInput.Simple -> listOf(
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

fun agent(
  name: String,
  description: String,
  system: String,
  model: Model,
  id: String = Uuid.random().toString(),
  tools: List<AI<*, *>> = emptyList(),
  toolCallback: ToolCallback? = null,
  apiKey: String = "",
  parameters: RequestParameters = RequestParameters.default
): Agent = Agent(
  name = name,
  description = description,
  system = system,
  model = model,
  tools = tools,
  apiKey = apiKey,
  id = id,
  parameters = parameters,
  toolCallBack = toolCallback
)
