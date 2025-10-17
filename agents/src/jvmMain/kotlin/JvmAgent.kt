@file:OptIn(ExperimentalUuidApi::class)

package predictable

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher
import predictable.agent.AgentInput
import predictable.agent.AgentResponse
import predictable.agent.Message
import predictable.agent.Model
import predictable.agent.RequestParameters
import predictable.agent.StreamResponse
import predictable.tool.ToolCallback
import java.util.concurrent.CompletableFuture
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

abstract class JvmAgent @JvmOverloads constructor(
  name: String,
  description: String,
  system: String,
  model: Model = Model.defaultModel,
  id : String = Uuid.random().toString(),
  parameters: RequestParameters = RequestParameters.Companion.defaultParameters,
  tools: List<AI<*, *>> = emptyList(),
  apiKey: String = "",
  toolCallBack: ToolCallback? = null,
) : AbstractAgent(
  name = name,
  description = description,
  system = system,
  id = id,
  model = model,
  parameters = parameters,
  tools = tools,
  apiKey = apiKey,
  toolCallBack = toolCallBack,
) {
  private val coroutineDispatcher = CoroutineScope(SupervisorJob())

  // ==================== Publisher-based Streaming Methods ====================

  /**
   * Stream structured responses with a target class as Publisher.
   */
  fun <T> streamAsPublisher(
    input: AgentInput,
    target: Class<T>
  ): Publisher<StreamResponse<T>> {
    val schema = ClassOutputSchema(target)
    val stream = coroutineDispatcher.future { structuredStream(input, schema) }.get()
    return stream.value.asPublisher()
  }

  /**
   * Stream text responses as Publisher.
   */
  fun streamAsPublisher(input: AgentInput): Publisher<StreamResponse<String>> {
    val stream = coroutineDispatcher.future { super.stream(input) }.get()
    return stream.value.asPublisher()
  }

  /**
   * Stream text responses from a string input as Publisher.
   */
  @JvmOverloads
  fun streamTextAsPublisher(input: String, requestParameters: RequestParameters = parameters): Publisher<StreamResponse<String>> =
    streamAsPublisher(AgentInput.Text(input, requestParameters))

  /**
   * Stream text responses from message history as Publisher.
   */
  @JvmOverloads
  fun streamMessagesAsPublisher(messages: List<Message>, requestParameters: RequestParameters = parameters): Publisher<StreamResponse<String>> =
    streamAsPublisher(AgentInput.Messages(messages, requestParameters))

  /**
   * Stream structured object generation as Publisher.
   */
  @JvmOverloads
  fun <I, T> streamObjectAsPublisher(
    input: I,
    inputClass: Class<I>,
    outputClass: Class<T>,
    requestParameters: RequestParameters = parameters
  ): Publisher<StreamResponse<T>> {
    val schema = ClassSchema(inputClass, outputClass)
    val stream = coroutineDispatcher.future {
      structuredStream(AgentInput.Structured(input, schema, requestParameters), schema)
    }.get()
    return stream.value.asPublisher()
  }

  // ==================== CompletableFuture Async Methods ====================

  /**
   * Async structured generation with target class.
   */
  fun <T> structuredAsync(input: AgentInput, target: Class<T>): CompletableFuture<T> {
    val schema = ClassOutputSchema(target)
    return coroutineDispatcher.future {
      structured(input, schema).value
    }
  }

  /**
   * Async text generation.
   */
  fun textAsync(input: AgentInput): CompletableFuture<String> =
    coroutineDispatcher.future {
      stringOutput(input).value
    }

  /**
   * Async text generation from string input.
   */
  @JvmOverloads
  fun textAsync(input: String, requestParameters: RequestParameters = parameters): CompletableFuture<String> =
    textAsync(AgentInput.Text(input, requestParameters))

  /**
   * Async text generation from message history.
   */
  @JvmOverloads
  fun textAsync(messages: List<Message>, requestParameters: RequestParameters = parameters): CompletableFuture<String> =
    textAsync(AgentInput.Messages(messages, requestParameters))

  /**
   * Async chat with metadata from string input.
   */
  @JvmOverloads
  fun chatAsync(input: String, requestParameters: RequestParameters = parameters): CompletableFuture<AgentResponse.Text> =
    coroutineDispatcher.future {
      chat(input, requestParameters)
    }

  /**
   * Async chat with metadata from message history.
   */
  @JvmOverloads
  fun chatAsync(messages: List<Message>, requestParameters: RequestParameters = parameters): CompletableFuture<AgentResponse.Text> =
    coroutineDispatcher.future {
      chat(messages, requestParameters)
    }

  /**
   * Async structured object generation.
   */
  @JvmOverloads
  fun <I, O> generateObjectAsync(
    input: I,
    inputClass: Class<I>,
    outputClass: Class<O>,
    requestParameters: RequestParameters = parameters
  ): CompletableFuture<O> {
    val schema = ClassSchema(inputClass, outputClass)
    return coroutineDispatcher.future {
      structured(AgentInput.Structured(input, schema, requestParameters), schema).value
    }
  }

  // ==================== Blocking Methods ====================

  /**
   * Blocking structured generation with target class.
   */
  fun <T> structured(input: AgentInput, target: Class<T>): T =
    structuredAsync(input, target).get()

  /**
   * Blocking text generation.
   */
  fun text(input: AgentInput): String =
    textAsync(input).get()

  /**
   * Blocking text generation from string input.
   */
  @JvmOverloads
  fun text(input: String, requestParameters: RequestParameters = parameters): String =
    textAsync(input, requestParameters).get()

  /**
   * Blocking text generation from message history.
   */
  @JvmOverloads
  fun text(messages: List<Message>, requestParameters: RequestParameters = parameters): String =
    textAsync(messages, requestParameters).get()

  /**
   * Blocking chat with metadata from string input.
   */
  @JvmOverloads
  fun chatBlocking(input: String, requestParameters: RequestParameters = parameters): AgentResponse.Text =
    chatAsync(input, requestParameters).get()

  /**
   * Blocking chat with metadata from message history.
   */
  @JvmOverloads
  fun chatBlocking(messages: List<Message>, requestParameters: RequestParameters = parameters): AgentResponse.Text =
    chatAsync(messages, requestParameters).get()

  /**
   * Blocking structured object generation.
   */
  @JvmOverloads
  fun <I, O> generateObjectBlocking(
    input: I,
    inputClass: Class<I>,
    outputClass: Class<O>,
    requestParameters: RequestParameters = parameters
  ): O =
    generateObjectAsync(input, inputClass, outputClass, requestParameters).get()

}
