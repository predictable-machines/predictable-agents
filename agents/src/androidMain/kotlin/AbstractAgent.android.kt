@file:OptIn(ExperimentalUuidApi::class)

package predictable

import predictable.agent.Model
import predictable.agent.RequestParameters
import predictable.tool.ToolCallback
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual class Agent(
  name: String,
  description: String,
  system: String,
  model: Model = Model.defaultModel,
  id: String = Uuid.random().toString(),
  parameters: RequestParameters = RequestParameters.defaultParameters,
  tools: List<AI<*, *>> = emptyList(),
  apiKey: String = "",
  toolCallBack: ToolCallback? = null,
) : AbstractAgent(
  name = name,
  description = description,
  system = system,
  id = id,
  model = model,
  parameters = parameters,
  tools = tools,
  apiKey = apiKey,
  toolCallBack = toolCallBack,
)
