package predictable.agent.compression

import arrow.core.raise.Raise
import predictable.agent.Message
import predictable.agent.RequestParameters

/**
 * History manager applying precedence order.
 *
 * Precedence: compressionStrategy → maxTokens → maxHistorySize
 */
interface HistoryManager {
  /**
   * Apply history management in precedence order:
   * 1. compressionStrategy - Apply if specified
   * 2. maxTokens - Check and trim if over limit
   * 3. maxHistorySize - Apply message count limit
   */
  context(raise: Raise<CompressionError>)
  suspend fun apply(
    messages: List<Message>,
    parameters: RequestParameters
  ): List<Message>
}
