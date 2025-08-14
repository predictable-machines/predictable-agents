package predictable.agent

import kotlinx.serialization.Serializable
import predictable.tool.InputSchema

sealed interface AgentInput {

  val requestParameters: RequestParameters

  data class Structured<T>(
    val value: T,
    val schema: InputSchema<T>,
    override val requestParameters: RequestParameters
  ) : AgentInput


  data class Simple(val value: String, override val requestParameters: RequestParameters) : AgentInput


  data class Messages(val value: List<Message>, override val requestParameters: RequestParameters) : AgentInput

}
