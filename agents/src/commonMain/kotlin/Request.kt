package predictable

import kotlinx.serialization.Serializable
import predictable.agent.Message
import predictable.agent.RequestParameters

@Serializable
data class Request(
  val messages: List<Message>,
  val parameters: RequestParameters? = null,
)
