package predictable

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import predictable.agent.Message
import predictable.agent.RequestParameters
import predictable.tool.OutputSchema

@Serializable
data class RequestWithOutputSchema(
  val messages: List<Message>,
  val parameters: RequestParameters? = null,
  val outputSchema: OutputSchema<JsonElement>,
)
