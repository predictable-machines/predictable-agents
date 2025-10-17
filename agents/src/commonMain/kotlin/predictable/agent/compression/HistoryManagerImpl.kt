package predictable.agent.compression

import arrow.core.raise.Raise
import predictable.agent.Message
import predictable.agent.MessageRole
import predictable.agent.Model
import predictable.agent.RequestParameters
import predictable.agent.providers.AgentProvider

/**
 * Implementation of HistoryManager applying precedence order.
 *
 * Precedence: compressionStrategy → maxTokens → maxHistorySize
 */
internal class HistoryManagerImpl(
  private val provider: AgentProvider,
  private val model: Model
) : HistoryManager {

  context(raise: Raise<CompressionError>)
  override suspend fun apply(
    messages: List<Message>,
    parameters: RequestParameters
  ): List<Message> =
    messages
      .let { applyCompression(it, parameters) }
      .let { applyTokenLimit(it, parameters) }
      .let { applyHistorySize(it, parameters) }

  context(raise: Raise<CompressionError>)
  private suspend fun applyCompression(
    messages: List<Message>,
    parameters: RequestParameters
  ): List<Message> =
    parameters.compressionStrategy?.let {
      provider.compressHistory(it, messages, model)
    } ?: messages

  context(raise: Raise<CompressionError>)
  private fun applyTokenLimit(
    messages: List<Message>,
    parameters: RequestParameters
  ): List<Message> =
    parameters.maxTokens?.let { limit ->
      trimToTokenLimit(messages, limit)
    } ?: messages

  context(raise: Raise<CompressionError>)
  private fun applyHistorySize(
    messages: List<Message>,
    parameters: RequestParameters
  ): List<Message> =
    parameters.maxHistorySize?.let { limit ->
      keepLastNMessages(messages, limit)
    } ?: messages

  private fun trimToTokenLimit(
    messages: List<Message>,
    maxTokens: Int
  ): List<Message> {
    val estimated = estimateTokens(messages)
    return if (estimated > maxTokens) {
      val systemMessages = messages.filter { it.role == MessageRole.System }
      val otherMessages = messages.filterNot { it.role == MessageRole.System }
      systemMessages + otherMessages.takeLast(maxTokens / 100)
    } else messages
  }

  private fun keepLastNMessages(
    messages: List<Message>,
    maxSize: Int
  ): List<Message> {
    val systemMessages = messages.filter { it.role == MessageRole.System }
    val otherMessages = messages.filterNot { it.role == MessageRole.System }
    return systemMessages + otherMessages.takeLast(maxSize)
  }

  private fun estimateTokens(messages: List<Message>): Int =
    messages.sumOf { it.content.length / 4 }
}
