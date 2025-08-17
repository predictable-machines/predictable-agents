package predictable.agent

import kotlinx.serialization.Serializable

/**
 * Metadata about an agent's response including token usage and model information.
 * 
 * This data class captures important metrics about the AI model's processing,
 * useful for monitoring costs, performance, and debugging.
 * 
 * @property promptTokens Number of tokens in the input prompt
 * @property completionTokens Number of tokens in the generated response
 * @property totalTokens Total tokens used (prompt + completion)
 * @property model The model identifier used for generation
 * @property provider The AI provider service (e.g., "openai", "anthropic")
 */
@Serializable
data class AgentMetadata(
  val promptTokens: Int,
  val completionTokens: Int,
  val totalTokens: Int,
  val model: String,
  val provider: String,
)
