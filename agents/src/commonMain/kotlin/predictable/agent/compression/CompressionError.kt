package predictable.agent.compression

/**
 * Sealed class representing all possible errors during history compression operations.
 *
 * All compression functions use Arrow Raise context to propagate these typed errors:
 * ```kotlin
 * context(Raise<CompressionError>)
 * suspend fun compress(...)
 * ```
 */
sealed class CompressionError {

  /**
   * Error when maxTokens is set lower than system prompt token count.
   *
   * **Cause**: Developer specified maxTokens that cannot accommodate system prompt
   * **Recovery**: Increase maxTokens or remove system prompt content
   *
   * @property systemPromptTokens Token count of system prompt alone
   * @property maxTokens The invalid maxTokens value specified
   *
   * Example:
   * ```
   * System prompt: 5000 tokens
   * maxTokens: 3000 → InvalidTokenLimit(5000, 3000)
   * ```
   */
  data class InvalidTokenLimit(
    val systemPromptTokens: Int,
    val maxTokens: Int
  ) : CompressionError() {
    override fun toString(): String =
      "maxTokens ($maxTokens) must be >= system prompt tokens ($systemPromptTokens)"
  }

  /**
   * Error when compression operation fails.
   *
   * **Cause**: Koog's replaceHistoryWithTLDR() threw exception
   * **Common causes**:
   * - LLM API error during TL;DR generation
   * - Network failure
   * - Invalid message format
   *
   * @property strategy The compression strategy that failed (toString representation)
   * @property cause The underlying Kotlin exception
   *
   * Example:
   * ```
   * Koog throws: IOException("Connection timeout")
   * → CompressionFailed("WholeHistory", IOException(...))
   * ```
   */
  data class CompressionFailed(
    val strategy: String,
    val cause: Throwable
  ) : CompressionError() {
    override fun toString(): String =
      "Compression failed with strategy $strategy: ${cause.message}"
  }

  /**
   * Error when history has too few messages for compression.
   *
   * **Cause**: Compression requires minimum message count (typically 10)
   * **Recovery**: Wait for more conversation turns or disable compression
   *
   * @property messageCount Current number of messages in history
   * @property minimum Minimum required for this compression strategy
   *
   * Example:
   * ```
   * compressionStrategy = WholeHistory
   * messages = 3
   * → InsufficientHistory(messageCount = 3, minimum = 10)
   * ```
   */
  data class InsufficientHistory(
    val messageCount: Int,
    val minimum: Int
  ) : CompressionError() {
    override fun toString(): String =
      "Insufficient history: $messageCount messages (minimum $minimum required)"
  }
}
