package predictable.agent

import kotlinx.serialization.Serializable

@Serializable
data class AgentMetadata(
  val promptTokens: Int,
  val completionTokens: Int,
  val totalTokens: Int,
  val model: String,
  val provider: String,
)
