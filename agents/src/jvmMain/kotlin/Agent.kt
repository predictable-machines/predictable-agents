@file:OptIn(ExperimentalUuidApi::class)

package predictable

import predictable.agent.*
import predictable.tool.ToolCallback
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual class Agent @JvmOverloads constructor(
  name: String,
  description: String,
  system: String,
  model: Model = Model.defaultModel,
  id : String = Uuid.random().toString(),
  parameters: RequestParameters = RequestParameters.defaultParameters,
  tools: List<AI<*, *>> = emptyList(),
  apiKey: String = "",
  toolCallBack: ToolCallback? = null,
) : JvmAgent(
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

