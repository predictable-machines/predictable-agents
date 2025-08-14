package predictable.agent.providers.openai

import com.aallam.openai.api.chat.ChatCompletionRequestBuilder
import com.aallam.openai.api.chat.FunctionToolChoice
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import predictable.AI
import predictable.agent.RequestParameters
import predictable.tool.KotlinSchema
import predictable.tool.Schema
import predictable.tool.ToolChoice
import predictable.tool.ToolType

/**
 * Configures tools for an OpenAI request
 */
fun ChatCompletionRequestBuilder.configureTools(tools: List<AI<*, *>>, parameters: RequestParameters) {
  if (tools.isNotEmpty()) {
    tools {
      for (tool in tools) {
        function(
          name = tool.name,
          description = tool.description,
        ) {
          val schema = KotlinSchema.json.parseToJsonElement(tool.schema.inputJsonSchema()).jsonObject
          schema.forEach { (key, value) ->
            put(key, value)
          }
        }
      }
    }
    toolChoice = parameters.toolChoice?.let {
      when (it) {
        is ToolChoice.Mode -> com.aallam.openai.api.chat.ToolChoice.Mode(it.value)
        is ToolChoice.Named -> {
          if (it.function != null) {
            com.aallam.openai.api.chat.ToolChoice.Named(
              type = when (it.type) {
                ToolType.Function -> com.aallam.openai.api.chat.ToolType.Function
                else -> null
              },
              function = FunctionToolChoice(it.function.name),
            )
          } else {
            com.aallam.openai.api.chat.ToolChoice.Auto
          }
        }
      }
    }
  }
}
