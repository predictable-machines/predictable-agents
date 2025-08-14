package predictable.agent.providers.openai

import com.aallam.openai.api.chat.*
import com.aallam.openai.api.model.ModelId
import kotlinx.serialization.json.encodeToJsonElement
import predictable.AI
import predictable.agent.RequestParameters
import predictable.tool.Schema
import kotlinx.serialization.json.jsonObject
import predictable.tool.KotlinSchema
import predictable.tool.OutputSchema

/**
 * Builds a ChatCompletionRequest with the given parameters
 */
fun buildChatCompletionRequest(
    modelId: ModelId,
    chatMessages: List<ChatMessage>,
    tools: List<AI<*, *>>,
    schema: OutputSchema<*>?,
    parameters: RequestParameters
): ChatCompletionRequest {

    return chatCompletionRequest {
        this.model = modelId
        this.messages = chatMessages
        configureTools(tools, parameters)
        if (schema != null) {
            configureResponseFormat(schema)
        }
        configureParameters(parameters)
    }
}


private fun ChatCompletionRequestBuilder.configureParameters(parameters: RequestParameters) {
    this.temperature = parameters.temperature
    this.topP = parameters.topP
    this.n = parameters.n
    this.stop = parameters.stop
    this.maxTokens = parameters.maxCompletionTokens
    this.presencePenalty = parameters.presencePenalty
    this.frequencyPenalty = parameters.frequencyPenalty
    this.logitBias = parameters.logitBias
    this.user = parameters.user
    this.logprobs = parameters.logprobs
    this.topLogprobs = parameters.topLogprobs
    this.instanceId = parameters.instanceId
}

private fun ChatCompletionRequestBuilder.configureResponseFormat(schema: OutputSchema<*>) {
    this.responseFormat = ChatResponseFormat.jsonSchema(
        schema = JsonSchema(
            name = schema.outputSerialName().substringAfterLast("."),
            schema = KotlinSchema.json.parseToJsonElement(schema.outputJsonSchema()).jsonObject
        )
    )
}
