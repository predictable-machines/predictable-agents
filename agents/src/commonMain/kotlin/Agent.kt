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

  inline operator fun <reified I, reified O> invoke() : Tool<I, O> =
    Tool(
      name = name,
      description = description,
      schema = KotlinSchema<I, O>(),
      id = Uuid.random().toString()
    ) { input: I ->
      structured(AgentInput.Structured(input, KotlinSchema<I, O>(), parameters), KotlinSchema<I, O>()).value
    }

  inline fun <reified I, reified O> generateObject(requestParameters: RequestParameters = parameters): Tool<I, O> =
    Tool {
      val schema = KotlinSchema<I, O>()
      structured(AgentInput.Structured(it, schema, requestParameters), schema).value
    }

  suspend inline fun <reified I, reified O> generateObject(input: I, requestParameters: RequestParameters = parameters): O =
    generateObject<I, O>(requestParameters).invoke(input)

  inline fun <reified I, reified O> streamObject(requestParameters: RequestParameters = parameters): Tool<I, Flow<StreamResponse<O>>> =
    Tool {
      val schema = KotlinSchema<I, O>()
      structuredStream(AgentInput.Structured(it, schema, requestParameters), schema).value
    }

  suspend inline fun <reified I, reified O> streamObject(input: I, requestParameters: RequestParameters = parameters): Flow<StreamResponse<O>> =
    streamObject<I, O>(requestParameters).invoke(input)

  suspend operator fun invoke(input: String, requestParameters: RequestParameters = parameters): String =
    stringOutput(AgentInput.Text(input, requestParameters)).value

  suspend operator fun invoke(input: List<Message>, requestParameters: RequestParameters = parameters): String =
    stringOutput(AgentInput.Messages(input, requestParameters)).value

  suspend fun chat(input: String, requestParameters: RequestParameters = parameters): AgentResponse.Text =
    stringOutput(AgentInput.Text(input, requestParameters))

  suspend fun chat(input: List<Message>, requestParameters: RequestParameters = parameters): AgentResponse.Text =
    stringOutput(AgentInput.Messages(input, requestParameters))

  fun stream(input: List<Message>, requestParameters: RequestParameters = parameters): Flow<StreamResponse<String>> =
    stream(AgentInput.Messages(input, requestParameters)).value

  fun stream(input: String, requestParameters: RequestParameters = parameters): Flow<StreamResponse<String>> =
    stream(AgentInput.Text(input, requestParameters)).value

  fun <T> stream(
    input: T,
    schema: InputSchema<T>,
    requestParameters: RequestParameters = parameters
  ): Flow<StreamResponse<String>> =
    stream(AgentInput.Structured(input, schema, requestParameters)).value

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

  fun stream(input: AgentInput): AgentResponse.StringStream =
    provider.chatCompletionStream(
      messages = messages(input),
      model = model,
      tools = tools,
      parameters = input.requestParameters,
      toolCallBack = toolCallBack
    )

  private suspend fun stringOutput(input: AgentInput): AgentResponse.Text =
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
  ): AgentResponse.StructuredStream<T> =
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

