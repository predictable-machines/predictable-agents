package predictable

import kotlinx.serialization.Serializable
import predictable.agent.AgentMetadata
import predictable.agent.Message

@Serializable
data class Response(
  val messages: List<Message>,
  val metadata: AgentMetadata,
)
