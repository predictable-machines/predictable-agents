package predictable.agent.providers

import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy as KoogStrategy
import predictable.agent.CompressionStrategy

/**
 * Internal converter between our public CompressionStrategy and Koog's HistoryCompressionStrategy.
 *
 * This keeps Koog implementation details contained within the providers package
 * and prevents leaking Koog types into the public API.
 *
 * **Visibility**: Internal to providers package only
 */
internal object CompressionConverter {

  /**
   * Converts our public CompressionStrategy to Koog's HistoryCompressionStrategy.
   *
   * @param strategy Our public compression strategy
   * @return Koog's equivalent compression strategy
   */
  fun toKoog(strategy: CompressionStrategy): KoogStrategy = when (strategy) {
    is CompressionStrategy.WholeHistory ->
      KoogStrategy.WholeHistory

    is CompressionStrategy.LastNMessages ->
      KoogStrategy.FromLastNMessages(strategy.n)

    is CompressionStrategy.Chunked ->
      KoogStrategy.Chunked(strategy.chunkSize)

    is CompressionStrategy.FromTimestamp ->
      KoogStrategy.FromTimestamp(strategy.timestamp)

    is CompressionStrategy.WholeHistoryMultipleSystemMessages ->
      KoogStrategy.WholeHistoryMultipleSystemMessages
  }
}
