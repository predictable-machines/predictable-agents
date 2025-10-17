package predictable.agent

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Strategy for compressing conversation history to manage token usage.
 *
 * Compression reduces the number of messages in conversation history by generating
 * summaries (TL;DR) of message sequences, preserving context while reducing tokens.
 *
 * All compression operations preserve system messages and apply intelligent
 * summarization using the AI model.
 */
@Serializable
sealed class CompressionStrategy {

  /**
   * Compress entire conversation history into a single TL;DR summary.
   *
   * Preserves:
   * - All system messages
   * - First user message
   * - Memory messages
   *
   * Compresses:
   * - All other conversation messages
   *
   * Use when: You want maximum compression and the overall conversation
   * context is more important than specific message details.
   *
   * Example:
   * ```
   * RequestParameters(
   *   compressionStrategy = CompressionStrategy.WholeHistory
   * )
   * ```
   */
  @Serializable
  data object WholeHistory : CompressionStrategy()

  /**
   * Keep the last N messages uncompressed, compress all earlier messages.
   *
   * Preserves:
   * - System messages
   * - Last N conversation messages
   *
   * Compresses:
   * - All messages before the last N
   *
   * Use when: Recent conversation context is critical but older messages
   * can be summarized.
   *
   * @property n Number of most recent messages to keep intact
   *
   * Example:
   * ```
   * RequestParameters(
   *   compressionStrategy = CompressionStrategy.LastNMessages(10)
   * )
   * ```
   */
  @Serializable
  data class LastNMessages(val n: Int) : CompressionStrategy()

  /**
   * Compress conversation history in fixed-size chunks.
   *
   * Divides history into groups of `chunkSize` messages and creates a
   * TL;DR summary for each group. Useful for maintaining topical boundaries.
   *
   * Use when: Conversation has distinct phases or topics that should be
   * summarized separately.
   *
   * @property chunkSize Number of messages per chunk
   *
   * Example:
   * ```
   * RequestParameters(
   *   compressionStrategy = CompressionStrategy.Chunked(5)
   * )
   * ```
   */
  @Serializable
  data class Chunked(val chunkSize: Int) : CompressionStrategy()

  /**
   * Compress messages before a specific timestamp, keep recent messages intact.
   *
   * Preserves:
   * - System messages
   * - Messages after the timestamp
   *
   * Compresses:
   * - Messages before the timestamp
   *
   * Use when: You want to compress older conversation turns but maintain
   * recent interaction verbatim.
   *
   * @property timestamp Cutoff timestamp (messages before this are compressed)
   *
   * Example:
   * ```
   * import kotlinx.datetime.Clock
   * import kotlin.time.Duration.Companion.hours
   *
   * RequestParameters(
   *   compressionStrategy = CompressionStrategy.FromTimestamp(
   *     Clock.System.now() - 1.hours
   *   )
   * )
   * ```
   */
  @Serializable
  data class FromTimestamp(val timestamp: Instant) : CompressionStrategy()

  /**
   * Compress history with multiple system messages present.
   *
   * Splits conversation at system message boundaries and compresses
   * each section separately. Useful when system prompt changes during
   * the conversation.
   *
   * Use when: Your conversation has multiple phases with different
   * system prompts or instructions.
   *
   * Example:
   * ```
   * RequestParameters(
   *   compressionStrategy = CompressionStrategy.WholeHistoryMultipleSystemMessages
   * )
   * ```
   */
  @Serializable
  data object WholeHistoryMultipleSystemMessages : CompressionStrategy()
}
